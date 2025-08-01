package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import okhttp3.*;
import org.sid.serviceapprobationwhatsapp.service.InfobipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class InfobipServiceImpl implements InfobipService {

    @Value("${infobip.api.key}")
    private String apiKey;

    @Value("${infobip.api.base-url}")
    private String baseUrl;

    public void sendOtp(String phoneNumber, String otp) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        String url = baseUrl + "/sms/2/text/advanced";
        String json = "{\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"from\": \"ServiceSMS\",\n" +
                "      \"destinations\": [\n" +
                "        {\n" +
                "          \"to\": \"" + phoneNumber + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"text\": \"Your OTP code is: " + otp + "\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "App " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            System.out.println("OTP sent successfully: " + (response.body() != null ? response.body().string() : null));
        }
    }

}
