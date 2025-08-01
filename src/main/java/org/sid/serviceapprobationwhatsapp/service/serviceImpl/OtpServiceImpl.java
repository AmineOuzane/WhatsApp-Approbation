package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;

/**
 * This service is responsible for generating, validating, and managing SMS OTPs (One-Time Passwords) for approval requests.
 * It includes methods for generating OTPs, caching them in memory for a limited time, validating them against the database,
 * and clearing expired OTPs. The OTPs are stored in the ApprovalOtpRepository and are associated with specific approval requests,
 * which are stored in the ApprovalRequestRepository. The service uses a SecureRandom object for generating secure random numbers
 * and a HashMap to store OTPData objects in memory, keyed by phone number. The OTPs are generated using a limited character set
 * (numbers only in this case) and are of a fixed length (currently 6 digits).
 */

@Service
public class OtpServiceImpl implements OtpService {

    private final ApprovalOtpRepository approvalOtpRepository;
    private static final Logger logger = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final Random RANDOM = new SecureRandom();  // Creates a SecureRandom object for generating secure random numbers
    private static final String ALPHABET = "0123456789"; // Defines the characters to be used for generating OTPs (numbers only in this case)

    public OtpServiceImpl(ApprovalOtpRepository approvalOtpRepository) {
        this.approvalOtpRepository = approvalOtpRepository;
    }

    @Override
    public String generateOTP(int length) {
        // Generate a random OTP of a given length
        StringBuilder returnValue = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // Appends a random character from the ALPHABET to the StringBuilder
            returnValue.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return returnValue.toString();
    }

    @Override
    public String generateAndCacheOTP(String recipientNumber, ApprovalRequest approvalRequest) {
        // Generate a random OTP of length 6, save it to the database and associate it with the ApprovalRequest
        String otp = generateOTP(6);
        LocalDateTime expiry = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        // Create the otp with the phone number, decision, ...
        ApprovalOTP approvalOTP = ApprovalOTP.builder()
                .recipientNumber(recipientNumber)
                .otp(otp)
                .decision(approvalRequest.getDecision())
                .status(otpStatut.PENDING)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .expiration(expiry)
                .invalidattempts(0)
                .approvalRequest(approvalRequest) // Associate the OTP with the ApprovalRequest
                .build();
        approvalOtpRepository.save(approvalOTP);
        return otp;
    }

    @Override
    public ResponseEntity<String> validateOTP(String recipientNumber, String otp) {
        // Get the current time in UTC
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        // Retrieve the OTP based on the recipient number and status to show the most recent one
        Optional<ApprovalOTP> approvalOTP = approvalOtpRepository.findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(recipientNumber, otpStatut.PENDING);
	        if (approvalOTP.isEmpty()) {
            // No OTP found for this recipient number.  This is a NOT_FOUND case.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No OTP found for this recipient.");
        }
            ApprovalOTP approvalAttempt = approvalOTP.get();
            // Return an error if the OTP has already been denied to handle it later on processOtpMessage method
        if (approvalAttempt.getStatus() == otpStatut.DENIED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("OTP already denied.");
        }

        // Check if OTP
        if (!approvalAttempt.getOtp().equals(otp)) {
            // Incorrect OTP entered.
            if (approvalAttempt.getExpiration().isAfter(now)) {
                // Increment invalid attempts *only* if the OTP hasn't expired.
                approvalAttempt.setInvalidattempts(approvalAttempt.getInvalidattempts() + 1);
                approvalOtpRepository.save(approvalAttempt); // Save the updated attempts count.

                if (approvalAttempt.getInvalidattempts() >= 3) {

                    approvalAttempt.setStatus(otpStatut.DENIED);
                    approvalOtpRepository.save(approvalAttempt);
                    logger.info("OTP set to DENIED for {}", recipientNumber);

                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("OTP denied (too many attempts).");
                }

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect OTP. Attempts remaining: " + (3 - approvalAttempt.getInvalidattempts())); // Provide feedback.
            } else {
                // OTP expired before incorrect attempts.
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("OTP expired.");  //Or maybe UNAUTHORIZED
            }
        }

        // OTP is correct, not denied, and not expired.
        if (approvalAttempt.getExpiration().isAfter(now) && approvalAttempt.getStatus() == otpStatut.PENDING) {
            // Valid OTP
            approvalOtpRepository.delete(approvalAttempt); // Delete after successful validation.
            return ResponseEntity.ok("OTP validated successfully.");
        } else {
            // OTP is expired, even though it's the correct OTP.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("OTP expired.");
        }
    }

    @Override
    public void clearOTP(String recipientNumber) {
        approvalOtpRepository.deleteByRecipientNumber(recipientNumber); // Remove every OTP of that PhoneNumber
    }
}