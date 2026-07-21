package com.smartdesk.service;

import com.smartdesk.model.entity.Area;
import com.smartdesk.model.entity.Ticket;
import com.smartdesk.repository.AreaRepository;
import com.smartdesk.repository.TicketRepository;
import com.smartdesk.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AreaRepository areaRepository;

    public DashboardService(TicketRepository ticketRepository, UserRepository userRepository,
                            AreaRepository areaRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.areaRepository = areaRepository;
    }

    public Map<String, Object> getMetrics() {
        List<Ticket> tickets = ticketRepository.findAll();
        List<Area> areas = areaRepository.findAll();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Ticket.Status status : Ticket.Status.values()) byStatus.put(status.name(), 0L);
        tickets.forEach(ticket -> byStatus.compute(ticket.getStatus().name(), (key, value) -> value + 1));

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Ticket.Priority priority : Ticket.Priority.values()) byPriority.put(priority.name(), 0L);
        tickets.forEach(ticket -> byPriority.compute(ticket.getPriority().name(), (key, value) -> value + 1));

        long activeTickets = tickets.stream().filter(this::isActive).count();
        long resolvedTickets = tickets.stream().filter(ticket ->
                ticket.getStatus() == Ticket.Status.RESUELTO || ticket.getStatus() == Ticket.Status.CERRADO).count();
        long unassignedTickets = tickets.stream().filter(ticket -> isActive(ticket) && ticket.getAssignedToId() == null).count();
        long criticalTickets = tickets.stream().filter(ticket ->
                isActive(ticket) && ticket.getPriority() == Ticket.Priority.CRITICA).count();
        long highPriorityTickets = tickets.stream().filter(ticket -> isActive(ticket)
                && (ticket.getPriority() == Ticket.Priority.ALTA || ticket.getPriority() == Ticket.Priority.CRITICA)).count();

        double resolutionRate = tickets.isEmpty() ? 0 : resolvedTickets * 100.0 / tickets.size();
        double averageResolutionHours = tickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && ticket.getResolvedAt() != null)
                .mapToLong(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        Map<UUID, String> areaNames = areas.stream()
                .collect(Collectors.toMap(Area::getId, Area::getName));
        Map<String, Long> activeByArea = tickets.stream().filter(this::isActive)
                .collect(Collectors.groupingBy(
                        ticket -> ticket.getAreaId() == null
                                ? "Sin área"
                                : areaNames.getOrDefault(ticket.getAreaId(), "Área eliminada"),
                        Collectors.counting()));
        List<Map<String, Object>> areaWorkload = new ArrayList<>();
        activeByArea.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> areaWorkload.add(Map.of("name", entry.getKey(), "count", entry.getValue())));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalTickets", tickets.size());
        metrics.put("activeTickets", activeTickets);
        metrics.put("resolvedTickets", resolvedTickets);
        metrics.put("unassignedTickets", unassignedTickets);
        metrics.put("criticalTickets", criticalTickets);
        metrics.put("highPriorityTickets", highPriorityTickets);
        metrics.put("resolutionRate", roundOneDecimal(resolutionRate));
        metrics.put("averageResolutionHours", roundOneDecimal(averageResolutionHours));
        metrics.put("totalUsers", userRepository.count());
        metrics.put("totalAreas", areas.size());
        metrics.put("ticketsByStatus", byStatus);
        metrics.put("ticketsByPriority", byPriority);
        metrics.put("areaWorkload", areaWorkload);
        return metrics;
    }

    private boolean isActive(Ticket ticket) {
        return ticket.getStatus() != Ticket.Status.RESUELTO && ticket.getStatus() != Ticket.Status.CERRADO;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
