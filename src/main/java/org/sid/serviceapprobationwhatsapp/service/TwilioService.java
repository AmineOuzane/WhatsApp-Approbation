package org.sid.serviceapprobationwhatsapp.service;

public interface TwilioService {


    String sendVerificationCode(String phoneNumber);
    boolean checkVerificationCode(String phoneNumber, String code, String verificationSid);
}
