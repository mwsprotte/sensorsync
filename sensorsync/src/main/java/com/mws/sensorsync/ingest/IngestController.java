package com.mws.sensorsync.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Ponto de entrada HTTP dos dispositivos. Autenticacao: Authorization: Bearer <token>. */
@RestController
public class IngestController {

    private final IngestService ingest;

    public IngestController(IngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping(value = {"/api/ingest", "/data/saveList"},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public IngestService.Result ingest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody JsonNode body, HttpServletRequest request) {
        // O instante de chegada e marcado ja com o corpo lido (o Spring le e converte o JSON antes daqui)
        IngestMetrics.Received rx = IngestMetrics.Received.now(request.isSecure() ? "https" : "http", null,
                Math.max(0, request.getContentLengthLong()));
        return ingest.ingest(bearer(authorization), body, rx);
    }

    static String bearer(String header) {
        if (header == null) {
            return null;
        }
        String h = header.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }
}
