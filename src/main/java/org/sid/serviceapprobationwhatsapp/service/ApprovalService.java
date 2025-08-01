package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;

import java.util.List;


public interface ApprovalService {
    void updateStatus(String id, statut decision);
    void sendOtpAndCreateApprovalOTP(ApprovalRequest approvalRequest, String phoneNumber);
    ApprovalRequest saveApprovalRequest(ApprovalRequest approvalRequest);
    ApprovalRequest getApproval(String approvalId);
}
