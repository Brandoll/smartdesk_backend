package com.smartdesk.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.entity.TicketMessage;
import com.smartdesk.repository.TicketMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final TicketMessageRepository ticketMessageRepository;
    private final ObjectMapper objectMapper;
    
    // Map to keep track of sessions per ticketId
    private final Map<UUID, Map<String, WebSocketSession>> ticketSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(TicketMessageRepository ticketMessageRepository, ObjectMapper objectMapper) {
        this.ticketMessageRepository = ticketMessageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Obtenemos ticketId y tenantId de los parametros URI (ej. /ws/chat?ticketId=123&tenantId=abc)
        String query = session.getUri().getQuery();
        UUID ticketId = extractParam(query, "ticketId");
        
        if (ticketId != null) {
            ticketSessions.computeIfAbsent(ticketId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
            log.info("WebSocket connection established for ticket {}", ticketId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, String> data = objectMapper.readValue(payload, Map.class);
        
        UUID ticketId = UUID.fromString(data.get("ticketId"));
        UUID userId = UUID.fromString(data.get("userId"));
        String text = data.get("message");
        String tenantId = data.get("tenantId");

        // Set tenant context to save message correctly
        TenantContext.setCurrentTenant(tenantId);
        
        TicketMessage ticketMessage = new TicketMessage();
        ticketMessage.setTicketId(ticketId);
        ticketMessage.setUserId(userId);
        ticketMessage.setMessage(text);
        ticketMessage = ticketMessageRepository.save(ticketMessage);

        TenantContext.clear();

        broadcastMessage(ticketId, ticketMessage);
    }

    /** Broadcasts messages persisted through the REST endpoint as well. */
    public void broadcastMessage(UUID ticketId, TicketMessage ticketMessage) {
        Map<String, WebSocketSession> sessions = ticketSessions.get(ticketId);
        if (sessions != null) {
            try {
                String broadcastPayload = objectMapper.writeValueAsString(ticketMessage);
                for (WebSocketSession s : sessions.values()) {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(broadcastPayload));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to broadcast message for ticket {}", ticketId, e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ticketSessions.values().forEach(map -> map.remove(session.getId()));
        log.info("WebSocket connection closed: {}", session.getId());
    }

    private UUID extractParam(String query, String paramName) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1 && pair[0].equals(paramName)) {
                return UUID.fromString(pair[1]);
            }
        }
        return null;
    }
}
