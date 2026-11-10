package com.mws.sensorsync;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Device;
import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.services.CodeGenerator;
import com.mws.sensorsync.tls.PlainHttpConfig;
import com.mws.sensorsync.tls.TlsSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** TLS sem Spring: gerador de codigo, leitura da CA e redirecionamento da porta HTTP. */
class TlsTests {

    static final String PEM = "-----BEGIN CERTIFICATE-----\nMIIBszCCAVmgAwIBAgIUZm9v\nYmFy\n-----END CERTIFICATE-----";

    static List<CodeGenerator.GeneratedFile> generate(CodeGenerator.Protocol protocol, CodeGenerator.Endpoint ep) {
        Project p = new Project();
        p.setId(7);
        p.setName("Estufa");
        Device d = new Device();
        d.setName("Placa 1");
        Metadata m = new Metadata();
        m.setDataDesc("Temp");
        return new CodeGenerator().generate(p, List.of(m), d, CodeGenerator.Board.ESP32, protocol, ep);
    }

    @Test
    void codeWithoutTlsIsUnchanged() {
        String code = generate(CodeGenerator.Protocol.HTTP, new CodeGenerator.Endpoint("10.0.0.104", 8080, 1883)).get(0).code();
        assertTrue(code.contains("sensorSync.useHTTP(SERVIDOR, 8080);"));
        assertFalse(code.contains("useTLS"));
        assertFalse(code.contains("CA_CERT"));
    }

    @Test
    void codeWithTlsEmbedsCaAndCallsUseTls() {
        String code = generate(CodeGenerator.Protocol.MQTT, new CodeGenerator.Endpoint("10.0.0.104", 8443, 8883, PEM + "\n")).get(0).code();
        assertTrue(code.contains("ESP32 via MQTT com TLS"));
        assertTrue(code.contains("static const char CA_CERT[] = R\"PEM(\n" + PEM + "\n)PEM\";"));
        assertTrue(code.contains("sensorSync.useMQTT(SERVIDOR, 8883);\n  sensorSync.useTLS(CA_CERT);"));
    }

    @Test
    void caWithUnexpectedCharactersIsRejected() {
        CodeGenerator.Endpoint ep = new CodeGenerator.Endpoint("h", 8443, 8883, PEM + ")PEM\";");
        assertThrows(ApiException.class, () -> generate(CodeGenerator.Protocol.HTTP, ep));
    }

    @Test
    void caIsReadFromFileOnlyWhenEnabled(@TempDir Path dir) throws Exception {
        Path ca = dir.resolve("ca.crt");
        assertTrue(new TlsSettings(false, ca.toString(), "redirect", 8080, 8443).caPem().isEmpty());

        TlsSettings on = new TlsSettings(true, ca.toString(), "redirect", 8080, 8443);
        assertThrows(ApiException.class, on::caPem);          // ligado e sem arquivo: erro claro
        Files.writeString(ca, PEM + "\n");
        assertEquals(PEM + "\n", on.caPem().orElseThrow());
        Files.writeString(dir.resolve("ca.crt"), "lixo");
        Files.setLastModifiedTime(ca, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertThrows(ApiException.class, on::caPem);          // arquivo trocado e invalido
    }

    @Test
    void plainHttpRedirectsToHttpsKeepingPathAndMethod() throws Exception {
        TlsSettings tls = new TlsSettings(true, "/nao/existe", "redirect", 8080, 8443);
        PlainHttpConfig config = new PlainHttpConfig();
        var filter = config.plainHttpRedirect(tls).getFilter();

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/dashboard.html");
        get.setServerName("10.0.0.104");
        get.setQueryString("id=3");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(get, r1, new MockFilterChain());
        assertEquals(301, r1.getStatus());
        assertEquals("https://10.0.0.104:8443/dashboard.html?id=3", r1.getHeader("Location"));

        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/ingest");
        post.setServerName("10.0.0.104");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(post, r2, new MockFilterChain());
        assertEquals(308, r2.getStatus());

        // Certificado da CA e requisicoes ja seguras passam direto
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/ca.crt"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        MockHttpServletRequest secure = new MockHttpServletRequest("GET", "/");
        secure.setSecure(true);
        MockFilterChain chain2 = new MockFilterChain();
        filter.doFilter(secure, new MockHttpServletResponse(), chain2);
        assertNotNull(chain2.getRequest());

        // Modo "dispositivos" (transicao): so o envio das placas passa sem TLS
        var devices = config.plainHttpRedirect(new TlsSettings(true, "/x", "dispositivos", 8080, 8443)).getFilter();
        for (String path : new String[]{"/api/ingest", "/data/saveList"}) {
            MockFilterChain c = new MockFilterChain();
            devices.doFilter(new MockHttpServletRequest("POST", path), new MockHttpServletResponse(), c);
            assertNotNull(c.getRequest(), path);
        }
        MockHttpServletResponse page = new MockHttpServletResponse();
        devices.doFilter(new MockHttpServletRequest("GET", "/login.html"), page, new MockFilterChain());
        assertEquals(301, page.getStatus());

        // Modo "allow" (experimentos) nao redireciona
        var allow = config.plainHttpRedirect(new TlsSettings(true, "/x", "allow", 8080, 8443)).getFilter();
        MockFilterChain chain3 = new MockFilterChain();
        allow.doFilter(new MockHttpServletRequest("POST", "/api/ingest"), new MockHttpServletResponse(), chain3);
        assertNotNull(chain3.getRequest());
    }

    /** Certificado de CA real, gerado por deploy/tls/sensorsync-tls.sh (conferido com openssl x509 -fingerprint). */
    static final String REAL_CA = """
            -----BEGIN CERTIFICATE-----
            MIIByDCCAW6gAwIBAgIUf3qdwMGTQys8py8fDMJyXn3p/TEwCgYIKoZIzj0EAwIw
            MDETMBEGA1UECgwKU2Vuc29yU3luYzEZMBcGA1UEAwwQU2Vuc29yU3luYyBDQSB2
            bTAeFw0yNjExMDYxNzMxMDFaFw00NjExMDExNzMxMDFaMDAxEzARBgNVBAoMClNl
            bnNvclN5bmMxGTAXBgNVBAMMEFNlbnNvclN5bmMgQ0Egdm0wWTATBgcqhkjOPQIB
            BggqhkjOPQMBBwNCAARZXgdSZKIFOlTArXRV6QQOjANIJvjzvVRMnJ/73IEMcLyd
            o653EjI+0bfqccJOO/ieQY3rMFhk6V4hKks+bef0o2YwZDAdBgNVHQ4EFgQU1wgw
            GBbRjLdqruNJTDRwgkuG65cwHwYDVR0jBBgwFoAU1wgwGBbRjLdqruNJTDRwgkuG
            65cwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0E
            AwIDSAAwRQIgDHupf/lJFELvd0vQCCXOuthtHpccUEd36+9ajY53fx4CIQC+7hSG
            7uHwkESkNU/HW/mdikagcggtTMfb08IFhSyAIQ==
            -----END CERTIFICATE-----
            """;

    @Test
    void caInfoShowsNameFingerprintsAndValidity(@TempDir Path dir) throws Exception {
        Path ca = dir.resolve("ca.crt");
        Files.writeString(ca, REAL_CA);
        TlsSettings.CaInfo info = new TlsSettings(true, ca.toString(), "redirect", 8080, 8443).caInfo().orElseThrow();
        assertEquals("SensorSync CA vm", info.subject());
        assertEquals("37:60:BB:79:46:AC:54:CF:C2:7A:26:BC:1D:34:1A:1A:72:95:1A:9A", info.sha1());
        assertEquals("F4:50:0B:A2:5E:2A:16:F8:EC:52:40:40:F0:9C:50:81:02:FC:55:4A:15:67:6E:C1:0D:71:A6:2B:60:4F:30:E2", info.sha256());
        assertEquals("2046-11-01", info.notAfter());
        assertTrue(new TlsSettings(false, ca.toString(), "redirect", 8080, 8443).caInfo().isEmpty());
    }
}
