package org.sid.serviceapprobationwhatsapp.service;

import org.springframework.http.ResponseEntity;

public interface OtpVerification {

    ResponseEntity<?> processOtpVerification(String phoneNumber, String messageBody);
}
