package org.sid.serviceapprobationwhatsapp.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OtpResendMapping {
    @Id
    private String mappingId;
    @Column(name = "approval_id", nullable = false)
    private String approvalId;
    @Column(name = "recepient_number", nullable = false)
    private String recipientNumber;
    @Column(name = "expiration", nullable = false)
    private LocalDateTime expiration;
}
