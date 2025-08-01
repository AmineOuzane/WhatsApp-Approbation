package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.sid.serviceapprobationwhatsapp.service.SMSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;


/**
 * This service is responsible for sending SMS messages using the Twilio API.
 * It initializes the Twilio client with the provided account SID and authentication token,
 * and provides a method to send SMS messages to a specified phone number.
 * This is a bypass to OTPs using normal SMS and not Verify by Twilio, which is used by default.
 * This service is used when the user wants to receive the OTP via SMS but does not want to use Verify by Twilio.
 */


@Service
public class SMSServiceImpl implements SMSService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone_number}") // Injects the Twilio Phone Number from the application.properties file
    private String twilioPhoneNumber;

    @Value("${bulksms.api.key}")
    private String bulkSmsApiKey;

    @Value("${bulksms.api.base-url}")
    private String bulkSmsBaseUrl;

    Logger logger = LoggerFactory.getLogger(TwilioServiceImpl.class);


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

    @Override
    public void sendSmsWithBulk(String toPhoneNumber, String otp) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        String message = "Your OTP code is: " + otp;
        // BulkSMS.ma expects application/x-www-form-urlencoded
        RequestBody body = new FormBody.Builder()
                .add("token", bulkSmsApiKey)
                .add("tel", toPhoneNumber) // e.g., "0600000000,0700000000"
                .add("message", message)
                .build();

        Request request = new Request.Builder()
                .url(bulkSmsBaseUrl) // e.g., https://bulksms.ma/developer/sms/send
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded") //
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to send SMS: " + response.code() + " - " + response.message());
            }
            String responseBody = response.body() != null ? response.body().string() : "No response body";
            logger.info("BulkSMS response: {}", responseBody);
        } catch (IOException e) {
            logger.error("Error sending SMS with BulkSMS: {}", e.getMessage());
            throw e; // Re-throw the exception to handle it in the calling method
        }
    }
}