package com.mws.sensorsync.tls;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Certificado publico da CA do servidor, para instalar no navegador dos PCs do laboratorio
 * (some o aviso de "conexao nao segura"). Nao e segredo: a chave da CA nunca sai do servidor.
 * A pagina certificado.html usa /ca.json para mostrar as impressoes digitais e o comando de instalacao.
 */
@RestController
public class CaController {

    private final TlsSettings tls;

    public CaController(TlsSettings tls) {
        this.tls = tls;
    }

    @GetMapping("/ca.crt")
    public ResponseEntity<String> ca() {
        return tls.caPem()
                .map(pem -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sensorsync-ca.crt\"")
                        .body(pem))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Resumo publico da CA; {"enabled": false} quando o TLS esta desligado. */
    @GetMapping("/ca.json")
    public Map<String, Object> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        tls.caInfo().ifPresentOrElse(ca -> {
            out.put("enabled", true);
            out.put("subject", ca.subject());
            out.put("sha1", ca.sha1());
            out.put("sha256", ca.sha256());
            out.put("notAfter", ca.notAfter());
        }, () -> out.put("enabled", false));
        return out;
    }
}
