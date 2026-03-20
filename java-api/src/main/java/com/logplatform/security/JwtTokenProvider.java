package com.logplatform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Provedor de Tokens JWT (Geração e Validação).
 * 
 * Teoria para aula:
 * - JWT (JSON Web Token): Um padrão de mercado para passar informações de segurança 
 *   assinadas digitalmente.
 * - Assinatura (HMAC-SHA256): Garante que ninguém alterou o token no caminho entre 
 *   o cliente e o servidor. Se um hacker mudar o nome do usuário no token, a 
 *   assinatura ficará inválida.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:plip-secret-key-change-in-production-min-32-chars!!}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        // 1. Define o momento da criação e a data de expiração
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        // 2. Constrói o token JWT assinado
        return Jwts.builder()
                .subject(username) // Define o "dono" do token no campo 'sub'
                .issuedAt(now)     // Define a data de emissão
                .expiration(expiry) // Define a data de expiração (TTL)
                .signWith(key)     // Assina o token usando a chave secreta SHA-256
                .compact();        // Gera a string final codificada em Base64
    }

    public String getUsernameFromToken(String token) {
        // Abre o token (parse), valida a assinatura e extrai o campo 'subject'
        return Jwts.parser()
                .verifyWith(key) // Usa a chave para validar que o token não foi alterado
                .build()
                .parseSignedClaims(token) // Lê as informações (claims)
                .getPayload()
                .getSubject(); // Retorna o nome do usuário
    }

    public boolean validateToken(String token) {
        try {
            // Tenta ler o token; se a assinatura estiver errada ou expirado, lançará exceção
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true; // Token é íntegro e válido
        } catch (JwtException | IllegalArgumentException e) {
            // Caso falhe (expirado, assinatura inválida, etc.), logamos o erro
            log.warn("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }
}
