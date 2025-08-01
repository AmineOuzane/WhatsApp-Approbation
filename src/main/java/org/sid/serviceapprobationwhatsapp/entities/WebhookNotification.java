package org.sid.serviceapprobationwhatsapp.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Data
@NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "approvalRequest_id")
    private ApprovalRequest approvalRequest;

    private Date createdAt;
}
