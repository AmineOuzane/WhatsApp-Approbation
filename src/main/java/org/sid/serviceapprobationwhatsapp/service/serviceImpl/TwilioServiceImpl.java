package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import org.sid.serviceapprobationwhatsapp.enums.otpStatus;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalOtpRepository;
import org.sid.serviceapprobationwhatsapp.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * This service is responsible for sending OTPs using Twilio Verify and checking their validity.
 * It initializes the Twilio client with the provided account SID and authentication token,
 * and provides methods to send OTPs and verify them.
 * This service is used when the user wants to receive the OTP via SMS using Twilio Verify.
 */

@Service
public class TwilioServiceImpl implements TwilioService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.verify.service.sid}")
    private String verifyServiceSid;

    Logger logger = LoggerFactory.getLogger(TwilioServiceImpl.class);

    public TwilioServiceImpl(ApprovalOtpRepository approvalOtpRepository) {
    }


    // Appelée après que instance de la class ait été créée et que toutes les dépendances aient été injectées.
    @PostConstruct
    // Nécessaire pour que le client Twilio puisse être utilisé pour envoyer des SMS.
    public void init() {
        try {
            // initialise client Twilio avec avec les information authentication
            Twilio.init(accountSid, authToken);
            logger.info("Twilio client initialized successfully.");
        } catch (Exception e) {
            logger.error("Error initializing Twilio client: {}", e.getMessage());
            throw new RuntimeException("Error initializing Twilio client", e);
        }
    }

    // Method that sends a verification OTP code to the user's phone number via Twilio Verify
    @Override
    public String sendVerificationCode(String phoneNumber) {
        // Input validation: Check for null, empty, and "+" prefix.
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.startsWith("+")) {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }

        try {
            Verification verification = Verification.creator(
                            verifyServiceSid,
                            phoneNumber,
                            "sms")          // Verification channel (SMS)
                    .create();

            System.out.println("Verification SID: " + verification.getSid()); // Log the SID
            return verification.getSid(); // Track unique identifier for each verification request

        } catch (ApiException e) {

            logger.error("Error sending verification code for phone number {}: {}", phoneNumber, e.getMessage(), e);
            // If you don't rethrow, the calling method won't know that sending the verification code failed.
            throw e;
        }
    }

    // Method to check if the OTP is valid or not using Twilio Verify
    @Override
    public boolean checkVerificationCode(String phoneNumber, String code, String verificationSid) {

        // phone number validation
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.startsWith("+")) {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }
        // code validation
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification code cannot be empty");
        }
        // verificationSid validation
        if (verificationSid == null || verificationSid.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification SID cannot be empty");
        }

        try {
            // Check the verification code using verificationSid => unique identifier for each verification request
            VerificationCheck verificationCheck = VerificationCheck.creator(verifyServiceSid)
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();

            logger.info("Twilio Verification Response: SID={}, Status={}",
                    verificationCheck.getSid(), verificationCheck.getStatus());

            return "approved".equals(verificationCheck.getStatus());

        } catch (ApiException e) {
            System.err.println("Error checking verification code: " + e.getMessage());
            return false; // Indicate failure instead of throwing an exception
        }
    }
}


