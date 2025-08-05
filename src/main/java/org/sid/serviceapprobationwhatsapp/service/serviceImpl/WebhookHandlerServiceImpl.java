package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import jakarta.persistence.EntityNotFoundException;
import org.sid.serviceapprobationwhatsapp.config.WhatsAppConfig;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;
import org.sid.serviceapprobationwhatsapp.enums.otpStatus;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 This service is handling all the webhook payload and treatment done to it
 Handles incoming button and text type messages from WhatsApp.
 Needs to implements to webhookNotification to the callbackURL provided by the external system
 */


@Service
public class WebhookHandlerServiceImpl implements WebhookHandlerService {

    private final WhatsAppService whatsAppService;
    private final SMSService smsService;
    private final OtpService otpService;
    private final OtpMessage otpMessage;
    private final ApprovalOtpRepository approvalOtpRepository;
    private final OtpResendMappingService otpResendMappingService;
    private final ApprovalService approvalService;
    private final MessageIdMappingService messageIdMappingService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final InfobipService infobipService;
    private final WhatsAppConfig whatsAppConfig;

    private static final Logger logger = LoggerFactory.getLogger(WebhookHandlerServiceImpl.class);

    // Map used to associate the OTP with the approval request
    // The key is the approval ID and the value is the OTP
    // Key: The sanitized phone number ; Value: The approval ID associated with the OTP process for that phone number.
    private final Map<String, String> otpApprovalMap = new ConcurrentHashMap<>();

    // Map used to store the action taken by the user for a specific approval request
    // The key is the approval ID and the value is the action
    private final Map<String, String> approvalActionCache = new ConcurrentHashMap<>();

    // Map to store the message ID of the WhatsApp message that triggered the approval process
    // The key is the message ID and the value is the approval ID
    private final Map<String, String> commentAwaiters = new ConcurrentHashMap<>();

    public WebhookHandlerServiceImpl(WhatsAppService whatsAppService,
                                     SMSService smsService,
                                     OtpService otpService,
                                     OtpMessage otpMessage,
                                     ApprovalOtpRepository approvalOtpRepository,
                                     OtpResendMappingService otpResendMappingService,
                                     ApprovalService approvalService,
                                     MessageIdMappingService messageIdMappingService,
                                     ApprovalRequestRepository approvalRequestRepository, InfobipService infobipService, WhatsAppConfig whatsAppConfig) {

        this.whatsAppService = whatsAppService;
        this.smsService = smsService;
        this.otpService = otpService;
        this.otpMessage = otpMessage;
        this.approvalOtpRepository = approvalOtpRepository;
        this.otpResendMappingService = otpResendMappingService;
        this.approvalService = approvalService;
        this.messageIdMappingService = messageIdMappingService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.infobipService = infobipService;
        this.whatsAppConfig = whatsAppConfig;
    }

    /**
     * Processes incoming webhook payloads from WhatsApp.
     * 1. Checks if the payload is a status update
     * 2. Extracts and processes messages if present
     *
     * @param payload A map containing the webhook payload data from WhatsApp
     */
    @Override
    public void processWebhookPayload(Map<String, Object> payload) {
        try {
            logger.debug("Processing webhook payload: {}", payload);

            // 1. Check if this is a status update
            if (isStatusUpdate(payload)) {
                handleStatusUpdate(payload);
                return;
            }

            // 2. Process messages
            List<Map<String, Object>> messages = extractMessages(payload);
            if (messages != null && !messages.isEmpty()) {
                for (Map<String, Object> message : messages) {
                    processSingleMessage(message, payload);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing webhook payload", e);
        }
    }

    /**
     * Processes a single message from the webhook payload.
     * Extracts message details, marks as read, and routes to appropriate handler based on message type.
     *
     * @param message The individual message to process from the webhook
     * @param payload The complete webhook payload for context
     */
    @Override
    public void processSingleMessage(Map<String, Object> message, Map<String, Object> payload) {
        try {
            String messageId = (String) message.get("id");
            String phoneNumber = extractPhoneNumber(message);
            String messageType = (String) message.get("type");

            // Mark message as read
            markMessageAsRead(extractPhoneNumberId(payload), messageId);

            // Process based on message type
            if ("button".equals(messageType)) {
                handleButtonMessage(message, phoneNumber);
            } else if ("text".equals(messageType)) {
                handleTextMessage(message, phoneNumber);
            } else {
                logger.debug("Unhandled message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error processing message", e);
        }
    }

    /**
     * Marks a WhatsApp message as read using the WhatsApp API.
     * Sends an asynchronous POST request to update the message status.
     *
     * @param phoneNumberId The WhatsApp phone number ID that received the message
     * @param messageId The ID of the message to mark as read
     */
    @Override
    public void markMessageAsRead(String phoneNumberId, String messageId) {
        if (phoneNumberId == null || messageId == null) {
            logger.warn("Cannot mark message as read - missing phoneNumberId or messageId");
            return;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "messaging_product", "whatsapp",
                    "status", "read",
                    "message_id", messageId
            );

            whatsAppConfig.webClient().post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            success -> logger.debug("Marked message {} as read", messageId),
                            error -> logger.error("Failed to mark message {} as read: {}", messageId, error.getMessage())
                    );
        } catch (Exception e) {
            logger.error("Error in markMessageAsRead", e);
        }
    }

    /**
     * Processes incoming button messages from WhatsApp.
     * Extracts button payload and context information, with the approvalID
     */

    @Override
    public void handleButtonMessage(Map<String, Object> message, String phoneNumber) throws IOException {

        logger.info("Processing button message");

        @SuppressWarnings("unchecked")
        Map<String, Object> button = (Map<String, Object>) message.get("button");
        if (button == null) {
            logger.warn("Button object is null");
        }

        String buttonPayload = (String) Objects.requireNonNull(button).get("payload");
        String buttonText = (String) button.get("text");
        logger.info("Button clicked: {}, Payload: {}", buttonText, buttonPayload);

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) message.get("context");
        String originalMessageId = context != null ? (String) context.get("id") : null;
        logger.debug("Original Message ID: {}", originalMessageId);

        String approvalId = messageIdMappingService.getApprovalId(originalMessageId);
        logger.debug("Approval ID retrieved: {}", approvalId);
        messageIdMappingService.logAllMappings();
        if (approvalId == null) {
            logger.warn("No request found for original message ID: {}", originalMessageId);
            return;
        }
        processButtonAction(buttonPayload, phoneNumber, approvalId);
    }

    /**
     * Processes the button action based on the payload.
     * Generates an OTP via SMS and sends it to the user.
     * Updates the approval status based on the button action.
     * Stores the OTP and approval ID mapping in the otpApprovalMap.
     * Modification => from using the smsMessage and the smsService to using the infobipService
     * not utilizing the smsMessage
     * which means deleting the smsMessage variable in the method sendOtpAndUpdateState
     * */

    @Override
    public void processButtonAction(String buttonPayload, String phoneNumber, String approvalId) throws IOException {

        logger.info("Processing button action for phone number: {}", phoneNumber);

        // The approval request is retrieved from the database
        // Generate OTP sms and send it to the user
        // The OTP is generated and cached for the phone number
        Optional<ApprovalRequest> request = approvalRequestRepository.findById(approvalId);
        String otp = otpService.generateAndCacheOTP(phoneNumber, request.orElseThrow(
                () -> new EntityNotFoundException("ApprovalRequest not found")));

        String formatedBulkSmsNumber = phoneNumber.replaceFirst("^\\+212", "0");
        smsService.sendSmsWithBulk(formatedBulkSmsNumber, otp);  // formated phone number for bulk SMS only accept this format 06/7XXXXXX

//        infobipService.sendOtp(phoneNumber, otp);
        // String smsMessage = "Your code is: " + otp;
        // smsService.sendSMS(phoneNumber, smsMessage);
        logger.info("Generated OTP: {} to : {}", otp, formatedBulkSmsNumber);

        // The button payload is processed based on its prefix
        // The action is stored in the cache for later processing
        // Decision is set after creating the otp after clicking the button and accepted after validating the otp
        if (buttonPayload.startsWith("APPROVE_")) {
            approvalActionCache.put(approvalId, buttonPayload);
            sendOtpAndUpdateState(phoneNumber, approvalId, "");
        } else if (buttonPayload.startsWith("REJECT_")) {
            approvalActionCache.put(approvalId, buttonPayload);
            sendOtpAndUpdateState(phoneNumber, approvalId, "awaiting_rejection_comment");
        } else if (buttonPayload.startsWith("ATTENTE_")) {
            approvalActionCache.put(approvalId, buttonPayload);
            sendOtpAndUpdateState(phoneNumber, approvalId, "awaiting_attente_comment");
        } else if (buttonPayload.startsWith("RESEND_")) {
            handleResendButton(phoneNumber, approvalId);
        }
    }

    /**
     * Sends an OTP message and updates the state of the approval request for the user
     * Caches the approval ID for the phone number
     * Stores the comment state in the commentAwaiters map
     * Modification =>
     * Removed the smsMessage parameter
     * add the otp generating method with fetching the approval request
     * sending the sms otp via infobipService instead ot smsService
     * */

    @Override
//  public void sendOtpAndUpdateState(String phoneNumber, String smsMessage, String approvalId, String commentState) {
    public void sendOtpAndUpdateState(String phoneNumber, String approvalId, String commentState) {
        // Log the phone number for which the OTP and state are being sent
        logger.info("Sending OTP and updating state for phone number: {}", phoneNumber);

        try {
//            Optional<ApprovalRequest> request = approvalRequestRepository.findById(approvalId);
//            String otp = otpService.generateAndCacheOTP(phoneNumber, request.orElseThrow(
//                    () -> new EntityNotFoundException("ApprovalRequest not found")));

            otpMessage.sendOtpMessage(phoneNumber);
            // smsService.sendSMS(phoneNumber, smsMessage);
//            infobipService.sendOtp(phoneNumber, otp);
            logger.info("Successfully sent OTP via SMS to: {}", phoneNumber);
        } catch (Exception e) {
            logger.error("Failed to send OTP via SMS: {}", e.getMessage(), e);
            otpService.clearOTP(phoneNumber);
            return;
        }
        // Store the approval ID in the map for the phone number
        // The phone number is sanitized to remove the "+" prefix
		// String phoneNumberKey = phoneNumber.replaceFirst("\\+", "");
        otpApprovalMap.put(phoneNumber, approvalId);
        if (!commentState.isEmpty()) {
            commentAwaiters.put(phoneNumber, commentState);
        }
        String formatedBulkSmsNumber = phoneNumber.replaceFirst("^\\+212", "0");
        logger.info("User state updated to {} for phone number: {}", approvalId, formatedBulkSmsNumber);
    }

    /**
     * Handles the resend button click event.
     * Retrieve the existing OTP from the database and set its status to EXPIRED.
     * Generate a new OTP and send it to the user.
     * Modification =>
     * Commenting out the smsService and using the infobipService instead
     * */

    @Override
    public void handleResendButton(String phoneNumber, String approvalId) {

        logger.info("Handling resend button for phone number: {}", phoneNumber);
        try {
            // The existing OTP is retrieved from the database
            Optional<ApprovalOTP> optionalApprovalOTP = approvalOtpRepository.findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatus.PENDING);
            // If the OTP is found, its status is set to EXPIRED
            if (optionalApprovalOTP.isPresent()) {
                ApprovalOTP approvalOTP = optionalApprovalOTP.get();
                ApprovalRequest approvalRequest = approvalOTP.getApprovalRequest();

                // Set the previous OTP to EXPIRED
                approvalOTP.setStatus(otpStatus.EXPIRED);
                approvalOtpRepository.save(approvalOTP);
                logger.info("OTP successfully set to EXPIRED with phone number: {}", phoneNumber);

                // Maps each phone number to its corresponding approvalId to track the approval process for OTP validation and handle expired OTPs.
                otpApprovalMap.put(phoneNumber, approvalId);
                logger.info("Updated otpApprovalMap: phoneNumberKey={}, approvalId={}", phoneNumber, approvalId);

                // Store the button payload in the approval action cache to update the status after validating the new otp
                approvalActionCache.compute(approvalId, (k, buttonPayload) -> buttonPayload);
                logger.info("Stored button payload in approvalActionCache for approvalId: {}", approvalId);

                // Generate a new OTP
                String otp = otpService.generateAndCacheOTP(phoneNumber, approvalRequest);
//                infobipService.sendOtp(phoneNumber, otp);
                String formatedBulkSmsNumber = phoneNumber.replaceFirst("^\\+212", "0");
                smsService.sendSmsWithBulk(formatedBulkSmsNumber, otp); // formated phone number for bulk SMS only accept this format 06/7XXXXXX
                // smsService.sendSMS(phoneNumber, "Your new code is: " + otp);
                logger.info("New OTP Code {} sent succesfully to: {}",otp, formatedBulkSmsNumber);

            } else {
                logger.warn("ApprovalOTP not found for approvalId: {}", approvalId);
            }
        } catch (Exception e) {
            logger.error("Failed to resend OTP: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles incoming text messages from WhatsApp.
     * Check if it's an OTP message or a contextual comment.
     * Processes the message accordingly.
     */

    @Override
    public void handleTextMessage(Map<String, Object> message, String phoneNumber) {

        logger.info("Processing text message");
        Object textObj = message.get("text");
        if (!(textObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) textObj;
        String messageBody = (String) text.get("body");
        if (messageBody == null || messageBody.trim().isEmpty()) {
            return;
        }
        messageBody = messageBody.trim();
        logger.debug("Received input: {}", messageBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) message.get("context");
        if (!(phoneNumber != null && phoneNumber.startsWith("+"))) {
            phoneNumber = "+" + phoneNumber;
        }
        if (phoneNumber.trim().isEmpty() && message.get("from") != null) {
            phoneNumber = (String) message.get("from");
            if (!phoneNumber.startsWith("+")) {
                phoneNumber = "+" + phoneNumber;
            }
        }
        if (phoneNumber.trim().isEmpty()) {
            logger.warn("Unable to retrieve sender phone number");
            return;
        }
        logger.info("Sender phone number: {}", phoneNumber);
        // --- CORRECTED LOGIC ORDER ---

        // Check 1 (Comment check - PRIORITIZE):
        // If the message is a REPLY (has context), process it as a comment.
        if (context != null) {
            logger.info("Prioritizing: Processing text message as contextual comment based on context.");
            processContextualComment(phoneNumber, messageBody, context, phoneNumber);
            return; // STOP PROCESSING: It was a comment.
        }

        // Check 2 (OTP check - Only if NOT a reply):
        // If the message is NOT a reply, THEN check if the user is in the OTP awaiting state.
        if (otpApprovalMap.containsKey(phoneNumber)) {
            logger.info("Not a reply, and user is in OTP state: Processing text message as OTP.");
            // YES, call processOtpMessage here! This is the intended path for OTPs that aren't replies.
            processOtpMessage(phoneNumber, messageBody, phoneNumber);
            return ; // STOP PROCESSING: It was the expected OTP.
        }

        // Handle messages that are neither replies nor awaited OTPs
        logger.warn("Unhandled text message from {}. Not a reply and not awaiting OTP. Message body: {}", phoneNumber, messageBody);
        // Optional: send a default "I don't understand" message via whatsAppService
        // Method ends here implicitly or with return;
    }

    /**
     * Processes the OTP message.
     * Validates the OTP and updates the approval status based on the button payload.
     * Handles different response statuses from the OTP validation.
     */

    @Override
    public void processOtpMessage(String phoneNumber, String messageBody, String phoneNumberKey) {
        logger.info("Processing text message as OTP for {} based on state.", phoneNumber);

        // Use the validateOTP method from the otpService to validate the OTP
        ResponseEntity<String> response = otpService.validateOTP(phoneNumber, messageBody);
        String approvalId = String.valueOf(otpApprovalMap.get(phoneNumberKey));
        logger.info("OTP validation response status: {}", response.getStatusCode());

        if (response.getStatusCode().equals(HttpStatus.OK)) {
            // Retrieve the button payload from the approval action cache
            String buttonPayload = approvalActionCache.get(approvalId);
            if (buttonPayload != null) {
                // Update the approval status based on the button payload
                updateApprovalStatus(approvalId, buttonPayload, phoneNumber);
            } else {
                logger.warn("Button payload not found for approvalId: {}", approvalId);
            }
        } else if (response.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
            // OTP is DENIED (too many attempts)
            // Step 1: Remove the denied OTP from the map (Important!)
            otpApprovalMap.remove(phoneNumberKey);

            // Step 2: Create resend mapping
            Optional<OtpResendMapping> resendMapping = Optional.of(
                    otpResendMappingService.createResendMapping(approvalId, phoneNumber)
            );

            OtpResendMapping mapping = resendMapping.get();
            logger.info("Created Resend Mapping: {}", mapping.getMappingId());

            // Step 3: Get the original request
            ApprovalRequest approvalRequest = approvalRequestRepository.findById(approvalId)
                    .orElseThrow(() -> new EntityNotFoundException("ApprovalRequest not found"));

            // Step 4: Send the button to allow requesting a new OTP
            otpMessage.resendOtpMessage(phoneNumber, resendMapping, approvalRequest);
            logger.info("Resend OTP WhatsApp Message sent to: {}", phoneNumber);

        } else if (response.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
            // Invalid OTP (but not yet DENIED)
            logger.warn("Invalid OTP for phone number: {}", phoneNumber);
            otpMessage.sendTryAgain(phoneNumber);

        } else if (response.getStatusCode().equals(HttpStatus.NOT_FOUND)){
            logger.warn("No valid OTP found for phone number: {}", phoneNumber);
        } else {
            // Response is something else unexpected (e.g., 500 Internal Server Error)
            logger.error("Unexpected response from validateOTP: {}", response.getStatusCode());
        }
    }


    /**
     * Updates the approval status based on the button payload.
     * Clears the state and cache for the phone number and approval ID.
     */

    @Override
    public void updateApprovalStatus(String approvalId, String buttonPayload, String phoneNumber) {

        statut updatedStatus = null;
        logger.info("Treating the updateStatus for approval ID: {}", approvalId);

        if (buttonPayload.startsWith("APPROVE_")) {
            updatedStatus = statut.Approuver;
            whatsAppService.sendCommentaire(approvalId, phoneNumber);
        } else if (buttonPayload.startsWith("REJECT_")) {
            updatedStatus = statut.Rejeter;
            whatsAppService.sendCommentaire(approvalId, phoneNumber);
        } else if (buttonPayload.startsWith("ATTENTE_")) {
            updatedStatus = statut.En_Attente;
            whatsAppService.sendCommentaire(approvalId, phoneNumber);
        }

        if (updatedStatus != null) {
            approvalService.updateStatus(approvalId, updatedStatus);
            logger.info("Updating approval status to {} for approval ID: {}", updatedStatus, approvalId);
            otpApprovalMap.remove(phoneNumber.replaceFirst("\\+", ""));
            approvalActionCache.remove(approvalId);
            logger.info("Cleared state and cache for phone number: {} and approvalId: {}", phoneNumber, approvalId);
        }
    }


    /**
     * Processes the contextual comment from the user.
     * Updates the approval request with the comment.
     * Removes the phone number from the commentAwaiters map.
     */

    @Override
    public void processContextualComment(String phoneNumber, String messageBody, Map<String, Object> context, String phoneNumberKey) {

        String originalMessageId = (String) context.get("id");
        if (originalMessageId != null) {
            String approvalId = messageIdMappingService.getApprovalId(originalMessageId);

            if (approvalId != null) {
                logger.debug("Approval ID retrieved from messageIdMappingService: {}", approvalId);
                Optional<ApprovalRequest> optionalApprovalRequest = approvalRequestRepository.findById(approvalId);

                if (optionalApprovalRequest.isPresent()) {
                    ApprovalRequest approvalRequest = optionalApprovalRequest.get();
                    approvalRequest.setCommentaire(messageBody);
                    approvalRequestRepository.save(approvalRequest);
                    commentAwaiters.remove(phoneNumberKey);
                    logger.info("Comment saved and notification sent for approvalId: {}", approvalId);

                } else {
                    logger.warn("Approval request not found for approvalId: {}", approvalId);
                }
            } else {
                logger.warn("No approvalId found in messageIdMappingService for messageId: {}", originalMessageId);
            }
        } else {
            logger.warn("No original message ID (context.id) found in the text message.");
        }
    }


    // --------------------------- Helper methods ---------------------------
    /**
     * Checks if the webhook payload contains a status update.
     * Examines the payload structure to identify if it contains message status information.
     *
     * @param payload The webhook payload to check
     * @return true if the payload contains status updates, false otherwise
     */
    @SuppressWarnings("unchecked")
    private boolean isStatusUpdate(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries != null && !entries.isEmpty()) {
                Map<String, Object> entry = entries.get(0);
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes != null && !changes.isEmpty()) {
                    Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
                    return value != null && value.containsKey("statuses");
                }
            }
        } catch (Exception e) {
            logger.error("Error checking if payload is status update", e);
        }
        return false;
    }

    /**
     * Processes status updates from the webhook payload.
     * Extracts and logs message status information like delivery and read receipts.
     *
     * @param payload The webhook payload containing status updates
     */
    private void handleStatusUpdate(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> statuses = extractStatuses(payload);
            if (statuses != null) {
                statuses.forEach(status -> {
                    String statusType = (String) status.get("status");
                    String messageId = (String) status.get("id");
                    logger.info("Message status update - ID: {}, Status: {}", messageId, statusType);
                });
            }
        } catch (Exception e) {
            logger.error("Error handling status update", e);
        }
    }

    /**
     * Extracts messages from the webhook payload.
     * Navigates through the payload structure to find and return the messages array.
     *
     * @param payload The webhook payload to process
     * @return List of message objects, or empty list if none found
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMessages(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries != null && !entries.isEmpty()) {
                Map<String, Object> entry = entries.get(0);
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes != null && !changes.isEmpty()) {
                    Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
                    return (List<Map<String, Object>>) value.get("messages");
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting messages from payload", e);
        }
        return Collections.emptyList();
    }

    /**
     * Extracts status updates from the webhook payload.
     * Similar to extractMessages but specifically for status information.
     *
     * @param payload The webhook payload to process
     * @return List of status update objects, or empty list if none found
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractStatuses(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries != null && !entries.isEmpty()) {
                Map<String, Object> entry = entries.get(0);
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes != null && !changes.isEmpty()) {
                    Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
                    return (List<Map<String, Object>>) value.get("statuses");
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting statuses from payload", e);
        }
        return Collections.emptyList();
    }

    /**
     * Extracts and formats the sender's phone number from a message.
     * Ensures the phone number is properly formatted with a '+' prefix.
     *
     * @param message The message object containing sender information
     * @return Formatted phone number or null if not found/invalid
     */
    private String extractPhoneNumber(Map<String, Object> message) {
        try {
            String phoneNumber = (String) message.get("from");
            if (phoneNumber != null) {
                phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
                if (!phoneNumber.startsWith("+")) {
                    phoneNumber = "+" + phoneNumber;
                }
                return phoneNumber;
            }
        } catch (Exception e) {
            logger.error("Error extracting phone number", e);
        }
        return null;
    }

    /**
     * Extracts the WhatsApp phone number ID from the webhook payload.
     * This ID is used for sending responses back to WhatsApp.
     *
     * @param payload The webhook payload containing metadata
     * @return WhatsApp phone number ID or null if not found
     */
    @SuppressWarnings("unchecked")
    private String extractPhoneNumberId(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries != null && !entries.isEmpty()) {
                Map<String, Object> entry = entries.get(0);
                List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
                if (changes != null && !changes.isEmpty()) {
                    Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
                    return (String) ((Map<String, Object>) value.get("metadata")).get("phone_number_id");
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting phone number ID", e);
        }
        return null;
    }
}