package com.mws.sensorsync.ingest;

import com.fasterxml.jackson.databind.JsonNode;
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
                                       @RequestBody JsonNode body) {
        return ingest.ingest(bearer(authorization), body);
    }

    static String bearer(String header) {
        if (header == null) {
            return null;
        }
        String h = header.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }
}
