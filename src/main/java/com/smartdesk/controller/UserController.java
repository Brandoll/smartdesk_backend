package com.smartdesk.controller;

import com.smartdesk.model.dto.UserDTO;
import com.smartdesk.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints para la gestión de Usuarios del tenant")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Obtiene todos los usuarios con paginación.")
    public ResponseEntity<Page<UserDTO>> getAll(Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario", description = "Obtiene los detalles de un usuario específico.")
    public ResponseEntity<UserDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario en el tenant. Requiere enviar el password como header 'X-Temp-Password' temporalmente.")
    public ResponseEntity<UserDTO> create(@Valid @RequestBody UserDTO dto, @RequestHeader("X-Temp-Password") String password) {
        return ResponseEntity.ok(userService.createUser(dto, password));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Actualizar usuario", description = "Actualiza los detalles de un usuario.")
    public ResponseEntity<UserDTO> update(@PathVariable UUID id, @Valid @RequestBody UserDTO dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }
}
