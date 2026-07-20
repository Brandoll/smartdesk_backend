package com.smartdesk.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // Map: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        String tenantId = extractParamString(query, "tenantId");
        String userId = extractParamString(query, "userId");
        String role = extractParamString(query, "role");

        if (tenantId != null && userId != null) {
            session.getAttributes().put("tenantId", tenantId);
            session.getAttributes().put("userId", userId);
            session.getAttributes().put("role", role != null ? role : "");
            
            sessions.put(session.getId(), session);
            log.info("Notification WebSocket established for user {} in tenant {}", userId, tenantId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("Notification WebSocket closed: {}", session.getId());
    }

    /**
     * Send notification to specific roles in a tenant.
     */
    public void notifyRolesInTenant(String tenantId, String[] targetRoles, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            TextMessage textMessage = new TextMessage(message);

            sessions.values().stream()
                .filter(s -> tenantId.equals(s.getAttributes().get("tenantId")))
                .filter(s -> {
                    String userRole = (String) s.getAttributes().get("role");
                    for (String role : targetRoles) {
                        if (role.equals(userRole)) return true;
                    }
                    return false;
                })
                .forEach(s -> sendMessageSafe(s, textMessage));
        } catch (Exception e) {
            log.error("Failed to serialize or send notification", e);
        }
    }

    /**
     * Send notification to a specific user.
     */
    public void notifyUser(String tenantId, String userId, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            TextMessage textMessage = new TextMessage(message);

            sessions.values().stream()
                .filter(s -> tenantId.equals(s.getAttributes().get("tenantId")))
                .filter(s -> userId.equals(s.getAttributes().get("userId")))
                .forEach(s -> sendMessageSafe(s, textMessage));
        } catch (Exception e) {
            log.error("Failed to serialize or send notification", e);
        }
    }

    private void sendMessageSafe(WebSocketSession session, TextMessage message) {
        if (session.isOpen()) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.error("Failed to send message to session {}", session.getId(), e);
            }
        }
    }

    private String extractParamString(String query, String paramName) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1 && pair[0].equals(paramName)) {
                return pair[1];
            }
        }
        return null;
    }
}
