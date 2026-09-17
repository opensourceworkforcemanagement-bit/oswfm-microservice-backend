package org.oswfm.kafkaservice.service;

import org.oswfm.kafkaservice.handler.TopicWebSocketHandler;
import org.oswfm.kafkaserviceclient.model.KafkaMessageEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class KafkaBridgeService {

    @Value("${spring.kafka.bootstrap-servers:localhost:9095}")
    private String bootstrapServers;

    private final TopicWebSocketHandler topicWebSocketHandler;
    private final ObjectMapper objectMapper;

    /** topicName → running listener container */
    private final Map<String, ConcurrentMessageListenerContainer<String, String>> activeContainers =
            new ConcurrentHashMap<>();

    public KafkaBridgeService(@Lazy TopicWebSocketHandler topicWebSocketHandler,
                               ObjectMapper objectMapper) {
        this.topicWebSocketHandler = topicWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    public synchronized void ensureConsumerActive(String topicName) {
        if (activeContainers.containsKey(topicName)) {
            log.debug("[Bridge] Consumer already active for topic={}", topicName);
            return;
        }

        log.info("[Bridge] Starting consumer for topic={}", topicName);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkaservice-ws-" + topicName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ContainerProperties containerProps = new ContainerProperties(topicName);
        containerProps.setMessageListener(
                (MessageListener<String, String>) record -> onRecord(topicName, record));

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(
                        new DefaultKafkaConsumerFactory<>(props),
                        containerProps);
        container.setConcurrency(1);
        container.start();

        activeContainers.put(topicName, container);
        log.info("[Bridge] Consumer started for topic={}", topicName);
    }

    public synchronized void releaseConsumerIfIdle(String topicName) {
        if (topicWebSocketHandler.subscriberCount(topicName) > 0) {
            log.debug("[Bridge] Subscribers still present for topic={}, keeping consumer", topicName);
            return;
        }

        ConcurrentMessageListenerContainer<String, String> container = activeContainers.remove(topicName);
        if (container != null) {
            container.stop();
            log.info("[Bridge] Stopped idle consumer for topic={}", topicName);
        }
    }

    private void onRecord(String topicName, ConsumerRecord<String, String> record) {
        KafkaMessageEvent event = KafkaMessageEvent.builder()
                .topic(topicName)
                .partition(record.partition())
                .offset(record.offset())
                .key(record.key())
                .payload(record.value())
                .timestamp(record.timestamp())
                .build();

        try {
            String json = objectMapper.writeValueAsString(event);
            topicWebSocketHandler.push(topicName, json);
        } catch (JsonProcessingException e) {
            log.error("[Bridge] Failed to serialize event for topic={}: {}", topicName, e.getMessage());
        }
    }
}
