package com.mws.sensorsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa as regras de acesso de ponta a ponta (H2 em memoria, MQTT desligado). */
@SpringBootTest
@AutoConfigureMockMvc
class StartupTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String user, String pass) {
        String basic = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return b.header("Authorization", "Basic " + basic);
    }

    JsonNode json(MockHttpServletRequestBuilder b) throws Exception {
        String body = mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    @Test
    void accessRulesAndDeviceTokens() throws Exception {
        // Sem login: API 401, pagina redireciona para o login
        mvc.perform(get("/project")).andExpect(status().isUnauthorized());
        mvc.perform(get("/dashboard.html")).andExpect(status().is3xxRedirection());

        // Admin inicial criado pela migracao cria dois usuarios comuns
        for (String u : new String[]{"ana", "bruno"}) {
            mvc.perform(as(post("/user"), "admin", "adminadmin").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"" + u + "\",\"password\":\"senha-forte-1\"}")).andExpect(status().isOk());
        }
        // Lista de usuarios nao expoe senha e e restrita a admin
        String users = mvc.perform(as(get("/user"), "admin", "adminadmin")).andReturn().getResponse().getContentAsString();
        assertEquals(false, users.contains("password"));
        mvc.perform(as(get("/user"), "ana", "senha-forte-1")).andExpect(status().isForbidden());

        // Ana cria um projeto com 1 dispositivo; recebe o token uma unica vez
        JsonNode created = json(as(post("/project"), "ana", "senha-forte-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Estufa\",\"devices\":1,\"variables\":[{\"dataDesc\":\"Temp\",\"useCard\":true,\"useChart\":true}]}"));
        long anaProject = created.path("project").path("id").asLong();
        String token = created.path("devices").get(0).path("token").asText();

        // Bruno cria outro projeto
        long brunoProject = json(as(post("/project"), "bruno", "senha-forte-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Solar\",\"devices\":1,\"variables\":[{\"dataDesc\":\"V\"}]}")).path("project").path("id").asLong();

        // Isolamento entre usuarios
        mvc.perform(as(get("/project/" + brunoProject), "ana", "senha-forte-1")).andExpect(status().isNotFound());
        mvc.perform(as(delete("/project/" + brunoProject), "ana", "senha-forte-1")).andExpect(status().isNotFound());
        assertEquals(1, json(as(get("/project"), "ana", "senha-forte-1")).size());
        assertEquals(2, json(as(get("/project"), "admin", "adminadmin")).size());

        // Ingestao: sem token e token invalido sao recusados
        mvc.perform(post("/api/ingest").contentType(MediaType.APPLICATION_JSON).content("{\"values\":[1]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ingest").header("Authorization", "Bearer ss_invalido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[1]}")).andExpect(status().isUnauthorized());

        // Token valido grava no projeto do dispositivo, mesmo que o payload antigo aponte para outro projeto
        mvc.perform(post("/api/ingest").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[21.5, 99]}")).andExpect(status().isOk());
        mvc.perform(post("/data/saveList").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"projectID\":" + brunoProject + ",\"sensorIndex\":0,\"dataIndex\":0,\"data\":666}]")).andExpect(status().isOk());
        assertEquals(0, json(as(get("/data/project/" + brunoProject + "/length/10"), "bruno", "senha-forte-1")).size());
        JsonNode anaData = json(as(get("/data/project/" + anaProject + "/length/10"), "ana", "senha-forte-1"));
        assertEquals(2, anaData.size()); // 21.5 e 666 (o 99 foi ignorado: projeto tem 1 variavel)

        // Regenerar token invalida o anterior
        long deviceId = created.path("devices").get(0).path("device").path("id").asLong();
        String newToken = json(as(post("/project/" + anaProject + "/devices/" + deviceId + "/token"), "ana", "senha-forte-1"))
                .path("token").asText();
        mvc.perform(post("/api/ingest").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[1]}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ingest").header("Authorization", "Bearer " + newToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[1]}")).andExpect(status().isOk());
    }
}
