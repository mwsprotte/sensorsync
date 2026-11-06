package com.mws.sensorsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mws.sensorsync.ingest.IngestMetrics;
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

/** Log de metricas da ingestao (ligado no application.yml de teste). */
@SpringBootTest
@AutoConfigureMockMvc
class MetricsTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void ingestionIsLoggedWithSeqTimestampsAndStatus() throws Exception {
        String created = mvc.perform(as(post("/project"), "admin", "adminadmin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Metricas\",\"devices\":1,\"variables\":[{\"dataDesc\":\"T\"},{\"dataDesc\":\"U\"}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode dev = mapper.readTree(created).path("devices").get(0);
        String token = dev.path("token").asText();
        long deviceId = dev.path("device").path("id").asLong();

        mvc.perform(as(delete("/admin/metrics.csv"), "admin", "adminadmin")).andExpect(status().isNoContent());

        long t0 = System.currentTimeMillis();
        for (int seq = 1; seq <= 3; seq++) {
            mvc.perform(post("/api/ingest").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"values\":[1.5,2.5],\"seq\":" + seq + ",\"ts\":" + t0 + "}")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/ingest").header("Authorization", "Bearer ss_errado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[1],\"seq\":99}")).andExpect(status().isUnauthorized());

        String csv = mvc.perform(as(get("/admin/metrics.csv"), "admin", "adminadmin")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = Arrays.asList(csv.strip().split("\\R"));
        assertEquals(IngestMetrics.HEADER, lines.get(0));
        assertEquals(5, lines.size(), csv);

        // protocol,qos,device_id,seq,client_ts_ms,received_ms,committed_ms,server_us,values,bytes,status
        String[] first = lines.get(1).split(",", -1);
        assertEquals("http", first[0]);
        assertEquals("", first[1]);
        assertEquals(String.valueOf(deviceId), first[2]);
        assertEquals("1", first[3]);
        assertEquals(String.valueOf(t0), first[4]);
        assertTrue(Double.parseDouble(first[6]) >= Double.parseDouble(first[5]));
        assertTrue(Double.parseDouble(first[5]) >= t0 - 1, "chegada antes do envio");
        assertTrue(first[5].matches("\\d+\\.\\d{3}"), first[5]);
        assertTrue(Long.parseLong(first[7]) > 0);
        assertEquals("2", first[8]);
        assertTrue(Long.parseLong(first[9]) > 0);
        assertEquals("saved", first[10]);

        String[] rejected = lines.get(4).split(",", -1);
        assertEquals("", rejected[2]);
        assertEquals("99", rejected[3]);
        assertEquals("rejected_401", rejected[10]);

        // Usuario comum nao acessa o log
        mvc.perform(as(post("/user"), "admin", "adminadmin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"carla\",\"password\":\"senha-forte-1\"}")).andExpect(status().isOk());
        mvc.perform(as(get("/admin/metrics.csv"), "carla", "senha-forte-1")).andExpect(status().isForbidden());
    }
}
