package org.sid.serviceapprobationwhatsapp.web;


import org.sid.serviceapprobationwhatsapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

/**
 * This class handles incoming WhatsApp webhook events and processes button clicks and text messages.
 * It manages the approval process, OTP generation, and comment handling.
 * When a button is clicked, it processes the corresponding approval action and
 * sends an OTP to the user. The user must enter the OTP to validate the action.
 * After validation, the approval action is executed and the other approvers are
 * notified. If the action is Reject or Attente, the user is asked to enter a
 * comment. The comment is then saved and the approval request is updated.
 **/


@RestController
public class WhatsAppWebhookHandler {

    private final WebhookHandlerService handlerService;
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    public WhatsAppWebhookHandler(WebhookHandlerService handlerService) {
        this.handlerService = handlerService;
    }

    /**
     * Handles incoming webhook events from WhatsApp.
     * Processes button clicks and text messages.
     * @param payload The incoming webhook payload.
     * @return ResponseEntity with a message indicating processing status.
     */

    @PostMapping("/webhook")

    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) throws IOException {
        logger.info("Webhook received!");
        logger.debug("Full payload: {}", payload);

        Object entryObj = payload.get("entry");
        if (entryObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) entryObj;
            for (Map<String, Object> entry : entries) {

                Object changesObj = entry.get("changes");
                if (changesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> changes = (List<Map<String, Object>>) changesObj;
                    for (Map<String, Object> change : changes) {

                        Object valueObj = change.get("value");
                        if (valueObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> value = (Map<String, Object>) valueObj;

                            Object messagesObj = value.get("messages");
                            if (messagesObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesObj;
                                for (Map<String, Object> message : messages) {

                                    String phoneNumber = (String) message.get("from");
                                    if (phoneNumber != null) {
                                        phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
                                        if (!phoneNumber.startsWith("+")) {
                                            phoneNumber = "+" + phoneNumber;
                                        }
                                    }
                                    String messageType = (String) message.get("type");
                                    logger.debug("Message type: {}", messageType);
                                    if ("button".equals(messageType)) {
                                        handlerService.handleButtonMessage(message, phoneNumber);
                                    } else if ("text".equals(messageType)) {
                                        handlerService.handleTextMessage(message, phoneNumber);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return ResponseEntity.ok(Collections.singletonMap("message", "Processed"));
    }
}