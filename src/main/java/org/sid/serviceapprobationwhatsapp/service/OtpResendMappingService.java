package org.sid.serviceapprobationwhatsapp.service;

import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;

import java.util.Optional;

public interface OtpResendMappingService {

    OtpResendMapping createResendMapping(String approvalId, String phoneNumber);
    Optional<OtpResendMapping> getResendMapping(String mappingId);
    void deleteResendMapping(String mappingId);

}
