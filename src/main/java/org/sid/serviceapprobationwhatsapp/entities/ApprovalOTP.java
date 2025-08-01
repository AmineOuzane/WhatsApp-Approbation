package org.sid.serviceapprobationwhatsapp.entities;

import jakarta.persistence.*;
import lombok.*;

import org.sid.serviceapprobationwhatsapp.enums.otpStatus;
import org.sid.serviceapprobationwhatsapp.enums.statut;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_otp")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalOTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otpId;

    @Column(name = "phone_number", nullable = false)
    private String recipientNumber;

//    @Column(name = "verification_sid", nullable = false)
//    private String verificationSid; // From Twilio Verify

    @Column(name = "otp", nullable = false)
    private String otp;

    @Column(name = "decision", nullable = false)
    private statut decision;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private otpStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "invalid_attempts")
    private int invalidattempts;

    // This is useful to avoid potential issues like infinite recursion when the approvalRequest field references back to the current entity.
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", referencedColumnName = "id")
    private ApprovalRequest approvalRequest;


}