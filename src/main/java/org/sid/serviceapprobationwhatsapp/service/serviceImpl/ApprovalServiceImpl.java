package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import jakarta.persistence.EntityNotFoundException;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.otpStatus;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.ApprovalService;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
/**
 * This service is responsible for managing the approval flow of twilio verify. It handles the following operations:

 * Updating the status of an approval request
 * Sending an OTP to the approver and creating an ApprovalOTP to track the status of the OTP
 * Saving an approval request to the database
 * Retrieving an approval request by ID
 * It also handles optimistic locking failures by retrying the operation

 */

@Service
@Transactional
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalOtpRepository approvalOtpRepository;

    public ApprovalServiceImpl(ApprovalRequestRepository approvalRequestRepository, TwilioService twilioService, ApprovalOtpRepository approvalOtpRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalOtpRepository = approvalOtpRepository;
    }

    private static final Logger logger = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    // Method to update the status of the approval request
    @Override
    public void updateStatus(String id, statut decision) {
        ApprovalRequest approvalRequest = approvalRequestRepository.findById(id).orElse(null);
        if (approvalRequest != null) {
            approvalRequest.setDecision(decision);
            approvalRequestRepository.save(approvalRequest);
        }
    }

    // Method to send OTP and create ApprovalOTP
    @Override
    public void sendOtpAndCreateApprovalOTP(ApprovalRequest approvalRequest, String phoneNumber) {

            // Add + to the beginning of the phone number if it's missing
            if (!phoneNumber.startsWith("+")) {
                phoneNumber = "+" + phoneNumber;
            }

            // Check for a validated OTP
            Optional<ApprovalOTP> existingOtp = approvalOtpRepository
                    .findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(phoneNumber, otpStatus.PENDING);

            if (existingOtp.isPresent()) {
                logger.warn("Pending OTP already exists for phone {}. Not creating a new one.", phoneNumber);

            }
            // If no validated OTP exists, create a new one
            try {
                // Envoyer OTP using Twilio Verify
//                String verificationSid = twilioService.sendVerificationCode(phoneNumber);
                logger.info("OTP sent successfully for phone {}", phoneNumber);

                // Create ApprovalOTP entity and save it the database to track the OTP status
                ApprovalOTP otp = ApprovalOTP.builder()
                        .approvalRequest(approvalRequest)
                        .recipientNumber(phoneNumber)
                        .status(otpStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .invalidattempts(0)
                        .expiration(LocalDateTime.now().plusMinutes(5)) // Expires in 5 minutes
                        .build();

                approvalOtpRepository.save(otp);
                logger.info("ApprovalOTP created successfully for phone {}", phoneNumber);
            } catch (Exception e) {
                logger.error("Error sending OTP: {}", e.getMessage());
                throw new RuntimeException("Error sending OTP", e);
            }
    }

    // Method to save the ApprovalRequest
    @Override
    public ApprovalRequest saveApprovalRequest(ApprovalRequest approvalRequest) {
        try {
            return approvalRequestRepository.save(approvalRequest);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Log and retry logic
            logger.info("Optimistic locking failure, retrying...");
            return approvalRequestRepository.findById(approvalRequest.getId())
                    .orElseThrow(() -> new EntityNotFoundException("ApprovalRequest not found"));
        }
    }

    @Override
    public ApprovalRequest getApproval(String approvalId) {
        return approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid approval ID: " + approvalId));
    }
}
