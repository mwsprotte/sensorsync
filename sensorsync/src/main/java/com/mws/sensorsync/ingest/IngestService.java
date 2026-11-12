package com.mws.sensorsync.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.repositories.DataRepository;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.services.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Recebe leituras de dispositivos (via HTTP ou MQTT) e grava no banco.
 *
 * Formato atual (biblioteca SensorSync 1.x):
 *   {"values": [23.5, 61.2, null]}             -- HTTP, token no cabecalho Authorization: Bearer ...
 *   {"token": "ss_...", "values": [23.5, 61.2]} -- MQTT
 * A posicao no vetor e o indice da variavel; null = sem leitura nessa variavel.
 *
 * Formato antigo (biblioteca 0.x): lista de objetos Data com projectID/sensorIndex/dataIndex.
 * Com token, projectID e sensorIndex do payload sao ignorados e substituidos pelos do dispositivo.
 * Sem token, so e aceito se sensorsync.ingest.allow-legacy=true.
 *
 * Campos opcionais para experimentos: "seq" (sequencia do dispositivo) e "ts" (hora de envio, epoch ms).
 * Nao sao gravados nos dados; vao para o log de metricas (IngestMetrics), registrado depois do commit.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final DateTimeFormatter READ_TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public record Result(int saved, int ignored) {
    }

    private record Outcome(Result result, Long deviceId) {
    }

    /** Linhas por INSERT (cada mensagem gera uma linha por variavel). */
    private static final int INSERT_CHUNK = 200;
    private static final String INSERT_PREFIX =
            "INSERT INTO data (sensor_description, projectid, sensor_index, data_index, data, read_time) VALUES ";

    private final DeviceService devices;
    private final DataRepository dataRepository;
    private final JdbcTemplate jdbc;
    private final ProjectRepository projects;
    private final boolean allowLegacy;
    private final IngestMetrics metrics;
    private final TransactionTemplate tx;

    public IngestService(DeviceService devices, DataRepository dataRepository, ProjectRepository projects,
                         IngestMetrics metrics, PlatformTransactionManager txManager, JdbcTemplate jdbc,
                         @Value("${sensorsync.ingest.allow-legacy:false}") boolean allowLegacy) {
        this.devices = devices;
        this.dataRepository = dataRepository;
        this.jdbc = jdbc;
        this.projects = projects;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(txManager);
        this.allowLegacy = allowLegacy;
    }

    /** Uma mensagem para gravacao em lote (MQTT): token (ou null), corpo ja lido e a marca de chegada. */
    public record Item(String token, JsonNode body, IngestMetrics.Received rx) {
    }

    /**
     * Grava varias mensagens numa unica transacao (um so commit, ou seja, uma so escrita duravel no disco).
     * Mensagens rejeitadas (token invalido, formato) nao atrapalham as outras. Se o lote inteiro falhar
     * (erro de banco), cada mensagem e gravada de novo sozinha, para isolar a que causou o erro.
     * O log de metricas e registrado depois do commit, uma linha por mensagem, como no caminho normal.
     */
    public void ingestBatch(List<Item> items) {
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            ingestQuietly(items.get(0));
            return;
        }
        List<Object> outcomes;
        try {
            outcomes = tx.execute(status -> {
                List<Object> r = new ArrayList<>(items.size());
                List<Data> rows = new ArrayList<>();
                for (Item it : items) {
                    int mark = rows.size();
                    try {
                        r.add(doIngest(it.token(), it.body(), rows));
                    } catch (ApiException e) {
                        rows.subList(mark, rows.size()).clear();
                        r.add(e);
                    }
                }
                insertRows(rows);
                return r;
            });
        } catch (RuntimeException e) {
            log.warn("Lote de {} mensagens falhou ({}); gravando uma a uma", items.size(), e.getMessage());
            items.forEach(this::ingestQuietly);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            Long seq = optionalLong(it.body(), "seq");
            Long ts = optionalLong(it.body(), "ts");
            if (outcomes.get(i) instanceof Outcome o) {
                metrics.record(it.rx(), o.deviceId(), seq, ts, o.result().saved(), "saved");
            } else {
                ApiException e = (ApiException) outcomes.get(i);
                metrics.record(it.rx(), null, seq, ts, 0, "rejected_" + e.getStatus().value());
                log.warn("Mensagem rejeitada: {}", e.getMessage());
            }
        }
    }

    private void ingestQuietly(Item it) {
        try {
            ingest(it.token(), it.body(), it.rx());
        } catch (ApiException e) {
            log.warn("Mensagem rejeitada: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Falha ao gravar mensagem: {}", e.getMessage());
        }
    }

    public Result ingest(String token, JsonNode body) {
        return ingest(token, body, IngestMetrics.Received.now("internal", null, 0));
    }

    /**
     * Grava as leituras numa transacao e, depois do commit, registra a mensagem no log de metricas.
     * Erros (token invalido, formato) tambem sao registrados, com o status correspondente.
     */
    public Result ingest(String token, JsonNode body, IngestMetrics.Received rx) {
        Long seq = optionalLong(body, "seq");
        Long ts = optionalLong(body, "ts");
        try {
            Outcome o = tx.execute(status -> {
                List<Data> rows = new ArrayList<>();
                Outcome out = doIngest(token, body, rows);
                insertRows(rows);
                return out;
            });
            metrics.record(rx, o.deviceId(), seq, ts, o.result().saved(), "saved");
            return o.result();
        } catch (ApiException e) {
            metrics.record(rx, null, seq, ts, 0, "rejected_" + e.getStatus().value());
            throw e;
        } catch (RuntimeException e) {
            metrics.record(rx, null, seq, ts, 0, "error");
            throw e;
        }
    }

    /** Valida a mensagem e acrescenta as linhas a gravar em {@code rows} (a gravacao e feita por quem chamou). */
    private Outcome doIngest(String token, JsonNode body, List<Data> rows) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw ApiException.badRequest("Corpo vazio");
        }
        if (token == null && body.isObject() && body.hasNonNull("token")) {
            token = body.get("token").asText();
        }

        if (token != null && !token.isBlank()) {
            DeviceService.TokenInfo device = devices.lookupToken(token)
                    .orElseThrow(() -> ApiException.unauthorized("Token de dispositivo invalido"));
            Result r = saveForDevice(device, body, rows);
            devices.touch(device);
            return new Outcome(r, device.deviceId);
        }

        if (allowLegacy && body.isArray()) {
            return new Outcome(saveLegacy(body, rows), null);
        }
        throw ApiException.unauthorized("Token de dispositivo ausente");
    }

    private Result saveForDevice(DeviceService.TokenInfo device, JsonNode body, List<Data> rows) {
        long maxVars = device.dataNumber;
        String now = READ_TIME.format(LocalDateTime.now());
        int start = rows.size();
        int ignored = 0;

        if (body.isObject() && body.has("values")) {
            JsonNode values = body.get("values");
            if (!values.isArray()) {
                throw ApiException.badRequest("'values' deve ser um vetor");
            }
            for (int i = 0; i < values.size(); i++) {
                JsonNode v = values.get(i);
                if (v == null || v.isNull()) {
                    continue;
                }
                if (!v.isNumber() || i >= maxVars) {
                    ignored++;
                    continue;
                }
                rows.add(row(device, i, v.asDouble(), now));
            }
        } else if (body.isArray()) {
            for (JsonNode item : body) {
                long idx = item.path("dataIndex").asLong(-1);
                JsonNode v = item.get("data");
                if (idx < 0 || idx >= maxVars || v == null || !v.isNumber()) {
                    ignored++;
                    continue;
                }
                rows.add(row(device, idx, v.asDouble(), now));
            }
        } else {
            throw ApiException.badRequest("Formato desconhecido: esperado {\"values\": [...]}");
        }
        return new Result(rows.size() - start, ignored);
    }

    private Result saveLegacy(JsonNode body, List<Data> rows) {
        String now = READ_TIME.format(LocalDateTime.now());
        int start = rows.size();
        int ignored = 0;
        for (JsonNode item : body) {
            long projectId = item.path("projectID").asLong(-1);
            JsonNode v = item.get("data");
            Project p = projectId < 0 ? null : projects.findById(projectId).orElse(null);
            long idx = item.path("dataIndex").asLong(-1);
            if (p == null || v == null || !v.isNumber() || idx < 0 || idx >= p.getDataNumber()) {
                ignored++;
                continue;
            }
            Data d = new Data();
            d.setProjectID(projectId);
            d.setSensorIndex(item.path("sensorIndex").asLong(0));
            d.setDataIndex(idx);
            d.setSensorDescription(truncate(item.path("sensorDescription").asText(null)));
            d.setData(v.asDouble());
            d.setReadTime(now);
            rows.add(d);
        }
        if (rows.size() > start) {
            log.debug("Ingestao legada (sem token): {} valores", rows.size() - start);
        }
        return new Result(rows.size() - start, ignored);
    }

    /**
     * Grava as linhas com INSERTs de varias linhas cada (no lugar de um INSERT por valor, que e o que o JPA faz
     * com id automatico). No Raspberry Pi 3 o custo por comando SQL limitava a vazao a ~44 msg/s.
     */
    private void insertRows(List<Data> rows) {
        for (int from = 0; from < rows.size(); from += INSERT_CHUNK) {
            List<Data> chunk = rows.subList(from, Math.min(rows.size(), from + INSERT_CHUNK));
            StringBuilder sql = new StringBuilder(INSERT_PREFIX.length() + chunk.size() * 16).append(INSERT_PREFIX);
            Object[] args = new Object[chunk.size() * 6];
            int a = 0;
            for (int i = 0; i < chunk.size(); i++) {
                Data d = chunk.get(i);
                sql.append(i == 0 ? "(?,?,?,?,?,?)" : ",(?,?,?,?,?,?)");
                args[a++] = d.getSensorDescription();
                args[a++] = d.getProjectID();
                args[a++] = d.getSensorIndex();
                args[a++] = d.getDataIndex();
                args[a++] = d.getData();
                args[a++] = d.getReadTime();
            }
            jdbc.update(sql.toString(), args);
        }
    }

    private static Data row(DeviceService.TokenInfo device, long dataIndex, double value, String now) {
        Data d = new Data();
        d.setProjectID(device.projectId);
        d.setSensorIndex(device.deviceIndex);
        d.setDataIndex(dataIndex);
        d.setSensorDescription(truncate(device.name));
        d.setData(value);
        d.setReadTime(now);
        return d;
    }

    private static Long optionalLong(JsonNode body, String field) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode v = body.get(field);
        return v != null && v.canConvertToLong() ? v.asLong() : null;
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 254 ? s.substring(0, 254) : s);
    }
}
