package org.sid.serviceapprobationwhatsapp.service;

import java.io.IOException;

public interface InfobipService {

    void sendOtp(String phoneNumber, String otp) throws IOException;
}
