package com.smartdesk.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class AreaDTO {
    private UUID id;

    @NotBlank
    private String name;

    private String description;

    private Boolean active;
}
