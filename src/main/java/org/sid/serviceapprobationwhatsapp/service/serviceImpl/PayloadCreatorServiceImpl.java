package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.json.JSONObject;
import org.sid.serviceapprobationwhatsapp.service.PayloadCreatorService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service

// Service to create common payload objects for WhatsApp Template API
public class PayloadCreatorServiceImpl implements PayloadCreatorService {

    // Method to create the base request body that contain the phone number and messaging product
    @Override
    public JSONObject createBaseRequestBody(String recipientNumber) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("messaging_product", "whatsapp");
        requestBody.put("to", recipientNumber);
        requestBody.put("type", "template");
        return requestBody;
    }

    // Method to create the template object with the template name and language code
    @Override
    public JSONObject createTemplateObject(String templateName) {
        JSONObject template = new JSONObject();
        template.put("name", templateName);
        template.put("language", new JSONObject().put("code", "en"));
        return template;
    }

    // Method to create the text parameter for the template message
    @Override
    public JSONObject createTextParameter(String text) {
        return new JSONObject().put("type", "text").put("text", text);
    }
}
