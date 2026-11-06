package com.mws.sensorsync.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Regras de acesso:
 * - Paginas e API exigem usuario logado (sessao via formulario, ou HTTP Basic para scripts).
 * - /user/** somente ADMIN.
 * - Ingestao de dados (/api/ingest e /data/saveList) e publica no nivel do Spring Security,
 *   mas o controller exige o token do dispositivo.
 *
 * CSRF: desativado. A sessao usa cookie SameSite=Lax (application.yml) e a API so aceita
 * JSON; com isso um site de terceiros nao consegue disparar POST/PUT/DELETE autenticados.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login.html", "/login", "/error",
                                "/img/**", "/scripts/imports/**", "/scripts/dark_mode.js", "/scripts/login.js",
                                "/vendor/**", "/styles/**",
                                "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/ingest", "/data/saveList").permitAll()
                        .requestMatchers("/user/**", "/users.html", "/scripts/users.js").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login.html?erro")
                        .permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint()))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    /**
     * Paginas HTML redirecionam para o login; chamadas de API recebem 401 sem o cabecalho
     * WWW-Authenticate (para o navegador nao abrir a janela nativa de usuario/senha).
     */
    private AuthenticationEntryPoint entryPoint() {
        AuthenticationEntryPoint page = new LoginUrlAuthenticationEntryPoint("/login.html");
        AuthenticationEntryPoint api = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return (request, response, authException) -> {
            if (isPage(request)) {
                page.commence(request, response, authException);
            } else {
                api.commence(request, response, authException);
            }
        };
    }

    private static boolean isPage(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/") || uri.endsWith(".html");
    }
}
