package com.mws.sensorsync.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mws.sensorsync.exceptions.ApiException;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assina o topico MQTT e grava as mensagens direto pelo IngestService
 * (antes a mensagem era reenviada por HTTP para a propria API).
 * Se o broker nao estiver no ar na inicializacao, tenta de novo a cada 10 s;
 * depois de conectado, o Paho reconecta sozinho.
 *
 * Gravacao assincrona em lotes (padrao, sensorsync.mqtt.async=true): o callback do Paho so le a mensagem e
 * a coloca numa fila; uma thread grava o que acumulou na fila numa unica transacao (ate batch-size mensagens).
 * Motivo: o Paho entrega as mensagens uma por vez, e gravar dentro do callback limita a vazao a 1 / (tempo de
 * um commit), cerca de 23 msg/s num Raspberry Pi 3 com cartao SD (o resto se acumulava no broker e, com
 * QoS 0, era descartado). Com fila, sob carga varias mensagens dividem o mesmo commit; com pouca carga o
 * lote tem 1 mensagem e nada muda. A marca de chegada (metricas) e tirada no callback, entao a espera na fila
 * entra no tempo "no servidor".
 * Troca: com QoS 1 o PUBACK sai quando a mensagem entra na fila, nao depois do commit (antes era depois); se
 * a API cair, o que estiver na fila se perde. sensorsync.mqtt.async=false volta ao comportamento anterior.
 */
@Component
public class MqttIngestListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestListener.class);

    private final IngestService ingest;
    private final IngestMetrics metrics;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String broker;
    private final String username;
    private final String password;
    private final String topic;
    private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mqtt-connect");
        t.setDaemon(true);
        return t;
    });
    private MqttClient client;

    private final boolean async;
    private final int batchSize;
    private final BlockingQueue<IngestService.Item> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running = true;
    private Thread writer;

    public MqttIngestListener(IngestService ingest, IngestMetrics metrics, ObjectMapper mapper,
                              @Value("${sensorsync.mqtt.enabled:true}") boolean enabled,
                              @Value("${sensorsync.mqtt.broker:tcp://localhost:1883}") String broker,
                              @Value("${sensorsync.mqtt.username:}") String username,
                              @Value("${sensorsync.mqtt.password:}") String password,
                              @Value("${sensorsync.mqtt.topic:sensorsync}") String topic,
                              @Value("${sensorsync.mqtt.async:true}") boolean async,
                              @Value("${sensorsync.mqtt.batch-size:100}") int batchSize,
                              @Value("${sensorsync.mqtt.queue-size:10000}") int queueSize) {
        this.ingest = ingest;
        this.metrics = metrics;
        this.mapper = mapper;
        this.enabled = enabled;
        this.broker = broker;
        this.username = username;
        this.password = password;
        this.topic = topic;
        this.async = async;
        this.batchSize = Math.max(1, batchSize);
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    }

    @PostConstruct
    void startWriter() {
        if (!async) {
            return;
        }
        writer = new Thread(this::writeLoop, "mqtt-ingest");
        writer.setDaemon(true);
        writer.start();
    }

    /** Grava o que estiver na fila, em lotes de ate batchSize mensagens, ate a API parar (e esvazia a fila no fim). */
    private void writeLoop() {
        List<IngestService.Item> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                IngestService.Item first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                ingest.ingestBatch(batch);
                log.debug("MQTT: lote de {} mensagens gravado", batch.size());
            } catch (InterruptedException e) {
                if (!running) {
                    queue.drainTo(batch);
                    ingest.ingestBatch(batch);
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao gravar lote MQTT: {}", e.getMessage());
            } finally {
                batch.clear();
            }
        }
    }

    /** Mensagens na fila esperando gravacao (para testes e diagnostico). */
    public int pending() {
        return queue.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("MQTT desativado (sensorsync.mqtt.enabled=false)");
            return;
        }
        retry.execute(this::connect);
    }

    private void connect() {
        try {
            if (client == null) {
                client = new MqttClient(broker, "sensorsync-api-" + UUID.randomUUID().toString().substring(0, 8), new MemoryPersistence());
                client.setCallback(this);
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            if (!username.isEmpty()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
        } catch (MqttException e) {
            log.warn("Nao foi possivel conectar ao broker MQTT {} ({}); nova tentativa em 10 s", broker, e.getMessage());
            retry.schedule(this::connect, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // Assina fora da thread de callback do Paho (chamada bloqueante dentro do callback pode travar)
        retry.execute(() -> {
            try {
                client.subscribe(topic, 1);
                log.info("MQTT {} em {}, topico '{}'", reconnect ? "reconectado" : "conectado", serverURI, topic);
            } catch (MqttException e) {
                log.error("Falha ao assinar o topico {}", topic, e);
            }
        });
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexao MQTT perdida: {} (reconectando automaticamente)", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void messageArrived(String t, MqttMessage message) {
        IngestMetrics.Received rx = IngestMetrics.Received.now("mqtt", message.getQos(), message.getPayload().length);
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        JsonNode body;
        try {
            body = parse(payload);
        } catch (Exception e) {
            metrics.record(rx, null, null, null, 0, "invalid");
            log.warn("Mensagem MQTT invalida: {}", e.getMessage());
            return;
        }
        if (async) {
            if (!queue.offer(new IngestService.Item(null, body, rx))) {
                metrics.record(rx, null, null, null, 0, "dropped_queue_full");
                if (dropped.incrementAndGet() % 100 == 1) {
                    log.warn("Fila de gravacao MQTT cheia ({} mensagens): {} descartadas ate agora",
                            queue.size(), dropped.get());
                }
            }
            return;
        }
        try {
            IngestService.Result r = ingest.ingest(null, body, rx);
            log.debug("MQTT: {} valores gravados, {} ignorados", r.saved(), r.ignored());
        } catch (ApiException e) {
            log.warn("Mensagem MQTT rejeitada: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Falha ao gravar mensagem MQTT: {}", e.getMessage());
        }
    }

    /** Aceita JSON padrao e, por compatibilidade com a biblioteca antiga, JSON com aspas simples. */
    private JsonNode parse(String payload) throws Exception {
        try {
            return mapper.readTree(payload);
        } catch (Exception e) {
            return mapper.readTree(payload.replace('\'', '"'));
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void stop() {
        retry.shutdownNow();
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ignored) {
        }
        // para de receber antes e entao grava o que sobrou na fila
        running = false;
        if (writer != null) {
            try {
                writer.join(15_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (!queue.isEmpty()) {
                log.warn("API parando com {} mensagens MQTT nao gravadas", queue.size());
            }
        }
    }

    /** Para testes: entrega uma mensagem como se viesse do broker. */
    public void deliver(String payload, int qos) {
        MqttMessage m = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        m.setQos(qos);
        messageArrived(topic, m);
    }
}
