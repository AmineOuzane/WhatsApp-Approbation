package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.springframework.http.ResponseEntity;

public interface WhatsAppService {
    ResponseEntity<String> sendMessageWithInteractiveButtons(ApprovalRequest approvalRequest);

    String extractContextIdFromResponse(String jsonResponse);

    ResponseEntity<String> sendCommentaire(String approvalId,
                                           String recipientNumber
                        );

}

