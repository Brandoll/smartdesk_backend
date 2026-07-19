package com.smartdesk.controller;

import com.smartdesk.config.security.CustomUserDetails;
import com.smartdesk.model.dto.TicketDTO;
import com.smartdesk.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Endpoints para la gestión de Tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Listar tickets", description = "Obtiene los tickets paginados del tenant actual.")
    public ResponseEntity<Page<TicketDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ticketService.getAllTickets(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ticket", description = "Obtiene detalles de un ticket específico.")
    public ResponseEntity<TicketDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Obtener historial de ticket", description = "Obtiene el historial de eventos de un ticket específico.")
    public ResponseEntity<List<com.smartdesk.model.entity.TicketHistory>> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketHistory(id));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Obtener mensajes de ticket", description = "Obtiene los mensajes de chat de un ticket específico.")
    public ResponseEntity<List<com.smartdesk.model.entity.TicketMessage>> getMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketMessages(id));
    }

    @PostMapping
    @Operation(summary = "Crear ticket", description = "Crea un nuevo ticket.")
    public ResponseEntity<TicketDTO> create(@Valid @RequestBody TicketDTO dto, @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID creatorId = userDetails != null ? userDetails.getUser().getId() : null;
        return ResponseEntity.ok(ticketService.createTicket(dto, creatorId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar ticket", description = "Actualiza el estado, prioridad o asignación de un ticket.")
    public ResponseEntity<TicketDTO> update(@PathVariable UUID id, @Valid @RequestBody TicketDTO dto, @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID updaterId = userDetails != null ? userDetails.getUser().getId() : null;
        return ResponseEntity.ok(ticketService.updateTicket(id, dto, updaterId));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Agregar mensaje", description = "Agrega un mensaje al chat de un ticket.")
    public ResponseEntity<com.smartdesk.model.entity.TicketMessage> addMessage(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String message = (String) body.get("message");
        Boolean isInternal = body.get("isInternal") != null ? (Boolean) body.get("isInternal") : false;
        UUID userId = userDetails != null ? userDetails.getUser().getId() : null;
        String senderName = userDetails != null ? userDetails.getUser().getName() : "Unknown";
        return ResponseEntity.ok(ticketService.addMessage(id, userId, message, isInternal, senderName));
    }
}
