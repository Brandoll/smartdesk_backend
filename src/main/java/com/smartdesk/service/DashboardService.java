package com.smartdesk.service;

import com.smartdesk.model.entity.Ticket;
import com.smartdesk.repository.AreaRepository;
import com.smartdesk.repository.TicketRepository;
import com.smartdesk.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AreaRepository areaRepository;

    public DashboardService(TicketRepository ticketRepository, UserRepository userRepository, AreaRepository areaRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.areaRepository = areaRepository;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        long totalUsers = userRepository.count();
        long totalAreas = areaRepository.count();
        List<Ticket> allTickets = ticketRepository.findAll();

        Map<String, Long> ticketsByStatus = allTickets.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));

        Map<String, Long> ticketsByPriority = allTickets.stream()
                .collect(Collectors.groupingBy(t -> t.getPriority().name(), Collectors.counting()));

        metrics.put("totalUsers", totalUsers);
        metrics.put("totalAreas", totalAreas);
        metrics.put("totalTickets", allTickets.size());
        metrics.put("ticketsByStatus", ticketsByStatus);
        metrics.put("ticketsByPriority", ticketsByPriority);

        return metrics;
    }
}
