package com.workflow.bpm.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    // 🛠️ CONFIGURACIÓN CRÍTICA: Esto mata el error "More than one TaskExecutor bean found"
    // Al marcarlo como @Primary, Spring sabe qué pool usar para procesar las tareas asíncronas de la política.
    @Bean(name = "taskExecutor")
    @Primary
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("bpm-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker para topics públicos y colas privadas
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefijo para mensajes enviados por clientes al servidor
        registry.setApplicationDestinationPrefixes("/app");
        // Prefijo para mensajes dirigidos a un usuario específico
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Permitir todos los orígenes en desarrollo
                .withSockJS(); // SockJS para fallback
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // OJO: Inyectas 'authInterceptor' arriba mediante Lombok, pero aquí estabas creando uno anónimo.
        // Si ya tienes un interceptor real programado en la clase 'WebSocketAuthInterceptor', 
        // lo ideal es usar: registration.interceptors(authInterceptor);
        // Dejamos tu lógica actual pero limpia para no romper tu flujo de tokens:
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 1. Extraer el token del header nativo de STOMP
                    String authHeader = accessor.getFirstNativeHeader("X-Authorization");
                    if (authHeader == null) {
                        authHeader = accessor.getFirstNativeHeader("Authorization");
                    }

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        // Tu lógica de validación aquí
                    }
                }
                return message;
            }
        });
    }
}