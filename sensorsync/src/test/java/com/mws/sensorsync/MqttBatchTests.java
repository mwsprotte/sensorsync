package com.mws.sensorsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mws.sensorsync.ingest.MqttIngestListener;
import com.mws.sensorsync.repositories.DataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.mws.sensorsync.StartupTests.as;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingestao MQTT em fila + lotes: nada se perde, rejeitadas nao derrubam o lote, metricas uma por mensagem. */
@SpringBootTest
@AutoConfigureMockMvc
class MqttBatchTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    MqttIngestListener listener;

    @Autowired
    DataRepository data;

    @Test
    void burstIsWrittenInBatchesWithoutLoss() throws Exception {
        String created = mvc.perform(as(post("/project"), "admin", "adminadmin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lotes\",\"devices\":2,\"variables\":[{\"dataDesc\":\"T\"},{\"dataDesc\":\"U\"}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode devs = mapper.readTree(created).path("devices");
        String t0 = devs.get(0).path("token").asText();
        String t1 = devs.get(1).path("token").asText();
        mvc.perform(as(delete("/admin/metrics.csv"), "admin", "adminadmin")).andExpect(status().isNoContent());
        long before = data.count();

        int n = 300;
        for (int seq = 1; seq <= n; seq++) {
            String tok = seq % 2 == 0 ? t0 : t1;
            listener.deliver("{\"token\":\"" + tok + "\",\"values\":[" + seq + ",2.5],\"seq\":" + seq + "}", 0);
            if (seq == 150) {
                listener.deliver("{\"token\":\"ss_errado\",\"values\":[1],\"seq\":9999}", 1);
                listener.deliver("isto nao e json", 0);
            }
        }
        long deadline = System.currentTimeMillis() + 20_000;
        while (listener.pending() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(300); // ultimo lote em andamento
        assertEquals(0, listener.pending());
        assertEquals(before + 2L * n, data.count(), "todas as leituras gravadas");

        String csv = mvc.perform(as(get("/admin/metrics.csv"), "admin", "adminadmin")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = Arrays.asList(csv.strip().split("\\R"));
        long saved = lines.stream().filter(l -> l.startsWith("mqtt,") && l.endsWith(",saved")).count();
        long rejected = lines.stream().filter(l -> l.endsWith(",rejected_401") && l.contains(",9999,")).count();
        long invalid = lines.stream().filter(l -> l.endsWith(",invalid")).count();
        assertEquals(n, saved, csv);
        assertEquals(1, rejected, csv);
        assertEquals(1, invalid, csv);
        // chegada antes do commit em todas
        for (String l : lines.subList(1, lines.size())) {
            String[] f = l.split(",", -1);
            if (f[10].equals("saved")) {
                assertTrue(Double.parseDouble(f[6]) >= Double.parseDouble(f[5]), l);
            }
        }
    }
}
