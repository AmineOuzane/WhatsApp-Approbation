package org.sid.serviceapprobationwhatsapp.service.serviceImpl;

import org.sid.serviceapprobationwhatsapp.service.MessageIdMappingService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageIdMappingServiceImpl implements MessageIdMappingService {

    // Use ConcurrentHashMap for thread safety
    private final Map<String, String> messageIdToApprovalIdMap = new ConcurrentHashMap<>();

    // Method to store the mapping between context message ID and approval ID
    @Override
    public void storeMapping(String messageId, String approvalId) {
        messageIdToApprovalIdMap.put(messageId, approvalId);
        logAllMappings();
    }

    @Override
    public void clearMapping() {
        messageIdToApprovalIdMap.clear();
    }

    @Override
    public void logAllMappings() {
        messageIdToApprovalIdMap.forEach((key, value) ->
                System.out.println("Message ID: " + key + " -> Approval ID: " + value));
    }

    @Override
    public int getMapSize() {
        return messageIdToApprovalIdMap.size();
    }

    @Override
    public String getApprovalId(String messageId) {
        return messageIdToApprovalIdMap.get(messageId);
    }

}
