package com.smartdesk.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ABIERTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIA;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "assigned_to_id")
    private UUID assignedToId;

    @Column(name = "area_id")
    private UUID areaId;

    // AI suggested fields
    @Column(name = "ai_suggested_title")
    private String aiSuggestedTitle;

    @Column(name = "ai_suggested_area_id")
    private UUID aiSuggestedAreaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_priority")
    private Priority aiSuggestedPriority;

    @Column(name = "ai_classified")
    @Builder.Default
    private Boolean aiClassified = false;

    @Column(name = "ai_suggested_solution", columnDefinition = "TEXT")
    private String aiSuggestedSolution;

    // Resolution
    @Column(name = "resolution_comment", columnDefinition = "TEXT")
    private String resolutionComment;

    // Rating (1-5 stars)
    @Column
    private Integer rating;

    @Column(name = "rating_comment", columnDefinition = "TEXT")
    private String ratingComment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public enum Status {
        ABIERTO,
        ASIGNADO,
        EN_PROCESO,
        PROPUESTO,
        RESUELTO,
        CERRADO
    }

    public enum Priority {
        BAJA,
        MEDIA,
        ALTA,
        CRITICA
    }
}
