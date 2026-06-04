package com.workflow.bpm.auth;

import com.workflow.bpm.user.User;
import com.workflow.bpm.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/auth", "/api/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token generation")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Buscar usuario
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        // Validar credenciales
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return ResponseEntity.status(401).body(error);
        }

        // Generar token
        String token = jwtService.generateToken(user.getUsername(), user.getRole());

        // Respuesta
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    // Clase interna para el request
    static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}