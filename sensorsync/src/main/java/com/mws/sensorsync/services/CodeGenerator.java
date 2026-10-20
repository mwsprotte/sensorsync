package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Device;
import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Gera o sketch inicial para o microcontrolador usando a biblioteca SensorSync
 * (instalavel pelo Gerenciador de Bibliotecas da IDE Arduino).
 * O token nao e conhecido pelo servidor (so o hash), entao o codigo sai com um marcador
 * que a tela substitui quando o token acabou de ser gerado.
 */
@Service
public class CodeGenerator {

    public static final String TOKEN_PLACEHOLDER = "COLE_AQUI_O_TOKEN_DO_DISPOSITIVO";

    public enum Board { ESP8266, ESP32, ARDUINO_ESP8266 }

    public enum Protocol { HTTP, MQTT }

    public record Endpoint(String host, int httpPort, int mqttPort) {
    }

    /** Um ou dois arquivos (no modo Arduino + ESP8266 sao dois sketches). */
    public record GeneratedFile(String title, String fileName, String code) {
    }

    public List<GeneratedFile> generate(Project p, List<Metadata> vars, Device device, Board board,
                                        Protocol protocol, Endpoint ep) {
        if (vars.isEmpty()) {
            throw ApiException.badRequest("Projeto sem variaveis");
        }
        return switch (board) {
            case ESP8266, ESP32 -> List.of(new GeneratedFile(
                    board == Board.ESP32 ? "ESP32" : "ESP8266",
                    "sensorsync_" + slug(p.getName()) + ".ino",
                    espStandalone(p, vars, device, board, protocol, ep)));
            case ARDUINO_ESP8266 -> List.of(
                    new GeneratedFile("Arduino (leitura dos sensores)", "arduino_" + slug(p.getName()) + ".ino", arduinoSide(p, vars)),
                    new GeneratedFile("ESP8266 (ponte Wi-Fi)", "esp8266_" + slug(p.getName()) + ".ino", espBridge(p, vars, device, protocol, ep)));
        };
    }

    private String header(Project p, Device device, String board, Protocol protocol) {
        return """
                // SensorSync | %s via %s
                // Projeto: %s (id %d) | Dispositivo: %s (indice %d)
                //
                // Requisitos (IDE Arduino > Gerenciador de Bibliotecas):
                //   - SensorSync
                //   - PubSubClient (instalada junto como dependencia)

                """.formatted(board, protocol, oneLine(p.getName()), p.getId(), oneLine(device.getName()), device.getDeviceIndex());
    }

    private static String endpointLine(Protocol protocol, Endpoint ep) {
        return protocol == Protocol.HTTP
                ? "sensorSync.useHTTP(SERVIDOR, %d);".formatted(ep.httpPort())
                : "sensorSync.useMQTT(SERVIDOR, %d);".formatted(ep.mqttPort());
    }

    private String config(Device device, Endpoint ep) {
        return """
                #include <SensorSync.h>

                // ---- Configuracao -------------------------------------------------
                const char* WIFI_SSID     = "NOME_DA_REDE";   // ATUALIZE
                const char* WIFI_PASSWORD = "SENHA_DA_REDE";  // ATUALIZE
                const char* SERVIDOR      = "%s";
                // Token do dispositivo "%s". Trate como senha: nao publique este arquivo com o token.
                const char* TOKEN         = "%s";
                const unsigned long INTERVALO_MS = 1000;      // periodo de envio
                // --------------------------------------------------------------------

                SensorSync sensorSync;
                unsigned long ultimoEnvio = 0;
                """.formatted(ep.host(), oneLine(device.getName()), TOKEN_PLACEHOLDER);
    }

    private String espStandalone(Project p, List<Metadata> vars, Device device, Board board, Protocol protocol, Endpoint ep) {
        String cfg = config(device, ep);
        String endpointLine = endpointLine(protocol, ep);
        StringBuilder reads = new StringBuilder();
        for (int i = 0; i < vars.size(); i++) {
            reads.append("    sensorSync.setValue(%d, random(0, 100));  // %s%n".formatted(i, oneLine(vars.get(i).getDataDesc())));
        }
        return header(p, device, board == Board.ESP32 ? "ESP32" : "ESP8266", protocol)
                + cfg
                + """

                void setup() {
                  Serial.begin(115200);
                  sensorSync.setToken(TOKEN);
                  %s
                  sensorSync.begin(WIFI_SSID, WIFI_PASSWORD);

                  // ---- Inicializacao dos sensores ----

                }

                void loop() {
                  sensorSync.loop();  // mantem Wi-Fi e MQTT conectados (nao bloqueia)

                  if (millis() - ultimoEnvio >= INTERVALO_MS) {
                    ultimoEnvio = millis();

                    // ---- Aquisicao: substitua random() pelas leituras reais ----
                %s
                    if (!sensorSync.send()) {
                      Serial.print("Falha no envio: ");
                      Serial.println(sensorSync.lastError());
                    }
                  }
                }
                """.formatted(endpointLine, reads.toString().stripTrailing());
    }

    private String arduinoSide(Project p, List<Metadata> vars) {
        StringBuilder reads = new StringBuilder();
        for (int i = 0; i < vars.size(); i++) {
            reads.append("  valores[%d] = random(0, 100);  // %s%n".formatted(i, oneLine(vars.get(i).getDataDesc())));
        }
        return """
                // SensorSync | Arduino (leitura dos sensores)
                // Projeto: %s (id %d)
                // Envia as leituras ao ESP8266 pela serial, uma linha por ciclo: "v0;v1;...\\n"
                // Ligacao: pino 11 (TX) do Arduino -> RX do ESP8266 (use divisor de tensao 5V -> 3,3V), GND comum.

                #include <SoftwareSerial.h>

                SoftwareSerial esp(10, 11);  // RX, TX
                const int N_VALORES = %d;
                float valores[N_VALORES];

                void setup() {
                  Serial.begin(9600);
                  esp.begin(9600);

                  // ---- Inicializacao dos sensores ----

                }

                void loop() {
                  // ---- Aquisicao: substitua random() pelas leituras reais ----
                %s
                  for (int i = 0; i < N_VALORES; i++) {
                    esp.print(valores[i], 4);
                    esp.print(i < N_VALORES - 1 ? ';' : '\\n');
                  }
                  delay(1000);
                }
                """.formatted(oneLine(p.getName()), p.getId(), vars.size(), reads.toString().stripTrailing());
    }

    private String espBridge(Project p, List<Metadata> vars, Device device, Protocol protocol, Endpoint ep) {
        String cfg = config(device, ep);
        String endpointLine = endpointLine(protocol, ep);
        return header(p, device, "ESP8266 (ponte para Arduino)", protocol)
                + cfg
                + """
                const int N_VALORES = %d;
                String linha;

                void setup() {
                  Serial.begin(9600);  // RX do ESP8266 ligado ao TX do Arduino
                  sensorSync.setToken(TOKEN);
                  %s
                  sensorSync.begin(WIFI_SSID, WIFI_PASSWORD);
                }

                void loop() {
                  sensorSync.loop();

                  while (Serial.available()) {
                    char c = Serial.read();
                    if (c == '\\n') {
                      // Linha completa "v0;v1;...": separa e envia
                      int inicio = 0;
                      for (int i = 0; i < N_VALORES; i++) {
                        int fim = linha.indexOf(';', inicio);
                        String campo = (fim < 0) ? linha.substring(inicio) : linha.substring(inicio, fim);
                        if (campo.length() > 0) sensorSync.setValue(i, campo.toFloat());
                        if (fim < 0) break;
                        inicio = fim + 1;
                      }
                      sensorSync.send();
                      linha = "";
                    } else if (c != '\\r' && linha.length() < 256) {
                      linha += c;
                    }
                  }
                }
                """.formatted(vars.size(), endpointLine);
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n\"\\\\]", " ");
    }

    private static String slug(String s) {
        String r = java.text.Normalizer.normalize(s == null ? "projeto" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "").toLowerCase();
        return r.isEmpty() ? "projeto" : r;
    }
}
