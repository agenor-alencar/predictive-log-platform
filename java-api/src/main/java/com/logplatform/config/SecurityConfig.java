package com.logplatform.config;

import com.logplatform.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração de segurança da API utilizando Spring Security com JWT.
 * 
 * Teoria para aula:
 * - Stateless: A API não guarda "memória" de quem está logado em sessões no servidor (HttpSession).
 *   Cada requisição deve trazer um Token JWT para ser validada individualmente.
 * - JWT (JSON Web Token): Um padrão para troca segura de informações como um objeto JSON.
 * - CORS (Cross-Origin Resource Sharing): Permite que o frontend (que pode estar em outro domínio) 
 *   acesse os recursos desta API.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable) // Desabilita CSRF pois não usamos cookies (stateless)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Aplica regras de CORS
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Força modo sem sessão
                                .authorizeHttpRequests(auth -> auth
                                                // Define quais rotas são públicas (não precisam de senha/token)
                                                .requestMatchers(
                                                                "/",
                                                                "/auth/**",
                                                                "/logs/**",
                                                                "/stats/**",
                                                                "/predict/**",
                                                                "/actuator/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/api-docs/**",
                                                                "/v3/api-docs/**",
                                                                "/error")
                                                .permitAll()
                                                // Qualquer outra rota exige que o usuário esteja autenticado
                                                .anyRequest().authenticated())
                                // Insere nosso validador de Token JWT na "esteira" de filtros do Spring
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                // Em produção, aqui deveríamos listar os domínios específicos do frontend
                config.setAllowedOrigins(List.of("*"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}
