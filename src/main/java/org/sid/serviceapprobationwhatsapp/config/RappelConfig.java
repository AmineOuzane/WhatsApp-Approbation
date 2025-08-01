package org.sid.serviceapprobationwhatsapp.config;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.RappelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableScheduling
public class RappelConfig {
    @Autowired
    private RappelService rappelService;
    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Scheduled(fixedDelay = 7200000) // 2 hours
    @Transactional
    public void sendRappelNotification() {
        // Fetch all pending requests for the reminder notification
        List<ApprovalRequest> pendingRequests = approvalRequestRepository.findByDecision(statut.Pending);
        // Check if there are any pending requests
        if (!pendingRequests.isEmpty()) {
            Set<String> approverPhoneNumbers = new HashSet<>();
            // Extract approver phone numbers from the pending requests
            pendingRequests.forEach(request -> {
                approverPhoneNumbers.addAll(request.getApprovers());
            });
            // Send reminder notification to the approvers, one reminder that regroup all the pending requests
            approverPhoneNumbers.forEach(approverPhoneNumber -> {
                rappelService.sendRappelMessage(approverPhoneNumber);
            });
        }
    }
}
