package com.workflow.bpm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor AuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Canales del broker hacia el cliente (broadcast y privados)
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Prefijo para mensajes del cliente al servidor
        registry.setApplicationDestinationPrefixes("/app");
        
        // Prefijo para mensajes privados por usuario
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")              // ws://localhost:8080/ws
            .setAllowedOriginPatterns("*")   // solo dev — restringir en prod
            .withSockJS();                   // fallback automático
    }
    //@Override
    //public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        //registration.interceptors(AuthInterceptor);
    //}
 
}