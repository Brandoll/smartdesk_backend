package com.smartdesk.controller;

import com.smartdesk.model.dto.ActivationDTO;
import com.smartdesk.model.dto.InitRegistrationDTO;
import com.smartdesk.model.dto.UserResponseDTO;
import com.smartdesk.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<Void> initRegistration(@Valid @RequestBody InitRegistrationDTO dto) {
        registrationService.initRegistration(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/activate")
    public ResponseEntity<UserResponseDTO> activateTenant(@Valid @RequestBody ActivationDTO dto) {
        UserResponseDTO response = registrationService.activateTenant(dto);
        return ResponseEntity.ok(response);
    }
}
