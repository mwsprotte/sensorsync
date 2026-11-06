package com.mws.sensorsync.ingest;

import com.mws.sensorsync.exceptions.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Log de metricas da ingestao (somente ADMIN; precisa de SENSORSYNC_METRICS=true).
 * GET baixa o CSV; DELETE apaga para comecar uma rodada nova. Usado por bench/loadgen.py.
 */
@RestController
@RequestMapping("/admin/metrics.csv")
public class MetricsController {

    private final IngestMetrics metrics;

    public MetricsController(IngestMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public ResponseEntity<Resource> download() {
        requireEnabled();
        metrics.drain();
        if (!Files.exists(metrics.file())) {
            return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(new org.springframework.core.io.ByteArrayResource(
                            (IngestMetrics.HEADER + "\n").getBytes(StandardCharsets.UTF_8)));
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ingest-metrics.csv\"")
                .body(new FileSystemResource(metrics.file()));
    }

    @DeleteMapping
    public ResponseEntity<Void> reset() throws IOException {
        requireEnabled();
        metrics.drain();
        metrics.reset();
        return ResponseEntity.noContent().build();
    }

    private void requireEnabled() {
        if (!metrics.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Metricas desligadas (use SENSORSYNC_METRICS=true)");
        }
    }
}
