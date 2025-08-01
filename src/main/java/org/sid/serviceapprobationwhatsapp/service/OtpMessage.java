package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

public interface OtpMessage {

    ResponseEntity<String> sendOtpMessage(String recipientNumber);
    ResponseEntity<String> resendOtpMessage(String recipientNumber, Optional<OtpResendMapping> mapping, ApprovalRequest approvalRequest);
    ResponseEntity<String> sendTryAgain(String recipientNumber);
    // Extracting the message id from the approval request to match the decision button to the approval itself
    String extractContextIdFromResponse(String jsonResponse);
}
