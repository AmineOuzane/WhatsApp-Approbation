package org.sid.serviceapprobationwhatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Data
public class ApprovalRequestDTO {

    @NotBlank(message = "Object type is required")
    private String objectType;

    @NotBlank(message = "Object id is required")
    private String objectId;

    @NotBlank(message = "Object label is required")
    private String objectLabel;

    @NotNull
    private Map<String,Object> approvalData;

    @NotBlank(message = "Origin is required")
    private String origin;

    @NotEmpty(message = "Approver list cant be empty")
    private List<String> approvers;

    @NotBlank(message = "Demandeur is required")
    private String demandeur;

    @NotBlank(message = "Callback URL is required")
    private String callbackUrl;

    private Map<String,Object> metadata;

}
