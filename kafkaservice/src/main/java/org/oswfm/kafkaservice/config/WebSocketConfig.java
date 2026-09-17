package org.oswfm.kafkaservice.config;

import org.oswfm.kafkaservice.handler.TopicWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration("kafkaWebSocketConfig")
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TopicWebSocketHandler topicWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(topicWebSocketHandler, "/ws/topics/*")
                .setAllowedOrigins("*");
    }
}
