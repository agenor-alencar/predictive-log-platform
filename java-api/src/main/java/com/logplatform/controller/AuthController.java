package com.logplatform.controller;

import com.logplatform.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsável pela Autenticação.
 * 
 * Teoria para aula:
 * - O Login é o ponto de entrada para obter o "passaporte" (Token JWT).
 * - Verificamos as credenciais (usuário/senha) e, se estiverem corretas, 
 *   assinamos um token digital que o cliente deverá enviar em todas as outras chamadas.
 * - Bearer Token: É o padrão onde o cliente envia "Authorization: Bearer <token>".
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and JWT token management")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.admin.username:admin}")
    private String adminUsername;

    @Value("${jwt.admin.password:admin123}")
    private String adminPassword;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with username/password and receive a JWT token")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        // 1. Obtém o usuário e a senha do corpo da requisição JSON
        String username = credentials.getOrDefault("username", "");
        String password = credentials.getOrDefault("password", "");

        // 2. Valida as credenciais contra os valores configurados (ex: application.properties)
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            // 3. Se autorização OK, gera um novo Token JWT assinado
            String token = jwtTokenProvider.generateToken(username);
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "username", username));
        }

        // 4. Se credenciais inválidas, retorna Status 401 (Unauthorized)
        return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials"));
    }
}
