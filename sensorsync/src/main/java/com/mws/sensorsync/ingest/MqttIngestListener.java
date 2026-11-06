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

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Assina o topico MQTT e grava as mensagens direto pelo IngestService
 * (antes a mensagem era reenviada por HTTP para a propria API).
 * Se o broker nao estiver no ar na inicializacao, tenta de novo a cada 10 s;
 * depois de conectado, o Paho reconecta sozinho.
 */
@Component
public class MqttIngestListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestListener.class);

    private final IngestService ingest;
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

    public MqttIngestListener(IngestService ingest, ObjectMapper mapper,
                              @Value("${sensorsync.mqtt.enabled:true}") boolean enabled,
                              @Value("${sensorsync.mqtt.broker:tcp://localhost:1883}") String broker,
                              @Value("${sensorsync.mqtt.username:}") String username,
                              @Value("${sensorsync.mqtt.password:}") String password,
                              @Value("${sensorsync.mqtt.topic:sensorsync}") String topic) {
        this.ingest = ingest;
        this.mapper = mapper;
        this.enabled = enabled;
        this.broker = broker;
        this.username = username;
        this.password = password;
        this.topic = topic;
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
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            JsonNode body = parse(payload);
            IngestService.Result r = ingest.ingest(null, body);
            log.debug("MQTT: {} valores gravados, {} ignorados", r.saved(), r.ignored());
        } catch (ApiException e) {
            log.warn("Mensagem MQTT rejeitada: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Mensagem MQTT invalida: {}", e.getMessage());
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
    }
}
