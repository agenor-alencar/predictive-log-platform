package com.logplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filtro de Autenticação JWT.
 * 
 * Teoria para aula:
 * - Filtro (Filter): Funciona como um "porteiro" da API. Toda requisição passa por aqui 
 *   antes de chegar ao Controller. Ele verifica se o usuário enviou um token válido.
 * - Authorization Header: O padrão é extrair o token que vem após a palavra "Bearer ".
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 1. Extrai o token JWT do cabeçalho 'Authorization' da requisição
        String token = extractToken(request);

        // 2. Valida o token (checa assinatura e expiração)
        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 3. Obtém o nome do usuário contido no payload do token
            String username = jwtTokenProvider.getUsernameFromToken(token);
            
            // 4. Cria um objeto de autenticação do Spring Security
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            
            // 5. Define a autenticação no contexto de segurança global da thread atual
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 6. Continua o processamento da requisição chamando o próximo filtro na cadeia
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
