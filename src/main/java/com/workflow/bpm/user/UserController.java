package com.workflow.bpm.user;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @NonNull User user) {
        System.out.println("USER RECIBIDO: " + user);

        // Validar si el usuario ya existe
        if (repo.existsByUsername(user.getUsername())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Username already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Encriptar password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Asignar rol por defecto si no viene
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("USER");
        }

        User savedUser = repo.save(user);
        savedUser.setPassword(null); // No devolver el password en la respuesta
        
        return ResponseEntity.ok(savedUser);
    }

    @GetMapping  
    public List<User> getAll() {
        List<User> users = repo.findAll();
        //users.forEach(user -> user.setPassword(null)); // Ocultar passwords
        return users;
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable String id) {
        return repo.findById(id)
                .map(user -> {
                    user.setPassword(null);
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}