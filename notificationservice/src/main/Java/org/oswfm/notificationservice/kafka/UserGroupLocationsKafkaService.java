package org.oswfm.notificationservice.kafka;

import org.oswfm.commons.model.common.NotificationRequest;
import org.springframework.stereotype.Service;

import org.oswfm.kafkaserviceclient.exception.TopicAlreadyExistsException;
import org.oswfm.kafkaserviceclient.model.TopicCreateRequest;
import org.oswfm.kafkaserviceclient.service.TopicManagementService;
import org.oswfm.kafkaserviceclient.service.TopicSubscriptionService;
import org.oswfm.notificationservice.handler.NotificationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the {@code USER_GROUP_LOCATIONS} Kafka topic for the notification service.
 *
 * <p>On startup:
 * <ol>
 *   <li>Creates the {@code USER_GROUP_LOCATIONS} topic (no-op if it already exists).</li>
 *   <li>Subscribes to the topic so incoming group-location events are forwarded to the
 *       targeted WebSocket clients.</li>
 * </ol>
 *
 * <p>Messages on this topic are {@link NotificationRequest} envelopes published by gisservice
 * once a group's buffered member positions are complete (or flushed on timeout).
 * {@code targetUserIds} already contains the group's member IDs, so the payload is forwarded
 * as-is to those users via {@link NotificationHandler#pushToUsers}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupLocationsKafkaService {

    static final String TOPIC = "USER_GROUP_LOCATIONS";
    private static final String SUBSCRIBER_ID = "notificationservice-group-locations-handler";

    private final TopicManagementService topicManagementService;
    private final TopicSubscriptionService topicSubscriptionService;
    private final NotificationHandler notificationHandler;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        ensureTopicExists();
        subscribeToTopic();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void ensureTopicExists() {
        TopicCreateRequest request = new TopicCreateRequest();
        request.setTopicName(TOPIC);
        request.setPartitions(1);
        request.setReplicationFactor((short) 1);

        try {
            topicManagementService.createTopic(request);
            log.info("[UserGroupLocations] Created topic={}", TOPIC);
        } catch (TopicAlreadyExistsException e) {
            log.info("[UserGroupLocations] Topic already exists, skipping creation: {}", TOPIC);
        }
    }

    private void subscribeToTopic() {
        topicSubscriptionService.subscribe(TOPIC, SUBSCRIBER_ID, event -> {
            log.debug("[UserGroupLocations] Received message on topic={} offset={}", event.getTopic(), event.getOffset());
            try {
                NotificationRequest message = objectMapper.readValue(event.getPayload(), NotificationRequest.class);

                if (message.getTargetUserIds() == null || message.getTargetUserIds().isEmpty()) {
                    log.warn("[UserGroupLocations] Message has no targetUserIds, skipping push");
                    return;
                }

                java.util.List<String> targetUserIds = message.getTargetUserIds();
                message.setTargetUserIds(null);
                String json = objectMapper.writeValueAsString(message);
                notificationHandler.pushToUsers(json, targetUserIds);
                log.info("[UserGroupLocations] Forwarded group location event to {} user(s)", targetUserIds.size());

            } catch (java.io.IOException e) {
                log.error("[UserGroupLocations] Failed to process message: {}", e.getMessage(), e);
            }
        });
        log.info("[UserGroupLocations] Subscribed to topic={}", TOPIC);
    }
}
