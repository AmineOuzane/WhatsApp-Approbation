package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.springframework.http.ResponseEntity;

public interface OtpService {

    String generateOTP(int length);
    String generateAndCacheOTP(String recipientNumber, ApprovalRequest approvalRequest);
    ResponseEntity<String> validateOTP(String recipientNumber, String otp);
    void clearOTP(String recipientNumber);
}
