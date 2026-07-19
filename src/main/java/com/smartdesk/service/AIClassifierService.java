package com.smartdesk.service;

import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.entity.Ticket;
import com.smartdesk.model.entity.TicketHistory;
import com.smartdesk.repository.TicketHistoryRepository;
import com.smartdesk.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AIClassifierService {

    private final WebClient webClient;
    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public AIClassifierService(WebClient.Builder webClientBuilder, TicketRepository ticketRepository,
                                TicketHistoryRepository ticketHistoryRepository) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com/v1beta/models").build();
        this.ticketRepository = ticketRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
    }

    @Async
    public void classifyTicketAsync(UUID ticketId, String tenantId) {
        TenantContext.setCurrentTenant(tenantId);

        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) return;

        String prompt = String.format(
                "Eres un clasificador de tickets de soporte. Analiza el siguiente ticket y responde SOLO en formato JSON con estos campos: " +
                "'suggestedPriority' (BAJA, MEDIA, ALTA, CRITICA), 'suggestedTitle' (título resumido del problema), 'suggestedCategory' (categoría del problema). " +
                "No agregues explicación, solo el JSON.\n\n" +
                "Título: %s\nDescripción: %s",
                ticket.getTitle(), ticket.getDescription()
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        webClient.post()
                .uri("/gemini-1.5-pro:generateContent?key=" + geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> {
                    log.info("AI classification for ticket {}: {}", ticketId, response);
                    // Mark as classified
                    ticket.setAiClassified(true);
                    ticketRepository.save(ticket);

                    // Record in history
                    TicketHistory history = TicketHistory.builder()
                            .ticketId(ticketId)
                            .eventType(TicketHistory.EventType.AI_CLASSIFIED)
                            .newValue(response)
                            .build();
                    ticketHistoryRepository.save(history);
                })
                .doOnError(e -> log.error("AI classification error: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
