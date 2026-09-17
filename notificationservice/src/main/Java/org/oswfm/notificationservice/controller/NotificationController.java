package org.oswfm.notificationservice.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.oswfm.kafkaserviceclient.service.KafkaPublisherService;
import org.oswfm.notificationservice.handler.NotificationHandler;
import org.oswfm.commons.model.common.NotificationRequest;
import org.oswfm.commons.model.common.RestMessageRequest.Payload;
import org.oswfm.commons.model.user.Token;
import org.oswfm.notificationservice.service.FcmService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationHandler notificationHandler;
    private final KafkaPublisherService kafkaPublisherService;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody NotificationRequest request) throws Exception {

        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        if (request.getTimestamp() == 0) {
            request.setTimestamp(Instant.now().toEpochMilli());
        }

        if (Token.isBearerToken(authorizationHeader)) {
            String userId = Token.extractUserId(Token.getJwt(authorizationHeader));
            if (userId != null) {
                request.setRequestingUserId(userId);
            }
        }

        String json = objectMapper.writeValueAsString(request);

        if (request.getTargetUserIds() == null || request.getTargetUserIds().isEmpty()) {
            notificationHandler.push(json);
            fcmService.sendToAll(
                request.getType(),
                request.getPayload() != null ? request.getPayload().getTitle() : "",
                request.getPayload() != null ? request.getPayload().getBody() : "",
                null
            );
            log.info("[Notifications] Broadcasted to all sessions");
            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "id", request.getId(),
                "type", request.getType(),
                "target", "all"
            ));
        } else {
            notificationHandler.pushToUsers(json, request.getTargetUserIds());
            log.info("[Notifications] Sent to {} user(s)", request.getTargetUserIds().size());
            return ResponseEntity.ok(Map.of(
                "status", "sent",
                "id", request.getId(),
                "type", request.getType(),
                "target", request.getTargetUserIds()
            ));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(
            @RequestParam(defaultValue = "Test Alert") String title,
            @RequestParam(defaultValue = "Notification from backend") String body,
            @RequestParam(defaultValue = "ALERT") String type) throws Exception {

        NotificationRequest req = new NotificationRequest();
        req.setId(UUID.randomUUID().toString());
        req.setType(type);
        req.setSequence(Instant.now().toEpochMilli() / 1000);
        req.setTimestamp(Instant.now().toEpochMilli());

        Payload payload = new Payload();
        payload.setTitle(title);
        payload.setBody(body);
        req.setPayload(payload);

        String json = objectMapper.writeValueAsString(req);
        log.info("[Notifications] REST test push: {}", json);
        notificationHandler.push(json);
        fcmService.sendToAll(type, title, body, null);

        return ResponseEntity.ok(Map.of(
            "status", "sent",
            "id", req.getId(),
            "type", type,
            "title", title
        ));
    }
}
