package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.ApprovalService;
import org.sid.serviceapprobationwhatsapp.service.MessageIdMappingService;
import org.sid.serviceapprobationwhatsapp.service.PayloadCreatorService;
import org.sid.serviceapprobationwhatsapp.service.WhatsAppService;
import org.sid.serviceapprobationwhatsapp.web.WhatsAppWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class WhatsAppServiceImpl implements WhatsAppService {

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.api.token}")
    private String whatsappApiToken;

    private final RestTemplate restTemplate;
    private final PayloadCreatorService payloadCreatorService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final MessageIdMappingService messageIdMappingService;
    private final ApprovalService approvalService;

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    public WhatsAppServiceImpl(ApprovalRequestRepository approvalRequestRepository,
                               MessageIdMappingService messageIdMappingService,
                               ApprovalService approvalService,
                               PayloadCreatorService payloadCreatorService,
                               RestTemplate restTemplate  ) {

        this.approvalRequestRepository = approvalRequestRepository;
        this.messageIdMappingService = messageIdMappingService;
        this.approvalService = approvalService;
        this.payloadCreatorService = payloadCreatorService;
        this.restTemplate = restTemplate;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + whatsappApiToken);
        return headers;
    }

    // Method to send a message template of the approval request to the approvers
    // Returns a ResponseEntity object containing the response body, headers, and status code
    @Override
    public ResponseEntity<String> sendMessageWithInteractiveButtons(ApprovalRequest approvalRequest){

        // Extract the approvalId from the ApprovalRequest entity
        String approvalId = approvalRequest.getId();
        if (approvalId == null || approvalId.isEmpty()) {
            logger.error("Missing Approval ID for the request when sending it for approval.");
            return ResponseEntity.badRequest().body("Missing Approval ID for the request when sending it for approval.");
        } else {
            logger.info("Approval ID: {}", approvalId);
        }
        logger.info("Sending message to that list of approvers: {}", approvalRequest.getApprovers());

        // Check if there are any approvers
        List<String> approvers = approvalRequest.getApprovers();
        if (approvers == null || approvers.isEmpty()) {
            logger.warn("No approvers found for approval request ID: {}", approvalId);
            return ResponseEntity.ok("No approvers to notify for request " + approvalId);
        }

        // Build the base request JSON and send for EACH approver
        for(String approver : approvers) {
            try {
                // Add + to the beginning of the phone number if it's missing
                String formattedApprover = approver.startsWith("+") ? approver : "+" + approver;

                logger.info("Sending message to approver: {}", formattedApprover);

                // Payload creation
                JSONObject requestBody = payloadCreatorService.createBaseRequestBody(formattedApprover);
                JSONObject template = payloadCreatorService.createTemplateObject("generic_approval");

                JSONArray components = new JSONArray();

                // Title Component
                JSONArray titleParameters = new JSONArray();
                titleParameters.put(payloadCreatorService.createTextParameter(approvalRequest.getOrigin()));
                JSONObject titleComponent = new JSONObject();
                titleComponent.put("type", "header");
                titleComponent.put("parameters", titleParameters);
                components.put(titleComponent);

                // Body Component
                JSONArray bodyParameters = new JSONArray();
                bodyParameters.put(payloadCreatorService.createTextParameter(approvalRequest.getDemandeur()));  // {{1}}
                bodyParameters.put(payloadCreatorService.createTextParameter(approvalRequest.getObjectType())); // {{2}}
                bodyParameters.put(payloadCreatorService.createTextParameter(approvalRequest.getObjectId()));   // {{3}}
                bodyParameters.put(payloadCreatorService.createTextParameter(approvalRequest.getObjectLabel())); // {{4}}


                JSONObject bodyComponent = new JSONObject();
                bodyComponent.put("type", "body");
                bodyComponent.put("parameters", bodyParameters);
                components.put(bodyComponent);

                // "Approuver" button component
                JSONObject approveButtonComponent = new JSONObject();
                approveButtonComponent.put("type", "button");
                approveButtonComponent.put("sub_type", "quick_reply");
                approveButtonComponent.put("index", "0");
                JSONArray approveParameters = new JSONArray();
                JSONObject approvePayload = new JSONObject();
                approvePayload.put("type", "payload");
                approvePayload.put("payload", "APPROVE_" + approvalId);
                approveParameters.put(approvePayload);
                approveButtonComponent.put("parameters", approveParameters);
                components.put(approveButtonComponent);

                // "Rejeter" button component
                JSONObject rejectButtonComponent = new JSONObject();
                rejectButtonComponent.put("type", "button");
                rejectButtonComponent.put("sub_type", "quick_reply");
                rejectButtonComponent.put("index", "1");
                JSONArray rejectParameters = new JSONArray();
                JSONObject rejectPayload = new JSONObject();
                rejectPayload.put("type", "payload");
                rejectPayload.put("payload", "REJECT_" + approvalId);
                rejectParameters.put(rejectPayload);
                rejectButtonComponent.put("parameters", rejectParameters);
                components.put(rejectButtonComponent);

                // "Attente" button component
                JSONObject waitingButtonComponent = new JSONObject();
                waitingButtonComponent.put("type", "button");
                waitingButtonComponent.put("sub_type", "quick_reply");
                waitingButtonComponent.put("index", "2");
                JSONArray waitingParameters = new JSONArray();
                JSONObject waitingPayload = new JSONObject();
                waitingPayload.put("type", "payload");
                waitingPayload.put("payload", "ATTENTE_" + approvalId);
                waitingParameters.put(waitingPayload);
                waitingButtonComponent.put("parameters", waitingParameters);
                components.put(waitingButtonComponent);

                // Finalize the template and request body
                template.put("components", components);
                requestBody.put("template", template);

                // Send the request
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
                ResponseEntity<String> response = restTemplate.postForEntity(whatsappApiUrl, request, String.class);

                // Map the context message ID to the approval Id (keep this logic per message)
                String messageId = extractContextIdFromResponse(response.getBody());
                logger.info("Extracted Message ID: " + messageId);
                if (messageId != null) {
                    System.out.println("Storing mapping: Message ID = " + messageId + ", Approval ID = " + approvalId);
                    messageIdMappingService.storeMapping(messageId, approvalId);
                    System.out.println("Map size after storing: " + messageIdMappingService.getMapSize());
                    // Update status logic - consider if this should be done once after the loop
                    approvalService.updateStatus(approvalId, statut.Pending); // This will be called for EACH successful send
                    System.out.println("Approval " + approvalId + " status updated to Pending for message sent to " + formattedApprover);
                } else {
                    logger.warn("Could not extract message ID for approver: {}. Mapping not stored.", formattedApprover);
                }

            } catch (Exception e) {
                logger.error("Failed to process or send message to approver {}: {}", approver, e.getMessage(), e);
            }
        } // --- Loop ends after trying all approvers ---

        logger.info("Finished attempting to send messages to all approvers for approval ID: {}", approvalId);
        return ResponseEntity.ok("Demande approbation envoyée avec succès (tentative effectuée pour tous les approbateurs).");
    }


    // Method to send template message to the user to send a comment after rejecting or pending the approval
    @Override
    public ResponseEntity<String> sendCommentaire(String approvalId, String recipientNumber) {

        // String formattedPhoneNumber = recipientNumber.startsWith("+") ? recipientNumber : ("+" + recipientNumber);
        Optional<ApprovalRequest> approvalRequest = approvalRequestRepository.findById(approvalId);
        System.out.println("Retrieved recipient phone number info: " + recipientNumber);


        JSONObject requestBody = payloadCreatorService.createBaseRequestBody(recipientNumber);
        JSONObject template = payloadCreatorService.createTemplateObject("traitement");
        requestBody.put("template", template);

        JSONArray components = new JSONArray();

        JSONArray titleParameters = new JSONArray();
        titleParameters.put(payloadCreatorService.createTextParameter(approvalRequest.orElseThrow(() -> new RuntimeException("Approval request is empty")).getObjectId())); // {{1}}

        JSONObject titleComponent = new JSONObject();
        titleComponent.put("type", "header");
        titleComponent.put("parameters", titleParameters);
        components.put(titleComponent);

        // Body Component
        JSONObject bodyComponent = new JSONObject();
        bodyComponent.put("type", "body");
        components.put(bodyComponent);

        requestBody.put("template", template);
        template.put("components", components);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(whatsappApiUrl, request, String.class);

        // Mapping the context message ID to the approval ID to link that comment message containing the approval Id to the approval that needs a comment
        String messageId = extractContextIdFromResponse(response.getBody());
        System.out.println("Extracted Message ID: " + messageId);
        if (messageId != null) {
            System.out.println("Storing mapping: Message ID = " + messageId + ", Approval ID = " + approvalId);
            messageIdMappingService.storeMapping(messageId, approvalId);
            System.out.println("Map size after storing: " + messageIdMappingService.getMapSize());
            System.out.println("Approval " + approvalId + " is waiting for a comment !");
        }
        return response;
    }

    // Extracting the message id from the approval request to match the decision button to the approval itself
    @Override
    public String extractContextIdFromResponse(String jsonResponse) {
        System.out.println("Received JSON response: " + jsonResponse);
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode.has("messages") && rootNode.get("messages").isArray() && !rootNode.get("messages").isEmpty()) {
                JsonNode messagesNode = rootNode.get("messages").get(0);
                System.out.println("Messages node found: " + messagesNode.toString());

                // Extract the message ID
                String messageId = messagesNode.path("id").asText();
                if (!messageId.isEmpty()) {
                    System.out.println("Extracted message ID: " + messageId);
                    return messageId;
                } else {
                    System.out.println("Message ID is empty in the JSON response.");
                    return null;
                }
            } else {
                System.out.println("No messages found in the JSON response or messages array is empty.");
                return null;
            }
        } catch (JsonProcessingException e) {
            System.out.println("Error parsing JSON response: " + e.getMessage());
            return null;
        }
    }
}
