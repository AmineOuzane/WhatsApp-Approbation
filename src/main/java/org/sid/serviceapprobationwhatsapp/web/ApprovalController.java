package org.sid.serviceapprobationwhatsapp.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.sid.serviceapprobationwhatsapp.dto.ApprovalRequestDTO;
import org.sid.serviceapprobationwhatsapp.entities.ApprovalRequest;
import org.sid.serviceapprobationwhatsapp.enums.statut;
import org.sid.serviceapprobationwhatsapp.repositories.ApprovalRequestRepository;
import org.sid.serviceapprobationwhatsapp.service.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This controller handles the approval requests and manages the interactions with the WhatsApp service.
 * It includes endpoint for registering new approval requests and sending messages to approvers.
 * The controller uses asynchronous processing to handle requests efficiently.
 */

@Slf4j
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final WhatsAppService whatsAppService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;

    public ApprovalController(ApprovalRequestRepository approvalRequestRepository,
                              ObjectMapper objectMapper,
                              WhatsAppService whatsAppService ) {

        this.approvalRequestRepository = approvalRequestRepository;
        this.objectMapper = objectMapper;
        this.whatsAppService = whatsAppService;
    }

    // Principal Endpoint that receive the Request from the external system
    @Async
    @PostMapping("/register")
    public CompletableFuture<ResponseEntity<?>> registerApprovalRequest(@Valid @RequestBody ApprovalRequestDTO approvalRequestDTO) {

        log.info("Received request to register a new approval: {}", approvalRequestDTO);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Serialization des données et des métadonnées en JSON
                String dataJson = objectMapper.writeValueAsString(approvalRequestDTO.getApprovalData());
                String metadataJson = objectMapper.writeValueAsString(approvalRequestDTO.getMetadata());

                // Instanciation de object ApprovalRequest
                ApprovalRequest approvalRequest = ApprovalRequest.builder()
                        .objectType(approvalRequestDTO.getObjectType())
                        .objectId(approvalRequestDTO.getObjectId())
                        .objectLabel(approvalRequestDTO.getObjectLabel())
                        .data(dataJson)
                        .origin(approvalRequestDTO.getOrigin())
                        .approvers(approvalRequestDTO.getApprovers())
                        .demandeur(approvalRequestDTO.getDemandeur())
                        .commentaire("")
                        .callbackUrl(approvalRequestDTO.getCallbackUrl())
                        .metadata(metadataJson)
                        .decision(statut.Pending)
                        .requestTimeStamp(LocalDateTime.now())
                        .build();

                ApprovalRequest savedApprovalRequest = approvalRequestRepository.save(approvalRequest);
                log.info("Approval request saved with ID: {}", savedApprovalRequest.getId());

                // Send the Approval Request to the approvers
                whatsAppService.sendMessageWithInteractiveButtons(approvalRequest);

                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("approvalId", savedApprovalRequest.getId(), "message", "Approval request registered. Verification codes sent."));

            } catch (JsonProcessingException e) {
                log.error("Error serializing data or metadata to JSON", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid data format."));
            } catch (OptimisticLockingFailureException e) {
                log.error("Optimistic locking failure", e);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "The approval request was modified by another user. Please try again."));
            } catch (Exception e) {
                log.error("An unexpected error occurred", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
            }
        });
    }
}