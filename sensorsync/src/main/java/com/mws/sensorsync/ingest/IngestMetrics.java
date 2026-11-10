package com.mws.sensorsync.ingest;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Log de metricas da ingestao, uma linha CSV por mensagem recebida (experimentos de latencia e perda).
 * Desligado por padrao (SENSORSYNC_METRICS=true liga). A escrita e feita por uma thread separada,
 * fora do caminho medido: record() so coloca a linha numa fila.
 *
 * Colunas:
 *   protocol      http | https | mqtt
 *   qos           QoS da mensagem MQTT (vazio no HTTP)
 *   device_id     dispositivo do token (vazio se recusado)
 *   seq, client_ts_ms  campos opcionais "seq" e "ts" enviados pelo cliente
 *   received_ms   relogio do servidor (epoch ms, 3 casas) quando a mensagem chegou na API
 *   committed_ms  received_ms + server_us (instante do commit no banco)
 *   server_us     chegada ate o commit, relogio monotonico (microssegundos)
 *
 * O instante de chegada vem de Instant.now() (no Windows, System.currentTimeMillis() anda em degraus de
 * ate ~15 ms e faria a latencia ponta a ponta sair negativa); o do commit e derivado do relogio monotonico.
 *   values        valores gravados
 *   bytes         tamanho do corpo/payload
 *   status        saved | rejected_401 | rejected_400 | invalid | error
 */
@Component
public class IngestMetrics {

    public static final String HEADER =
            "protocol,qos,device_id,seq,client_ts_ms,received_ms,committed_ms,server_us,values,bytes,status";

    private static final Logger log = LoggerFactory.getLogger(IngestMetrics.class);

    /** Momento em que a mensagem chegou: relogio de parede (para comparar com o cliente) e monotonico. */
    public record Received(String protocol, Integer qos, long epochMicros, long nanos, long bytes) {
        public static Received now(String protocol, Integer qos, long bytes) {
            Instant now = Instant.now();
            return new Received(protocol, qos, now.getEpochSecond() * 1_000_000L + now.getNano() / 1000, System.nanoTime(), bytes);
        }
    }

    private final boolean enabled;
    private final Path file;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(200_000);
    private final Object fileLock = new Object();
    /** Linhas recebidas e ainda nao gravadas no arquivo (fila + lote em escrita). */
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean running = true;
    private Thread writer;

    public IngestMetrics(@Value("${sensorsync.metrics.enabled:false}") boolean enabled,
                         @Value("${sensorsync.metrics.file:metrics/ingest-metrics.csv}") String file) {
        this.enabled = enabled;
        this.file = Path.of(file).toAbsolutePath();
        if (enabled) {
            writer = new Thread(this::writeLoop, "ingest-metrics");
            writer.setDaemon(true);
            writer.start();
            log.info("Metricas de ingestao LIGADAS: {}", this.file);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public Path file() {
        return file;
    }

    /** Registra uma mensagem; nao bloqueia (se a fila encher, a linha e descartada e contada no log). */
    public void record(Received rx, Long deviceId, Long seq, Long clientTs, int values, String status) {
        if (!enabled) {
            return;
        }
        long serverUs = (System.nanoTime() - rx.nanos()) / 1000;
        String line = rx.protocol() + ',' + str(rx.qos()) + ',' + str(deviceId) + ',' + str(seq) + ',' + str(clientTs) + ','
                + millis(rx.epochMicros()) + ',' + millis(rx.epochMicros() + serverUs) + ',' + serverUs + ','
                + values + ',' + rx.bytes() + ',' + status;
        pending.incrementAndGet();
        if (!queue.offer(line)) {
            pending.decrementAndGet();
            log.warn("Fila de metricas cheia: linha descartada");
        }
    }

    /** Apaga o arquivo (inicio de uma rodada de experimento). */
    public void reset() throws IOException {
        synchronized (fileLock) {
            pending.addAndGet(-queue.size());
            queue.clear();
            Files.deleteIfExists(file);
        }
    }

    /** Espera todas as linhas pendentes serem gravadas (ate 5 s) antes de o arquivo ser lido. */
    public void drain() {
        long limit = System.currentTimeMillis() + 5000;
        while (pending.get() > 0 && System.currentTimeMillis() < limit) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void writeLoop() {
        List<String> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                String first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, 5000);
                synchronized (fileLock) {
                    Files.createDirectories(file.getParent());
                    boolean isNew = !Files.exists(file);
                    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        if (isNew) {
                            w.write(HEADER);
                            w.newLine();
                        }
                        for (String l : batch) {
                            w.write(l);
                            w.newLine();
                        }
                    }
                }
                pending.addAndGet(-batch.size());
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                log.warn("Falha ao gravar metricas em {}: {}", file, e.getMessage());
                pending.addAndGet(-batch.size());
                batch.clear();
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (writer != null) {
            try {
                writer.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Microssegundos desde a epoca como milissegundos com 3 casas ("1730912345678.123"). */
    static String millis(long epochMicros) {
        long ms = epochMicros / 1000;
        int frac = (int) (epochMicros % 1000);
        return ms + "." + (frac < 10 ? "00" : frac < 100 ? "0" : "") + frac;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
