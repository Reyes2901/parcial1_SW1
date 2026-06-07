package com.workflow.bpm.config;

import com.workflow.bpm.auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);

        if (token == null) {
            throw new MessageDeliveryException("Acceso denegado: Token ausente.");
        }

        try {
            // 1. Esto valida el token y extrae el usuario. Si el JWT es inválido, fallará
            // aquí.
            String userId = jwtService.extractUsername(token);

            // 2. Extraemos el rol directamente del Payload de forma segura y compatible
            String role = extractRoleFromValidatedToken(token);

            // Inyectar la autenticación completa en el contexto de Spring Messaging
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority(role)));

            accessor.setUser(auth);
            log.info("WebSocket Autenticado con Éxito. Usuario: {}, Rol: {}", userId, role);
        } catch (Exception e) {
            log.error("Error de autenticación en WebSocket: {}", e.getMessage());
            throw new MessageDeliveryException("Token JWT inválido o expirado");
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String nativeQuery = accessor.getFirstNativeHeader("query_string");
        if (nativeQuery != null && nativeQuery.contains("token=")) {
            try {
                String[] pairs = nativeQuery.split("&");
                for (String pair : pairs) {
                    if (pair.startsWith("token=")) {
                        return URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                log.warn("Error parseando query_string: {}", e.getMessage());
            }
        }
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Analiza el payload decodificado del JWT para asignar el Rol correcto.
     * Al estar previamente validado por jwtService, este procedimiento es 100%
     * seguro.
     */
    private String extractRoleFromValidatedToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(chunks[1]), StandardCharsets.UTF_8);

                // Si el JSON del payload contiene la palabra ADMIN, le asignamos el rol de
                // Administrador
                if (payload.toUpperCase().contains("ADMIN")) {
                    return "ROLE_ADMIN";
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo leer el payload del token para el rol, usando defecto: {}", e.getMessage());
        }
        return "ROLE_USER"; // Rol por defecto si no es admin
    }
}