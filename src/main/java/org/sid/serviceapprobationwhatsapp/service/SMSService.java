package org.sid.serviceapprobationwhatsapp.service;

import java.io.IOException;

public interface SMSService {
    void sendSmsWithBulk(String toPhoneNumber, String messageBody) throws IOException;
}
