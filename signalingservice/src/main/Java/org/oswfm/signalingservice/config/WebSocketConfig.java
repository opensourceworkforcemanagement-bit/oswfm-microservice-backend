package org.oswfm.signalingservice.config;

import org.oswfm.signalingservice.handler.SignalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration("signalingWebSocketConfig")
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalHandler signalHandler;

    public WebSocketConfig(SignalHandler signalHandler) {
        this.signalHandler = signalHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalHandler, "/signal")
                .setAllowedOrigins("*");

        registry.addHandler(signalHandler, "/signal/{roomId}")
                .setAllowedOrigins("*");
    }
}
