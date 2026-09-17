package org.oswfm.gisservice.kafka;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.oswfm.commons.model.common.NotificationRequest;
import org.oswfm.commons.model.common.RestMessageRequest.Payload;
import org.springframework.stereotype.Service;

import org.oswfm.gisservice.dto.UserCurrentPositionDTO;
import org.oswfm.kafkaserviceclient.exception.TopicAlreadyExistsException;
import org.oswfm.kafkaserviceclient.model.TopicCreateRequest;
import org.oswfm.kafkaserviceclient.service.KafkaPublisherService;
import org.oswfm.kafkaserviceclient.service.TopicManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes buffered group location updates to the {@code USER_GROUP_LOCATIONS} Kafka topic.
 *
 * <p>Consumed by notificationservice, which forwards the positions to each group member via
 * {@link org.oswfm.gisservice.dto.UserCurrentPositionDTO} pushed over WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupLocationsKafkaService {

    static final String TOPIC = "USER_GROUP_LOCATIONS";

    private final TopicManagementService topicManagementService;
    private final KafkaPublisherService kafkaPublisherService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        ensureTopicExists();
    }

    /**
     * Publishes the current set of buffered positions for a group to all of its members.
     */
    public void publish(Integer groupId, Collection<UserCurrentPositionDTO> positions, List<String> targetUserIds) {
        try {
            Payload payload = new Payload();
            payload.setTitle("Group location update");
            payload.setBody("Location update for group " + groupId);
            payload.setData(Map.of(
                    "groupId", String.valueOf(groupId),
                    "locations", objectMapper.writeValueAsString(positions)
            ));

            NotificationRequest message = new NotificationRequest();
            message.setType(TOPIC);
            message.setSource("gisservice");
            message.setTargetUserIds(targetUserIds);
            message.setPayload(payload);

            kafkaPublisherService.publishPayload(TOPIC, String.valueOf(groupId), message);
            log.info("[UserGroupLocations] Published {} position(s) for groupId={} to {} member(s)",
                    positions.size(), groupId, targetUserIds.size());
        } catch (JsonProcessingException e) {
            log.error("[UserGroupLocations] Failed to serialize positions for groupId={}: {}", groupId, e.getMessage(), e);
        }
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
}
