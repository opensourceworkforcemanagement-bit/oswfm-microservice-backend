package org.oswfm.notificationservice.handler;

import org.oswfm.kafkaserviceclient.service.KafkaPublisherService;
import org.oswfm.commons.model.user.Token;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for realtime notification delivery.
 *
 * <p>Clients connect to {@code /notifications?token=...&since=<seq>}.
 * Any connected client can broadcast a {@link org.oswfm.commons.model.common.NotificationRequest}
 * JSON payload by sending it as a text frame — the server echoes it to all other sessions.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final KafkaPublisherService kafkaPublisherService;

    /** All active notification sessions. */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /** sessionId → extracted userId or token (for logging). */
    private final Map<String, String> sessionMeta = new ConcurrentHashMap<>();

    /** userId → active WebSocket sessions for that user (one user may have multiple tabs/devices). */
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[Notifications] Client connected: sessionId={} uri={}", session.getId(), session.getUri());
        String token = extractParam(session, "token");
        String userId = Token.extractUserId(token);
        String since = extractParam(session, "since");

        if (userId == null) {
            log.warn("[Notifications] Rejected unauthenticated connection: sessionId={}", session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.error("[Notifications] Failed to close unauthenticated session: {}", e.getMessage());
            }
            return;
        }

        sessions.add(session);
        sessionMeta.put(session.getId(), token);
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);

        log.info("[Notifications] Client connected: sessionId={} userId={} since={}",
                session.getId(), userId, since);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        sessionMeta.remove(session.getId());

        String userId = Token.extractUserId(extractParam(session, "token"));
        if (userId != null) {
            Set<WebSocketSession> set = userSessions.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) userSessions.remove(userId);
            }
        }

        log.info("[Notifications] Client disconnected: sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[Notifications] Transport error: sessionId={}", session.getId(), exception);
        sessions.remove(session);

        String userId = Token.extractUserId(extractParam(session, "token"));
        if (userId != null) {
            Set<WebSocketSession> set = userSessions.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) userSessions.remove(userId);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession sender, TextMessage message) {
        String payload = message.getPayload();
        log.debug("[Notifications] Received from {}: {}", sender.getId(), payload);

        JsonNode tree;
        try {
            tree = objectMapper.readTree(payload);
        } catch (IOException e) {
            log.warn("[Notifications] Invalid JSON from {}: {}", sender.getId(), e.getMessage());
            return;
        }

        JsonNode typeNode = tree.get("type");
        if (typeNode != null && !typeNode.asText().isBlank()) {
            kafkaPublisherService.publishPayload(typeNode.asText(), payload);
            log.info("[Notifications] Published to Kafka topic={} from session={}", typeNode.asText(), sender.getId());
        }

        broadcast(payload);
    }

    public void push(String eventJson) {
        log.info("[Notifications] Pushing event to {} session(s): {}", sessions.size(), eventJson);
        broadcast(eventJson);
    }

    public void pushToUsers(String eventJson, Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        int sent = 0;

        for (String userId : userIds) {
            Set<WebSocketSession> targets = userSessions.get(userId);
            if (targets == null || targets.isEmpty()) {
                log.debug("[Notifications] pushToUsers: no active session for userId={}", userId);
                continue;
            }

            TextMessage msg;
            try {
                JsonNode tree = objectMapper.readTree(eventJson);
                com.fasterxml.jackson.databind.node.ArrayNode targetUserIds = objectMapper.createArrayNode();
                targetUserIds.add(userId);
                ((com.fasterxml.jackson.databind.node.ObjectNode) tree).set("targetUserIds", targetUserIds);
                msg = new TextMessage(objectMapper.writeValueAsString(tree));
            } catch (IOException e) {
                log.warn("[Notifications] pushToUsers: failed to inject userId into payload for userId={}: {}", userId, e.getMessage());
                msg = new TextMessage(eventJson);
            }

            for (WebSocketSession s : targets) {
                if (s.isOpen()) {
                    try {
                        synchronized (s) {
                            s.sendMessage(msg);
                        }
                        sent++;
                    } catch (IOException e) {
                        log.error("[Notifications] pushToUsers: failed to send to userId={} sessionId={}: {}",
                                userId, s.getId(), e.getMessage());
                    }
                }
            }
        }

        log.info("[Notifications] pushToUsers: delivered to {}/{} session(s) for {} user(s): {}",
                sent, userIds.size(), userIds.size(), eventJson);
    }

    private void broadcast(String json) {
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    synchronized (s) {
                        s.sendMessage(msg);
                    }
                } catch (IOException e) {
                    log.error("[Notifications] Failed to send to {}: {}", s.getId(), e.getMessage());
                }
            }
        }
    }

    private String extractParam(WebSocketSession session, String name) {
        if (session.getUri() == null) return null;
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }
}
