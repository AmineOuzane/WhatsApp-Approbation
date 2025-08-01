package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface RappelService {

    ResponseEntity<String> sendRappelMessage(String recipientNumber);
    List<ApprovalRequest> getPendingRequests();
    int countPendingRequests();
}
