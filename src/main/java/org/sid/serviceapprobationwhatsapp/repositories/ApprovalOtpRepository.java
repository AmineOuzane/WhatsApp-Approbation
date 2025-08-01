package org.sid.serviceapprobationwhatsapp.repositories;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalOTP;
import org.sid.serviceapprobationwhatsapp.enums.otpStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ApprovalOtpRepository extends JpaRepository<ApprovalOTP, Long> {

    @Modifying
    @Query("UPDATE ApprovalOTP a SET a.status = :newStatus WHERE a.recipientNumber = :phoneNumber AND a.status = :oldStatus")
    void updateStatusByPhoneNumber(@Param("phoneNumber") String phoneNumber,
                                   @Param("oldStatus") otpStatut oldStatus,
                                   @Param("newStatus") otpStatut newStatus);
    // Custom query method to find the most recent pending OTP for a given phone number
    Optional<ApprovalOTP> findTopByRecipientNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, otpStatut status);

    ApprovalOTP findByRecipientNumberAndOtpAndExpirationAfter(String recipientNumber, String otp, LocalDateTime now);

    void deleteByRecipientNumber(String recipientNumber);

    ApprovalOTP findByRecipientNumber(String recipientNumber);


}
