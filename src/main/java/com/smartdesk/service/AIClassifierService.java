package com.smartdesk.service;

import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.entity.Ticket;
import com.smartdesk.model.entity.TicketHistory;
import com.smartdesk.repository.TicketHistoryRepository;
import com.smartdesk.repository.TicketRepository;
import com.smartdesk.config.websocket.NotificationWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AIClassifierService {

    private final WebClient webClient;
    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final Set<UUID> classificationsInProgress = ConcurrentHashMap.newKeySet();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.fallback-model:gemini-2.5-flash-lite}")
    private String geminiFallbackModel;

    public AIClassifierService(WebClient.Builder webClientBuilder, TicketRepository ticketRepository,
                                TicketHistoryRepository ticketHistoryRepository,
                                NotificationWebSocketHandler notificationWebSocketHandler) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com/v1beta/models").build();
        this.ticketRepository = ticketRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Async
    public void classifyTicketAsync(UUID ticketId, String tenantId) {
        if (!classificationsInProgress.add(ticketId)) return;
        TenantContext.setCurrentTenant(tenantId);
        try {
            Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket == null) return;

        String prompt = String.format(
                "Eres un clasificador de tickets de soporte técnico. Analiza el siguiente ticket y responde SOLO en formato JSON con estos campos: " +
                "'suggestedPriority' (BAJA, MEDIA, ALTA, CRITICA), 'suggestedTitle' (título resumido del problema), " +
                "'suggestedCategory' (categoría del problema), " +
                "'suggestedSolution' (una solución detallada y práctica en español para resolver el problema del usuario, en 2-4 oraciones). " +
                "No agregues explicación, solo el JSON.\n\n" +
                "Título: %s\nDescripción: %s",
                ticket.getTitle(), ticket.getDescription()
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

            String response;
            try {
                response = generateContent(geminiModel, requestBody);
            } catch (WebClientResponseException e) {
                if (geminiFallbackModel.equals(geminiModel) || e.getStatusCode().value() == 401
                        || e.getStatusCode().value() == 403) {
                    throw e;
                }
                log.warn("Gemini model {} failed with status {}; trying fallback {}",
                        geminiModel, e.getStatusCode().value(), geminiFallbackModel);
                response = generateContent(geminiFallbackModel, requestBody);
            }

            if (response == null) return;
            log.info("AI classification completed for ticket {}", ticketId);
            ticket.setAiClassified(true);

            try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        var root = om.readTree(response);
                        var textNode = root.at("/candidates/0/content/parts/0/text");
                        if (!textNode.isMissingNode()) {
                            String jsonText = textNode.asText()
                                .replaceAll("```json\\s*", "")
                                .replaceAll("```\\s*", "")
                                .trim();
                            var parsed = om.readTree(jsonText);
                            if (parsed.has("suggestedSolution")) {
                                ticket.setAiSuggestedSolution(parsed.get("suggestedSolution").asText());
                            }
                            if (parsed.has("suggestedTitle")) {
                                ticket.setAiSuggestedTitle(parsed.get("suggestedTitle").asText());
                            }
                            if (parsed.has("suggestedPriority")) {
                                try {
                                    ticket.setAiSuggestedPriority(
                                        Ticket.Priority.valueOf(parsed.get("suggestedPriority").asText()));
                                } catch (Exception ignored) {}
                            }
                        }
            } catch (Exception e) {
                log.warn("Failed to parse AI response JSON: {}", e.getMessage());
            }

            ticketRepository.save(ticket);

            TicketHistory history = TicketHistory.builder()
                    .ticketId(ticketId)
                    .eventType(TicketHistory.EventType.AI_CLASSIFIED)
                    .newValue(response)
                    .build();
            ticketHistoryRepository.save(history);

            Map<String, String> notification = Map.of(
                    "type", "AI_CLASSIFIED",
                    "ticketId", ticketId.toString(),
                    "message", "La sugerencia de IA ya está disponible"
            );
            notificationWebSocketHandler.notifyUser(
                    tenantId, ticket.getClientId().toString(), notification);
            notificationWebSocketHandler.notifyRolesInTenant(
                    tenantId,
                    new String[]{"ADMIN_TENANT", "COLABORADOR_RESOLUTOR"},
                    notification);
        } catch (Exception e) {
            log.error("AI classification error for ticket {}: {}", ticketId, e.getMessage(), e);
            String reason = aiErrorReason(e);
            notificationWebSocketHandler.notifyRolesInTenant(
                    tenantId,
                    new String[]{"ADMIN_TENANT"},
                    Map.of("type", "AI_FAILED", "ticketId", ticketId.toString(),
                            "message", "No se pudo generar la sugerencia de IA: " + reason));
        } finally {
            classificationsInProgress.remove(ticketId);
            TenantContext.clear();
        }
    }

    private String generateContent(String model, Map<String, Object> requestBody) {
        return webClient.post()
                .uri("/" + model + ":generateContent")
                .header("x-goog-api-key", geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(this::isTransientAiError)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block(Duration.ofSeconds(60));
    }

    private boolean isTransientAiError(Throwable error) {
        if (!(error instanceof WebClientResponseException responseError)) return false;
        int status = responseError.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private String aiErrorReason(Exception error) {
        if (error instanceof WebClientResponseException responseError) {
            String body = responseError.getResponseBodyAsString();
            try {
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                String message = json.at("/error/message").asText();
                if (!message.isBlank()) return "HTTP " + responseError.getStatusCode().value() + " - " + message;
            } catch (Exception ignored) {
                // Fall through to the status-only message.
            }
            return "HTTP " + responseError.getStatusCode().value();
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
