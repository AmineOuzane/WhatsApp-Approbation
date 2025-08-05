package org.sid.serviceapprobationwhatsapp.service;

import java.io.IOException;
import java.util.Map;

public interface WebhookHandlerService {

    void processWebhookPayload(Map<String, Object> payload);

    void processSingleMessage(Map<String, Object> message, Map<String, Object> payload);

    void markMessageAsRead(String phoneNumberId, String messageId);

    void handleButtonMessage(Map<String, Object> message, String phoneNumber) throws IOException;

    void processButtonAction(String buttonPayload, String phoneNumber, String approvalId) throws IOException;

//    void sendOtpAndUpdateState(String phoneNumber, String smsMessage, String approvalId, String commentState);
      void sendOtpAndUpdateState(String phoneNumber, String approvalId, String commentState);

    void handleResendButton(String phoneNumber, String approvalId);

    void handleTextMessage(Map<String, Object> message, String phoneNumber);

    void processOtpMessage(String phoneNumber, String messageBody, String phoneNumberKey);

    void updateApprovalStatus(String approvalId, String buttonPayload, String phoneNumber);

    void processContextualComment(String phoneNumber, String messageBody, Map<String, Object> context, String phoneNumberKey);

}
