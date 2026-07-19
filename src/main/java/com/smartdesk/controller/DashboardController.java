package com.smartdesk.controller;

import com.smartdesk.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Endpoints para métricas y estadísticas del Admin Panel")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Obtener métricas", description = "Retorna estadísticas generales del tenant (tickets por estado, prioridad, total de usuarios).")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(dashboardService.getMetrics());
    }
}
