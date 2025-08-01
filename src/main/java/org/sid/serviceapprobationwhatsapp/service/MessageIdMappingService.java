package org.sid.serviceapprobationwhatsapp.service;

public interface MessageIdMappingService {

    void storeMapping(String messageId, String approvalId);
    void clearMapping();
    void logAllMappings();
    int getMapSize();
    String getApprovalId(String messageId) ;

}
