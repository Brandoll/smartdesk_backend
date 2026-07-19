package com.smartdesk.model.dto;

import com.smartdesk.model.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UserDTO {
    private UUID id;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo inválido")
    private String email;

    private User.Role role;
    private User.Status status;

    // Multiple areas support
    private List<UUID> areaIds;

    private LocalDateTime createdAt;
}
