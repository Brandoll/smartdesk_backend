package com.smartdesk.model.dto;

import com.smartdesk.model.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TicketDTO {
    private UUID id;

    @NotBlank(message = "El título es obligatorio")
    private String title;

    @Size(min = 20, max = 5000, message = "La descripción debe tener entre 20 y 5000 caracteres")
    private String description;

    private Ticket.Status status;
    private Ticket.Priority priority;

    // Optional in creation - extracted from JWT
    private UUID clientId;

    private UUID assignedToId;
    private UUID areaId;

    // AI suggestions (read-only, returned by backend)
    private String aiSuggestedTitle;
    private UUID aiSuggestedAreaId;
    private Ticket.Priority aiSuggestedPriority;
    private Boolean aiClassified;
    private String aiSuggestedSolution;

    // Resolution
    private String resolutionComment;

    // Rating
    private Integer rating;
    private String ratingComment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
