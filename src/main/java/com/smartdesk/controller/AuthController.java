package com.smartdesk.controller;

import com.smartdesk.model.dto.ActivationDTO;
import com.smartdesk.model.dto.AuthResponseDTO;
import com.smartdesk.model.dto.InitRegistrationDTO;
import com.smartdesk.model.dto.LoginDTO;
import com.smartdesk.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints para registro, activación y login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario resolviendo su tenant de forma automática y devuelve un JWT.")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return ResponseEntity.ok(authService.login(loginDTO));
    }

    @PostMapping("/register")
    @Operation(summary = "Paso 1: Iniciar Registro (Sandbox)", description = "Registra un usuario inactivo y le envía un correo con un token de verificación.")
    public ResponseEntity<Void> initRegistration(@Valid @RequestBody InitRegistrationDTO dto) {
        authService.initRegistration(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/activate")
    @Operation(summary = "Paso 2: Activar y Aprovisionar Tenant", description = "Valida el token recibido por correo, crea el Tenant, la base de datos aislada y activa al usuario administrador.")
    public ResponseEntity<AuthResponseDTO> activateTenant(@Valid @RequestBody ActivationDTO dto) {
        return ResponseEntity.ok(authService.activateTenant(dto));
    }
}
