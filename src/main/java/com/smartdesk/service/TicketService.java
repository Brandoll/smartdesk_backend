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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final com.smartdesk.config.websocket.ChatWebSocketHandler chatWebSocketHandler;

    public TicketService(TicketRepository ticketRepository, TicketHistoryRepository ticketHistoryRepository,
                         TicketMessageRepository ticketMessageRepository, AIClassifierService aiClassifierService,
                         com.smartdesk.config.websocket.NotificationWebSocketHandler notificationWebSocketHandler,
                         com.smartdesk.config.websocket.ChatWebSocketHandler chatWebSocketHandler) {
        this.ticketRepository = ticketRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.aiClassifierService = aiClassifierService;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    public List<TicketHistory> getTicketHistory(UUID ticketId) {
        return ticketHistoryRepository.findByTicketIdOrderByTimestampAsc(ticketId);
    }

    public List<TicketMessage> getTicketMessages(UUID ticketId) {
        return ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public Page<TicketDTO> getAllTickets(Pageable pageable) {
        return ticketRepository.findAll(newestFirst(pageable)).map(this::mapToDTO);
    }

    public Page<TicketDTO> getTicketsByArea(UUID areaId, Pageable pageable) {
        return ticketRepository.findByAreaId(areaId, newestFirst(pageable)).map(this::mapToDTO);
    }

    public TicketDTO getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));
        if (!Boolean.TRUE.equals(ticket.getAiClassified())) {
            aiClassifierService.classifyTicketAsync(id, TenantContext.getCurrentTenant());
        }
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

        // Run AI only after the ticket transaction commits, so the async thread
        // can read the newly-created row from the tenant schema.
        UUID ticketId = ticket.getId();
        String tenantId = TenantContext.getCurrentTenant();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiClassifierService.classifyTicketAsync(ticketId, tenantId);
            }
        });

        notifyRolesAfterCommit(tenantId, new String[]{"ADMIN_TENANT", "COLABORADOR_RESOLUTOR"},
                "NEW_TICKET", ticketId, "Nuevo caso reportado: " + ticket.getTitle());
        notifyUserAfterCommit(tenantId, ticket.getClientId(), "TICKET_CREATED", ticketId,
                "Tu caso fue creado correctamente");

        return mapToDTO(ticket);
    }

    @Transactional
    public TicketDTO updateTicket(UUID id, TicketDTO dto, UUID updaterId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));
        String tenantId = TenantContext.getCurrentTenant();

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

            String message = "El estado del caso cambió a " + dto.getStatus().name();
            notifyUserAfterCommit(tenantId, ticket.getClientId(), "STATUS_CHANGED", ticket.getId(), message);
            notifyUserAfterCommit(tenantId, ticket.getAssignedToId(), "STATUS_CHANGED", ticket.getId(), message);
        }

        // Priority change
        if (dto.getPriority() != null && ticket.getPriority() != dto.getPriority()) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.PRIORITY_CHANGED,
                        ticket.getPriority().name(), dto.getPriority().name());
            ticket.setPriority(dto.getPriority());
            String message = "La prioridad del caso cambió a " + dto.getPriority().name();
            notifyUserAfterCommit(tenantId, ticket.getClientId(), "PRIORITY_CHANGED", ticket.getId(), message);
            notifyUserAfterCommit(tenantId, ticket.getAssignedToId(), "PRIORITY_CHANGED", ticket.getId(), message);
        }

        // Area change (reclassification)
        if (dto.getAreaId() != null && !dto.getAreaId().equals(ticket.getAreaId())) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.AREA_CHANGED,
                        String.valueOf(ticket.getAreaId()), String.valueOf(dto.getAreaId()));
            ticket.setAreaId(dto.getAreaId());
            String message = "El área del caso fue actualizada";
            notifyUserAfterCommit(tenantId, ticket.getClientId(), "AREA_CHANGED", ticket.getId(), message);
            notifyUserAfterCommit(tenantId, ticket.getAssignedToId(), "AREA_CHANGED", ticket.getId(), message);
        }

        // Assignment
        if (dto.getAssignedToId() != null && !dto.getAssignedToId().equals(ticket.getAssignedToId())) {
            saveHistory(ticket.getId(), updaterId, TicketHistory.EventType.ASSIGNED,
                        String.valueOf(ticket.getAssignedToId()), String.valueOf(dto.getAssignedToId()));
            ticket.setAssignedToId(dto.getAssignedToId());
            notifyUserAfterCommit(tenantId, ticket.getClientId(), "ASSIGNED", ticket.getId(),
                    "Se asignó un resolutor a tu caso");
            notifyUserAfterCommit(tenantId, dto.getAssignedToId(), "ASSIGNED", ticket.getId(),
                    "Te han asignado un nuevo caso: " + ticket.getTitle());
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
        UUID updatedTicketId = ticket.getId();
        runAfterCommit(() -> chatWebSocketHandler.broadcastEvent(updatedTicketId, java.util.Map.of(
                "type", "TICKET_UPDATED",
                "ticketId", updatedTicketId.toString()
        )));
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
        msg = ticketMessageRepository.save(msg);
        chatWebSocketHandler.broadcastMessage(ticketId, msg);
        return msg;
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

    private void notifyUserAfterCommit(String tenantId, UUID userId, String type,
                                       UUID ticketId, String message) {
        if (userId == null) return;
        runAfterCommit(() -> notificationWebSocketHandler.notifyUser(
                tenantId, userId.toString(), notificationPayload(type, ticketId, message)));
    }

    private void notifyRolesAfterCommit(String tenantId, String[] roles, String type,
                                        UUID ticketId, String message) {
        runAfterCommit(() -> notificationWebSocketHandler.notifyRolesInTenant(
                tenantId, roles, notificationPayload(type, ticketId, message)));
    }

    private java.util.Map<String, String> notificationPayload(String type, UUID ticketId, String message) {
        return java.util.Map.of(
                "type", type,
                "ticketId", ticketId.toString(),
                "message", message
        );
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
        dto.setAiSuggestedSolution(ticket.getAiSuggestedSolution());
        dto.setResolutionComment(ticket.getResolutionComment());
        dto.setRating(ticket.getRating());
        dto.setRatingComment(ticket.getRatingComment());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setResolvedAt(ticket.getResolvedAt());
        dto.setClosedAt(ticket.getClosedAt());
        return dto;
    }

    private Pageable newestFirst(Pageable pageable) {
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
