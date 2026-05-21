package com.smartdesk.model.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UserResponseDTO {
    private UUID id;
    private String nombres;
    private String email;
    private Boolean isActive;
}
