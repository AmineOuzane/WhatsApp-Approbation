package org.sid.serviceapprobationwhatsapp.repositories;

import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {

    @Query("SELECT ar FROM ApprovalRequest ar WHERE :phoneNumber MEMBER OF ar.approvers AND ar.decision IN :statuses")
    List<ApprovalRequest> findByApproverPhoneNumberAndDecisionIn(@Param("phoneNumber") String phoneNumber, @Param("statuses") List<statut> status);

    @Query("SELECT ar FROM ApprovalRequest ar WHERE :approver MEMBER OF ar.approvers")
    List<ApprovalRequest> findByApproversContaining(@Param("approver") String approver);

    @Query("SELECT ar FROM ApprovalRequest ar JOIN ar.approvers approver WHERE approver = :approver")
    Optional<ApprovalRequest> findByApprover(@Param("approver") String approver);

    @Query("SELECT a FROM ApprovalRequest a WHERE :senderPhoneNumber MEMBER OF a.approvers AND a.id = :approvalId")
    Optional<ApprovalRequest> findByApproverAndId(@Param("approvalId") String approvalId, @Param("senderPhoneNumber") String senderPhoneNumber);

    List<ApprovalRequest> findByDecision(statut decision);

    // (Optionnel) Si vous souhaitez avoir une requête personnalisée pour le regroupement
//    @Query("SELECT a FROM ApprovalRequest a WHERE a.decision = :statut ORDER BY a.approvers ASC")
//    List<ApprovalRequest> findPendingRequests(@Param("decision") statut statut);
}
