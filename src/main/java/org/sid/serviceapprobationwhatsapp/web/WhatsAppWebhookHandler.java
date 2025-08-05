package org.sid.serviceapprobationwhatsapp.web;


import org.sid.serviceapprobationwhatsapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
@RequestMapping("/webhook")
public class WhatsAppWebhookHandler {

    private final WebhookHandlerService handlerService;
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    public WhatsAppWebhookHandler(WebhookHandlerService handlerService) {
        this.handlerService = handlerService;
    }

    @Value("${VERIFY_TOKEN}")
    private String webhookVerifyToken;

    @Value("${whatsapp.api.token}")
    private String whatsappApiToken;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        logger.info("Received webhook verification request - mode: {}, token: {}", mode, token);

        if ("subscribe".equals(mode) && webhookVerifyToken.equals(token)) {
            logger.info("Webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }

        logger.warn("Webhook verification failed - Invalid token or mode");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        logger.info("Received webhook payload");

        // Process asynchronously to return 200 OK immediately
        CompletableFuture.runAsync(() -> handlerService.processWebhookPayload(payload));

        return ResponseEntity.ok().build();
    }}