package com.smartdesk.service;

import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.dto.TicketDTO;
import com.smartdesk.model.entity.Ticket;
import com.smartdesk.model.entity.TicketHistory;
import com.smartdesk.model.entity.TicketMessage;
import com.smartdesk.repository.TicketHistoryRepository;
import com.smartdesk.repository.TicketMessageRepository;
import com.smartdesk.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final AIClassifierService aiClassifierService;
    private final com.smartdesk.config.websocket.NotificationWebSocketHandler notificationWebSocketHandler;

    public TicketService(TicketRepository ticketRepository, TicketHistoryRepository ticketHistoryRepository,
                         TicketMessageRepository ticketMessageRepository, AIClassifierService aiClassifierService,
                         com.smartdesk.config.websocket.NotificationWebSocketHandler notificationWebSocketHandler) {
        this.ticketRepository = ticketRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.aiClassifierService = aiClassifierService;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    public List<TicketHistory> getTicketHistory(UUID ticketId) {
        return ticketHistoryRepository.findByTicketIdOrderByTimestampAsc(ticketId);
    }

    public List<TicketMessage> getTicketMessages(UUID ticketId) {
        return ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public Page<TicketDTO> getAllTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable).map(this::mapToDTO);
    }

    public TicketDTO getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));
        return mapToDTO(ticket);
    }

    @Transactional
    public TicketDTO createTicket(TicketDTO dto, UUID creatorId) {
        // Validate description length
        if (dto.getDescription() == null || dto.getDescription().length() < 20) {
            throw new RuntimeException("La descripción debe tener al menos 20 caracteres");
        }
        if (dto.getDescription().length() > 5000) {
            throw new RuntimeException("La descripción no puede exceder 5000 caracteres");
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setStatus(Ticket.Status.ABIERTO); // Always starts as ABIERTO
        ticket.setPriority(dto.getPriority() != null ? dto.getPriority() : Ticket.Priority.MEDIA);

        // clientId: prefer JWT user, fallback to DTO
        ticket.setClientId(creatorId != null ? creatorId : dto.getClientId());
        if (ticket.getClientId() == null) {
            throw new RuntimeException("No se pudo identificar al creador del ticket");
        }

        ticket.setAreaId(dto.getAreaId());

        ticket = ticketRepository.save(ticket);

        // Record in history
        saveHistory(ticket.getId(), creatorId, TicketHistory.EventType.TICKET_CREATED, null, "Ticket creado");

        // Trigger async AI classification
        try {
            aiClassifierService.classifyTicketAsync(ticket.getId(), TenantContext.getCurrentTenant());
        } catch (Exception e) {
            log.warn("AI classification failed for ticket {}: {}", ticket.getId(), e.getMessage());
            // Ticket created successfully, AI is optional
        }

        // Send real-time notification to admins and resolutors
        String messageStr = String.format("{\"type\":\"NEW_TICKET\",\"ticketId\":\"%s\",\"message\":\"Nuevo caso reportado: %s\"}", 
            ticket.getId(), ticket.getTitle());
        notificationWebSocketHandler.notifyRolesInTenant(TenantContext.getCurrentTenant(), 
            new String[]{"ADMIN", "RESOLUTOR"}, messageStr);

        return mapToDTO(ticket);
    }

    @Transactional
    public TicketDTO updateTicket(UUID id, TicketDTO dto, UUID updaterId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        // Status change with validation
        if (dto.getStatus() != null && ticket.getStatus() != dto.getStatus()) {
            validateStatusTransition(ticket.getStatus(), dto.getStatus());
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.STATUS_CHANGED,
                        ticket.getStatus().name(), dto.getStatus().name());
            ticket.setStatus(dto.getStatus());

            // Set timestamps for specific transitions
            if (dto.getStatus() == Ticket.Status.RESUELTO) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
            if (dto.getStatus() == Ticket.Status.CERRADO) {
                ticket.setClosedAt(LocalDateTime.now());
            }

            // Send real-time notification to the ticket creator
            String messageStr = String.format("{\"type\":\"STATUS_CHANGED\",\"ticketId\":\"%s\",\"message\":\"El estado de tu caso ha cambiado a %s\"}", 
                ticket.getId(), dto.getStatus().name());
            notificationWebSocketHandler.notifyUser(TenantContext.getCurrentTenant(), 
                ticket.getClientId().toString(), messageStr);
        }

        // Priority change
        if (dto.getPriority() != null && ticket.getPriority() != dto.getPriority()) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.PRIORITY_CHANGED,
                        ticket.getPriority().name(), dto.getPriority().name());
            ticket.setPriority(dto.getPriority());
        }

        // Area change (reclassification)
        if (dto.getAreaId() != null && !dto.getAreaId().equals(ticket.getAreaId())) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.AREA_CHANGED,
                        String.valueOf(ticket.getAreaId()), String.valueOf(dto.getAreaId()));
            ticket.setAreaId(dto.getAreaId());
        }

        // Assignment
        if (dto.getAssignedToId() != null && !dto.getAssignedToId().equals(ticket.getAssignedToId())) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.ASSIGNED,
                        String.valueOf(ticket.getAssignedToId()), String.valueOf(dto.getAssignedToId()));
            ticket.setAssignedToId(dto.getAssignedToId());
        }

        // Resolution comment
        if (dto.getResolutionComment() != null) {
            ticket.setResolutionComment(dto.getResolutionComment());
        }

        // Rating
        if (dto.getRating() != null) {
            if (dto.getRating() < 1 || dto.getRating() > 5) {
                throw new RuntimeException("La calificación debe ser entre 1 y 5");
            }
            ticket.setRating(dto.getRating());
            ticket.setRatingComment(dto.getRatingComment());
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.RATED,
                        null, String.valueOf(dto.getRating()));
        }

        // Update title/description only if provided
        if (dto.getTitle() != null) {
            ticket.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            ticket.setDescription(dto.getDescription());
        }

        ticket = ticketRepository.save(ticket);
        return mapToDTO(ticket);
    }

    @Transactional
    public TicketMessage addMessage(UUID ticketId, UUID userId, String message, boolean isInternal, String senderName) {
        // Verify ticket exists
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        TicketMessage msg = new TicketMessage();
        msg.setTicketId(ticketId);
        msg.setUserId(userId);
        msg.setMessage(message);
        msg.setIsInternal(isInternal);
        msg.setSenderType(TicketMessage.SenderType.USER);
        msg.setSenderName(senderName);
        return ticketMessageRepository.save(msg);
    }

    private void validateStatusTransition(Ticket.Status current, Ticket.Status next) {
        boolean valid = switch (current) {
            case ABIERTO -> next == Ticket.Status.ASIGNADO || next == Ticket.Status.EN_PROCESO;
            case ASIGNADO -> next == Ticket.Status.EN_PROCESO;
            case EN_PROCESO -> next == Ticket.Status.PROPUESTO;
            case PROPUESTO -> next == Ticket.Status.RESUELTO || next == Ticket.Status.EN_PROCESO; // accept or reject
            case RESUELTO -> next == Ticket.Status.CERRADO;
            case CERRADO -> false; // Terminal state
        };

        if (!valid) {
            throw new RuntimeException(
                String.format("Transición de estado inválida: %s → %s", current.name(), next.name()));
        }
    }

    private void saveHistory(UUID ticketId, UUID userId, TicketHistory.EventType type, String oldVal, String newVal) {
        TicketHistory history = new TicketHistory();
        history.setTicketId(ticketId);
        history.setUserId(userId);
        history.setEventType(type);
        history.setOldValue(oldVal);
        history.setNewValue(newVal);
        ticketHistoryRepository.save(history);
    }

    private TicketDTO mapToDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setStatus(ticket.getStatus());
        dto.setPriority(ticket.getPriority());
        dto.setClientId(ticket.getClientId());
        dto.setAssignedToId(ticket.getAssignedToId());
        dto.setAreaId(ticket.getAreaId());
        dto.setAiSuggestedTitle(ticket.getAiSuggestedTitle());
        dto.setAiSuggestedAreaId(ticket.getAiSuggestedAreaId());
        dto.setAiSuggestedPriority(ticket.getAiSuggestedPriority());
        dto.setAiClassified(ticket.getAiClassified());
        dto.setResolutionComment(ticket.getResolutionComment());
        dto.setRating(ticket.getRating());
        dto.setRatingComment(ticket.getRatingComment());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setResolvedAt(ticket.getResolvedAt());
        dto.setClosedAt(ticket.getClosedAt());
        return dto;
    }
}
