package com.mws.sensorsync.tls;

import com.mws.sensorsync.exceptions.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Configuracao de TLS (perfil "tls", ver application-tls.yml) e acesso ao certificado da CA,
 * que vai embutido no codigo gerado para as placas e pode ser baixado em /ca.crt.
 */
@Component
public class TlsSettings {

    /** O que fazer com a porta HTTP sem TLS quando o TLS esta ligado. */
    public enum PlainHttp {
        /** Redireciona para HTTPS (padrao). */
        REDIRECT,
        /** Aceita HTTP puro (so para experimentos de comparacao). */
        ALLOW,
        /** Porta fechada. */
        OFF
    }

    private final boolean enabled;
    private final Path caFile;
    private final PlainHttp plainHttp;
    private final int plainHttpPort;
    private final int httpsPort;

    private volatile String caPem;
    private volatile long caModified = Long.MIN_VALUE;

    public TlsSettings(@Value("${sensorsync.tls.enabled:false}") boolean enabled,
                       @Value("${sensorsync.tls.ca-file:/etc/sensorsync/tls/ca.crt}") String caFile,
                       @Value("${sensorsync.tls.plain-http:redirect}") String plainHttp,
                       @Value("${sensorsync.tls.plain-http-port:8080}") int plainHttpPort,
                       @Value("${server.port:8080}") int httpsPort) {
        this.enabled = enabled;
        this.caFile = Path.of(caFile);
        this.plainHttp = PlainHttp.valueOf(plainHttp.trim().toUpperCase(Locale.ROOT));
        this.plainHttpPort = plainHttpPort;
        this.httpsPort = httpsPort;
    }

    public boolean enabled() {
        return enabled;
    }

    public PlainHttp plainHttp() {
        return plainHttp;
    }

    public int plainHttpPort() {
        return plainHttpPort;
    }

    public int httpsPort() {
        return httpsPort;
    }

    /**
     * Certificado da CA em PEM; vazio se o TLS estiver desligado. Relido se o arquivo mudar.
     *
     * @throws ApiException se o TLS estiver ligado e o arquivo nao puder ser lido
     */
    public Optional<String> caPem() {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            long modified = Files.getLastModifiedTime(caFile).toMillis();
            if (caPem == null || modified != caModified) {
                String pem = Files.readString(caFile, StandardCharsets.US_ASCII).strip();
                if (!pem.startsWith("-----BEGIN CERTIFICATE-----") || !pem.endsWith("-----END CERTIFICATE-----")) {
                    throw new IOException("conteudo nao e um certificado PEM");
                }
                caPem = pem + "\n";
                caModified = modified;
            }
            return Optional.of(caPem);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Certificado da CA nao encontrado em " + caFile + " (rode sensorsync-tls.service): " + e.getMessage());
        }
    }

    /** Dados para conferir a CA antes de instalar: nome, impressoes digitais e validade. */
    public record CaInfo(String subject, String sha1, String sha256, String notAfter) {
    }

    /** Descricao da CA; vazio se o TLS estiver desligado. */
    public Optional<CaInfo> caInfo() {
        return caPem().map(TlsSettings::describe);
    }

    static CaInfo describe(String pem) {
        try {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            byte[] der = cert.getEncoded();
            HexFormat hex = HexFormat.ofDelimiter(":").withUpperCase();
            String subject = cert.getSubjectX500Principal().getName();
            for (Rdn rdn : new LdapName(subject).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    subject = rdn.getValue().toString();
                }
            }
            return new CaInfo(subject,
                    hex.formatHex(MessageDigest.getInstance("SHA-1").digest(der)),
                    hex.formatHex(MessageDigest.getInstance("SHA-256").digest(der)),
                    cert.getNotAfter().toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Certificado da CA invalido: " + e.getMessage());
        }
    }
}
