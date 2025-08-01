package org.sid.serviceapprobationwhatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.sid.serviceapprobationwhatsapp.enums.statut;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApprovalResponseDTO {

    @NotBlank(message = "Object type is required")
    private String objectType;

    @NotBlank(message = "Object id is required")
    private String objectId;

    @NotBlank(message = "Approver Number is required")
    private String approverNumber; // Personne ayant traité la demande

    @NotBlank(message = "approval_status is required")
    private statut approval_status; // Decision de l'approbation

    @NotBlank(message = "Commentaire is required")
    private String comment;

    @NotBlank(message = "Metadata is required")
    private Map<String,Object> metadata;

}
