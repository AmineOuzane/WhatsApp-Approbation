package org.sid.serviceapprobationwhatsapp.repositories;

import org.sid.serviceapprobationwhatsapp.entities.OtpResendMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpResendMappingRepository extends JpaRepository<OtpResendMapping, String> {

    Optional<OtpResendMapping> findByMappingId(String mappingId);

}
