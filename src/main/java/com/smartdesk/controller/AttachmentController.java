package com.smartdesk.controller;

import com.smartdesk.config.security.CustomUserDetails;
import com.smartdesk.model.entity.TicketAttachment;
import com.smartdesk.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/attachments")
@Tag(name = "Attachments", description = "Endpoints para la gestión de adjuntos de tickets")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Subir adjunto", description = "Sube un archivo adjunto a un ticket.")
    public ResponseEntity<TicketAttachment> uploadFile(@PathVariable UUID ticketId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails != null ? userDetails.getUser().getId() : null;
        return ResponseEntity.ok(attachmentService.uploadFile(ticketId, userId, file));
    }
}
