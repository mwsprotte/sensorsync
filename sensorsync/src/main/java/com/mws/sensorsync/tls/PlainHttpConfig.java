package com.mws.sensorsync.tls;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Com TLS ligado, a API escuta em HTTPS (server.port, 8443) e abre tambem a porta HTTP antiga
 * (8080) para nao quebrar enderecos salvos: por padrao ela so redireciona para HTTPS
 * (modo "dispositivos": aceita sem TLS apenas o envio das placas antigas, durante a transicao).
 * O certificado da CA (/ca.crt) e servido tambem em HTTP, para instalar no navegador.
 */
@Configuration
@ConditionalOnProperty(name = "sensorsync.tls.enabled", havingValue = "true")
public class PlainHttpConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> plainHttpConnector(TlsSettings tls) {
        return factory -> {
            if (tls.plainHttp() == TlsSettings.PlainHttp.OFF) {
                return;
            }
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(tls.plainHttpPort());
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> plainHttpRedirect(TlsSettings tls) {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(new RedirectFilter(tls));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    static final class RedirectFilter extends OncePerRequestFilter {

        private final TlsSettings tls;

        RedirectFilter(TlsSettings tls) {
            this.tls = tls;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String uri = request.getRequestURI();
            boolean deviceIngest = "/api/ingest".equals(uri) || "/data/saveList".equals(uri);
            if (request.isSecure() || tls.plainHttp() == TlsSettings.PlainHttp.ALLOW
                    || (tls.plainHttp() == TlsSettings.PlainHttp.DEVICES && deviceIngest)
                    || "/ca.crt".equals(uri)) {
                chain.doFilter(request, response);
                return;
            }
            StringBuilder url = new StringBuilder("https://").append(request.getServerName());
            if (tls.httpsPort() != 443) {
                url.append(':').append(tls.httpsPort());
            }
            url.append(request.getRequestURI());
            if (request.getQueryString() != null) {
                url.append('?').append(request.getQueryString());
            }
            boolean safe = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
            // 308 preserva metodo e corpo (POST de placa com codigo antigo recebe o endereco novo)
            response.setStatus(safe ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
            response.setHeader("Location", url.toString());
        }
    }
}
