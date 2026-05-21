package com.smartdesk.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivationDTO {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    private String companyName;
}
