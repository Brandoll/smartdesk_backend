package com.smartdesk.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;

    public enum EventType {
        TICKET_CREATED,
        STATUS_CHANGED,
        PRIORITY_CHANGED,
        AREA_CHANGED,
        ASSIGNED,
        RECLASSIFIED,
        RESOLUTION_PROPOSED,
        RESOLUTION_ACCEPTED,
        RESOLUTION_REJECTED,
        COMMENT_ADDED,
        INTERNAL_NOTE_ADDED,
        ATTACHMENT_ADDED,
        AI_CLASSIFIED,
        RATED
    }
}
