package org.sid.serviceapprobationwhatsapp.service.serviceImpl;


import org.json.JSONArray;
import org.json.JSONObject;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.PayloadCreatorService;
import org.sid.serviceapprobationwhatsapp.service.RappelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RappelServiceImpl implements RappelService {

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Value("${whatsapp.api.token}")
    private String whatsappApiToken;

    private final ApprovalRequestRepository approvalRequestRepository;

    private static final Logger logger = LoggerFactory.getLogger(RappelServiceImpl.class);

   private final PayloadCreatorService payloadCreatorService;

    public RappelServiceImpl(PayloadCreatorService payloadCreatorService, ApprovalRequestRepository approvalRequestRepository) {
        this.payloadCreatorService = payloadCreatorService;
        this.approvalRequestRepository = approvalRequestRepository;
    }

    // initiating the header for the request to the WhatsApp API
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + whatsappApiToken);
        return headers;
    }

    // Method to send a reminder message template to the recipient
    @Override
    public ResponseEntity<String> sendRappelMessage(String recipientNumber) {

        System.out.println("Retrieved recipient phone number info: " + recipientNumber);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + whatsappApiToken);

        JSONObject requestBody = payloadCreatorService.createBaseRequestBody(recipientNumber);
        JSONObject template = payloadCreatorService.createTemplateObject("notif_rappel");

        requestBody.put("template", template);

        JSONArray components = new JSONArray();

        // Title Component
        JSONObject titleComponent = new JSONObject();
        titleComponent.put("type", "header");
        components.put(titleComponent);

        // Body Component
        JSONArray bodyParameters = new JSONArray();
        bodyParameters.put(payloadCreatorService.createTextParameter(String.valueOf(countPendingRequests()))); // {{1}}

        List<String> objectIdList = getPendingRequests().stream()
                .map(ApprovalRequest::getObjectId)
                .collect(Collectors.toList());

        String objectIdString = String.join(",", objectIdList);

        bodyParameters.put(payloadCreatorService.createTextParameter(objectIdString));
        JSONObject bodyComponent = new JSONObject();
        bodyComponent.put("type", "body");
        bodyComponent.put("parameters", bodyParameters);
        components.put(bodyComponent);

        // Finalize the template and request body
        template.put("components", components);
        requestBody.put("template", template);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), createHeaders());
        return restTemplate.postForEntity(whatsappApiUrl, request, String.class);
    }

    // Method retrieving all pending requests for the reminder message
    @Override
    public List<ApprovalRequest> getPendingRequests() {
        logger.info("Retrieving all pending requests for the reminder message");
        return approvalRequestRepository.findByDecision(statut.Pending);
    }

    // Method counting the number of pending requests for the reminder message
    @Override
    public int countPendingRequests() {
        logger.info(" number of pending requests: {}", approvalRequestRepository.findByDecision(statut.Pending).size());
        return approvalRequestRepository.findByDecision(statut.Pending).size();
    }
}
