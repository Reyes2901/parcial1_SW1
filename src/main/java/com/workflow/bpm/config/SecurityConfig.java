package com.workflow.bpm.config;

import com.workflow.bpm.auth.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos
                        .requestMatchers(
                                "/api/auth/**",
                                "/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/ws/**", // WebSocket endpoint y SockJS
                                "/test/**",
                                "/topics/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users", "/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/policies", "/api/policies/active",
                                "/api/policies/*/start-form")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/departments", "/api/departments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/process-types").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users", "/users", "/users/**").authenticated()
                        // 🔄 MODIFICADO: Añadido /** para evitar el 403 en sub-rutas o barras
                        // inclinadas de departamentos
                        // 🔄 AÑADIDO: Permitir que CUALQUIER usuario logueado (ADMIN, EMPLOYEE, etc.)
                        // pueda listar usuarios/departamentos
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Orígenes permitidos (puedes agregar más)
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:4200",
                "http://localhost:8080", // para pruebas locales
                "http://frontend-734852757342.us-central1.run.app",
                "http://localhost:*", // cualquier puerto local (útil para Flutter)
                "http://192.168.0.9:4200"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}