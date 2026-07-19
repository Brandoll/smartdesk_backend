package com.smartdesk.controller;

import com.smartdesk.model.dto.AreaDTO;
import com.smartdesk.service.AreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/areas")
@Tag(name = "Areas", description = "Endpoints para la gestión de Áreas/Departamentos")
public class AreaController {

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    @GetMapping
    @Operation(summary = "Listar áreas", description = "Obtiene todas las áreas del tenant actual.")
    public ResponseEntity<List<AreaDTO>> getAll() {
        return ResponseEntity.ok(areaService.getAllAreas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener área", description = "Obtiene los detalles de un área específica.")
    public ResponseEntity<AreaDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(areaService.getAreaById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Crear área", description = "Crea un nuevo departamento en el tenant.")
    public ResponseEntity<AreaDTO> create(@Valid @RequestBody AreaDTO dto) {
        return ResponseEntity.ok(areaService.createArea(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Actualizar área", description = "Actualiza los detalles de un departamento.")
    public ResponseEntity<AreaDTO> update(@PathVariable UUID id, @Valid @RequestBody AreaDTO dto) {
        return ResponseEntity.ok(areaService.updateArea(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Eliminar área", description = "Elimina un departamento del sistema.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        areaService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }
}
