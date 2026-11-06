package com.mws.sensorsync.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.model.Device;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.repositories.DataRepository;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.services.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final DateTimeFormatter READ_TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public record Result(int saved, int ignored) {
    }

    private final DeviceService devices;
    private final DataRepository dataRepository;
    private final ProjectRepository projects;
    private final boolean allowLegacy;

    public IngestService(DeviceService devices, DataRepository dataRepository, ProjectRepository projects,
                         @Value("${sensorsync.ingest.allow-legacy:false}") boolean allowLegacy) {
        this.devices = devices;
        this.dataRepository = dataRepository;
        this.projects = projects;
        this.allowLegacy = allowLegacy;
    }

    @Transactional
    public Result ingest(String token, JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw ApiException.badRequest("Corpo vazio");
        }
        if (token == null && body.isObject() && body.hasNonNull("token")) {
            token = body.get("token").asText();
        }

        if (token != null && !token.isBlank()) {
            Device device = devices.findByToken(token)
                    .orElseThrow(() -> ApiException.unauthorized("Token de dispositivo invalido"));
            Result r = saveForDevice(device, body);
            devices.touch(device);
            return r;
        }

        if (allowLegacy && body.isArray()) {
            return saveLegacy(body);
        }
        throw ApiException.unauthorized("Token de dispositivo ausente");
    }

    private Result saveForDevice(Device device, JsonNode body) {
        Project p = device.getProject();
        long maxVars = p.getDataNumber();
        String now = READ_TIME.format(LocalDateTime.now());
        List<Data> rows = new ArrayList<>();
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
                rows.add(row(p.getId(), device, i, v.asDouble(), now));
            }
        } else if (body.isArray()) {
            for (JsonNode item : body) {
                long idx = item.path("dataIndex").asLong(-1);
                JsonNode v = item.get("data");
                if (idx < 0 || idx >= maxVars || v == null || !v.isNumber()) {
                    ignored++;
                    continue;
                }
                rows.add(row(p.getId(), device, idx, v.asDouble(), now));
            }
        } else {
            throw ApiException.badRequest("Formato desconhecido: esperado {\"values\": [...]}");
        }
        dataRepository.saveAll(rows);
        return new Result(rows.size(), ignored);
    }

    private Result saveLegacy(JsonNode body) {
        String now = READ_TIME.format(LocalDateTime.now());
        List<Data> rows = new ArrayList<>();
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
        dataRepository.saveAll(rows);
        if (!rows.isEmpty()) {
            log.debug("Ingestao legada (sem token): {} valores", rows.size());
        }
        return new Result(rows.size(), ignored);
    }

    private static Data row(long projectId, Device device, long dataIndex, double value, String now) {
        Data d = new Data();
        d.setProjectID(projectId);
        d.setSensorIndex(device.getDeviceIndex());
        d.setDataIndex(dataIndex);
        d.setSensorDescription(truncate(device.getName()));
        d.setData(value);
        d.setReadTime(now);
        return d;
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 254 ? s.substring(0, 254) : s);
    }
}
