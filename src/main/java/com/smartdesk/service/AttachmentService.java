package com.smartdesk.service;

import com.smartdesk.model.entity.TicketAttachment;
import com.smartdesk.repository.TicketAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "application/pdf", "image/png", "image/jpeg", "image/jpg");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_ATTACHMENTS_PER_TICKET = 3;

    private final TicketAttachmentRepository ticketAttachmentRepository;
    private final Path fileStorageLocation;

    public AttachmentService(TicketAttachmentRepository ticketAttachmentRepository,
                             @Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.ticketAttachmentRepository = ticketAttachmentRepository;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("No se pudo crear el directorio de uploads.", ex);
        }
    }

    public TicketAttachment uploadFile(UUID ticketId, UUID userId, MultipartFile file) {
        // Validate file type
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("Tipo de archivo no permitido. Solo se aceptan: PDF, PNG, JPG, JPEG");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("El archivo excede el tamaño máximo de 5MB");
        }

        // Validate max attachments per ticket
        long currentCount = ticketAttachmentRepository.findByTicketId(ticketId).size();
        if (currentCount >= MAX_ATTACHMENTS_PER_TICKET) {
            throw new RuntimeException("Se ha alcanzado el límite de " + MAX_ATTACHMENTS_PER_TICKET + " archivos por ticket");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;

        try {
            if (storedFileName.contains("..")) {
                throw new RuntimeException("Nombre de archivo inválido: " + originalFileName);
            }

            Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicketId(ticketId);
            attachment.setUserId(userId);
            attachment.setFileUrl("/uploads/" + storedFileName);
            attachment.setFileName(originalFileName);
            attachment.setFileType(file.getContentType());
            attachment.setFileSize(file.getSize());

            return ticketAttachmentRepository.save(attachment);

        } catch (IOException ex) {
            throw new RuntimeException("Error al guardar el archivo: " + originalFileName, ex);
        }
    }
}
