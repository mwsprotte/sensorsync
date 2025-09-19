package com.mws.sensorsync;

import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.services.DataService;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.services.ProjectService;
import com.mws.sensorsync.services.UserHumanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("project")

public class ProjectController {

    public enum DeviceType {
        ESP8266(0),
        ESP32(1),
        ARDUINO(2);

        private final int value;

        DeviceType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static DeviceType fromInt(int value) {
            for (DeviceType type : DeviceType.values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid device type: " + value);
        }
    }

    public enum ProtocolType {
        HTTP(0),
        MQTT(1);

        private final int value;

        ProtocolType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ProtocolType fromInt(int value) {
            for (ProtocolType type : ProtocolType.values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid protocol type: " + value);
        }
    }

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private DataService dataServices;

    @Autowired
    private UserHumanService userHumanService;


//    *********************************************************************************************
//    Endpoints do Crud básico

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Project> findall() {
        return projectService.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Project findByID(@PathVariable(value = "id") Long id) {
        return projectService.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Project save(@RequestBody Project dataPackage) {
        return projectService.save(dataPackage);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Project update(@RequestBody Project dataPackage) {
        return projectService.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
//    *********************************************************************************************

    //Endpoint para deletar todos os dados relacionados a um id específico
    @DeleteMapping(value = "/deleteAll/{id}/user/{user}/password/{password}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean deleteAll(@PathVariable(value = "id") Long projectID, @PathVariable(value = "user") String user, @PathVariable(value = "password") String password) {

        if (Objects.equals(userHumanService.getUserByLogin(user).getPassword(), password)) {
            projectService.delete(projectID);
            metadataService.deleteAllMetadata(projectID);
            dataServices.deleteAllData(projectID);
            return true;
        } else {
            return false;
        }
    }

    //  Endpoint para deletar todos os dados relacionados a um id específico
//  *********************************************************************************************
    @GetMapping(value = "/generateCode/{id}/device/{deviceType}/protocol/{protocol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> generateCode(@PathVariable(value = "id") Long projectID, @PathVariable(value = "deviceType") int deviceType, @PathVariable(value = "protocol") int protocol) {
        List<String> codes = new ArrayList<>();

        Project p = projectService.findById(projectID);
        List<Metadata> metadataList = metadataService.findByProjectId(projectID);
        String dataToSend = "";

        switch (DeviceType.fromInt(deviceType)) {
            case ESP8266:

                for (int i = 0; i < p.getDataNumber(); i++) {
                    dataToSend = dataToSend + "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                }
                StringBuilder esp8266Code = new StringBuilder();
                esp8266Code.append("// CÓDIGO ESP8266 - ").append(protocol == ProtocolType.HTTP.getValue() ? "HTTP" : "MQTT").append(" | TEMPLATE GERADO VIA API SENSORSYNC\n")
                        .append("\n")
                        .append("#include \"Arduino.h\"\n")
                        .append("#include \"ESP8266WiFi.h\"\n")
                        .append("#include \"ESP8266HTTPClient.h\"\n")
                        .append("#include \"PubSubClient.h\"\n")
                        .append("#include \"sensor_sync/sensor_sync.hpp\"\n");


                esp8266Code.append("\n")
                        .append("#define wifi_ssid \"Sistema_IoT\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define wifi_password \"entrarentrar\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define project ").append(p.getId()).append("\n")
                        .append("#define device 0  // ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n")
                        .append("#define dataNumber ").append(p.getDataNumber()).append("\n")
                        .append("#define server \"10.0.0.104\"  // Servidor MQTT\n")
                        .append("\n")
                        .append("char *URL = \"http://10.0.0.104:8080/data/saveList\";  // URL do servidor HTTP\n")
                        .append("String desc[").append(p.getDataNumber()).append("];  // Descrições dos sensores\n")
                        .append("float data[").append(p.getDataNumber()).append("];   // Dados dos sensores\n")
                        .append("\n")
                        .append("// Objetos globais para as classes HTTP e MQTT\n");

                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp8266Code.append("HTTPConnection httpConn;\n");

                } else {
                    esp8266Code.append("MQTTConnection mqttConn;\n");

                }

                esp8266Code.append("\n")
                        .append("void setup() {\n")
                        .append("  Serial.begin(9600);  // Inicializa o monitor serial com a taxa de transmissão 57600\n")
                        .append("\n")
                        .append("  // Configuração Wi-Fi usando a função global\n")
                        .append("  setup_wifi(wifi_ssid, wifi_password); \n")
                        .append("\n")
                        .append("  delay(1500);\n");

                if (protocol != ProtocolType.HTTP.getValue()) {
                    esp8266Code.append("  // Configuração do cliente MQTT\n")
                            .append("  mqttConn.setServer(server, 1883);\n");
                }

                esp8266Code.append("\n")
                        .append("  // **********************************************************************************************\n")
                        .append("  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n")
                        .append("\n")
                        .append("  // **********************************************************************************************\n")
                        .append("}\n")
                        .append("\n")
                        .append("void loop() {\n")
                        .append("  // **********************************************************************************************\n")
                        .append("  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS\n")
                        .append(dataToSend)
                        .append("\n")
                        .append("  // **********************************************************************************************\n")
                        .append("\n");

                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp8266Code.append("  // Enviar dados via HTTP\n")
                            .append("httpConn.sendData(project, device, dataNumber, desc, data, URL);\n");
                } else {
                    esp8266Code.append("  // Enviar dados via MQTT\n")
                            .append("  mqttConn.sendData(project, device, desc, data, \"sensorsync\");\n");
                }

                esp8266Code.append("\n")
                        .append("  delay(1000);  // Atraso para o próximo ciclo de aquisição de dados\n")
                        .append("}\n");

                String esp8266CodeString = esp8266Code.toString();
                codes.add(esp8266CodeString);

                break;


            case ESP32: //ESP32

                for (int i = 0; i < p.getDataNumber(); i++) {
                    dataToSend = dataToSend + "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                }

                StringBuilder esp32Code = new StringBuilder();
                esp32Code.append("// CÓDIGO ESP32 - ").append(protocol == ProtocolType.HTTP.getValue() ? "HTTP" : "MQTT").append(" | TEMPLATE GERADO VIA API SENSORSYNC\n")
                        .append("#include \"Arduino.h\"\n")
                        .append("#include \"WiFi.h\"\n")
                        .append("#include \"HTTPClient.h\"\n")
                        .append("#include \"PubSubClient.h\"\n")
                        .append("#include \"sensor_sync/sensor_sync.hpp\"  // Incluindo o arquivo de cabeçalho com as classe de integração com o sistema\n")
                        .append("\n")
                        .append("#define wifi_ssid \"Sistema_IoT\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define wifi_password \"entrarentrar\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define project ").append(p.getId()).append("\n")
                        .append("#define device 0  // ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n")
                        .append("#define dataNumber ").append(p.getDataNumber()).append("\n")
                        .append("#define server \"10.0.0.104\"  // Servidor MQTT\n")
                        .append("char *URL = \"http://10.0.0.104:8080/data/saveList\";  // URL do servidor HTTP\n")
                        .append("String desc[").append(p.getDataNumber()).append("];  // Descrições dos sensores\n")
                        .append("float data[").append(p.getDataNumber()).append("];   // Dados dos sensores\n");
                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp32Code.append("HTTPConnection httpConn;\n");
                } else {
                    esp32Code.append("MQTTConnection mqttConn;\n");
                }

                esp32Code.append("void setup() {\n")
                        .append("  Serial.begin(57600);  // Inicializa o monitor serial com a taxa de transmissão 57600\n")
                        .append("  // Configuração Wi-Fi usando a função global\n")
                        .append("  setup_wifi(wifi_ssid, wifi_password);\n")
                        .append("  delay(1500);\n");
                if (protocol == ProtocolType.MQTT.getValue()) {
                    esp32Code.append("  mqttConn.setServer(server, 1883);\n\n");
                }

                esp32Code.append("  // **********************************************************************************************\n")
                        .append("  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n\n\n")
                        .append("  // **********************************************************************************************\n")
                        .append("}\n")
                        .append("void loop() {\n")
                        .append("  // **********************************************************************************************\n")
                        .append("  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS\n")
                        .append(dataToSend)
                        .append("\n\n// **********************************************************************************************\n");

                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp32Code.append("  // Enviar os dados via HTTP\n")
                            .append("  httpConn.sendData(project, device, dataNumber, desc, data, URL);\n");
                } else {
                    esp32Code.append("  // Enviar dados via MQTT\n")
                            .append("  mqttConn.sendData(project, device, desc, data, \"sensorsync\");\n");
                }

                esp32Code.append("  delay(1000);  // Atraso para o próximo ciclo de aquisição de dados\n")
                        .append("}\n");

                String esp32CodeString = esp32Code.toString();
                codes.add(esp32CodeString);


                break;

            case ARDUINO: //ESP8266 e arduino

                String dataToSendArduino = "";
                for (int i = 0; i < p.getDataNumber(); i++) {

                    dataToSendArduino = dataToSendArduino +
                            "\ndata[" + i + "] = random(0, 99)";
                }

// Código no arduino será o mesmo para os dois protocolos
                String arduinoCode = new StringBuilder()
                        .append("// CÓDIGO Arduino | TEMPLATE GERADO VIA API SENSORSYNC\\n\"\n")
                        .append("// NOTA: Usar biblioteca Arduino Json na versão 5\\n\"\n")
                        .append("#include \"SoftwareSerial.h\"\n")
                        .append("#include \"ArduinoJson.h\"\n")
                        .append("\n")
                        .append("// DESIGNANDO UM PAR DE PINOS PARA O ENVIO DE DADOS VIA SERIAL\n")
                        .append("SoftwareSerial ESP(10, 11); //(RX, TX)\n")
                        .append("\n")
                        .append("float data[").append(p.getDataNumber()).append("]\n")
                        .append("\n")
                        .append("void setup() {\n")
                        .append("  Serial.begin(9600);\n")
                        .append("  ESP.begin(9600);\n")
                        .append("  delay(1000);\n")
                        .append("\n")
                        .append("\n")
                        .append("// **********************************************************************************************\n")
                        .append("// Espaço destinado para inicialização dos sensores\n")
                        .append("\n")
                        .append("\n")
                        .append("\n")
                        .append("// **********************************************************************************************\n")
                        .append("\n")
                        .append("}\n")
                        .append("\n")
                        .append("\n")
                        .append("void loop() {\n")
                        .append("\n")
                        .append("  StaticJsonBuffer<1000> jsonBuffer;\n")
                        .append("  JsonObject& data = jsonBuffer.createObject();\n")
                        .append("\n")
                        .append("// **********************************************************************************************\n")
                        .append("// Espaço destinado para aquisição dos dados a serem enviados\n")
                        .append("\n")
                        .append(dataToSendArduino)
                        .append("\n")
                        .append("// **********************************************************************************************\n")
                        .append("\n")
                        .append("  data.printTo(ESP);\n")
                        .append("  jsonBuffer.clear();\n")
                        .append("\n")
                        .append("  // delay(10000);\n")
                        .append("}")
                        .toString();

                codes.add(arduinoCode);


// Construção do código base para o ESP8266
                StringBuilder esp8266CodeArduino = new StringBuilder();
                esp8266CodeArduino.append("// CÓDIGO ESP8266 - ").append(protocol == ProtocolType.HTTP.getValue() ? "HTTP" : "MQTT").append(" | TEMPLATE GERADO VIA API SENSORSYNC\n")
                        .append("#include \"Arduino.h\"\n")
                        .append("#include \"ESP8266WiFi.h\"\n")
                        .append("#include \"ESP8266HTTPClient.h\"\n")
                        .append("#include \"PubSubClient.h\"\n")
                        .append("#include \"ArduinoJson.h\"\n")
                        .append("#include \"SoftwareSerial.h\"\n")
                        .append("#include \"sensor_sync/sensor_sync.hpp\"  // Incluindo o arquivo de cabeçalho com as classes de integração com o sistema\n")
                        .append("\n")
                        .append("#define wifi_ssid \"Sistema_IoT\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define wifi_password \"entrarentrar\"  // ATUALIZAR PARA AS CREDENCIAIS DA SUA REDE\n")
                        .append("#define project ").append(p.getId()).append("\n")
                        .append("#define device 0  // ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n")
                        .append("#define dataNumber ").append(p.getDataNumber()).append("\n")
                        .append("#define server \"10.0.0.104\"  // Servidor MQTT\n")
                        .append("char *URL = \"http://10.0.0.104:8080/data/saveList\";  // URL do servidor HTTP\n")
                        .append("String desc[").append(p.getDataNumber()).append("];  // Descrições dos sensores\n")
                        .append("float data[").append(p.getDataNumber()).append("];   // Dados dos sensores\n");

// Escolher a classe de conexão de acordo com o protocolo
                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp8266CodeArduino.append("HTTPConnection httpConn;\n");
                } else {
                    esp8266CodeArduino.append("MQTTConnection mqttConn;\n");
                }

                esp8266CodeArduino.append("void setup() {\n")
                        .append("  Serial.begin(57600);  // Inicializa o monitor serial com a taxa de transmissão 57600\n")
                        .append("  // Configuração Wi-Fi usando a função global\n")
                        .append("  setup_wifi(wifi_ssid, wifi_password);\n")
                        .append("  delay(1500);\n");

                if (protocol == ProtocolType.MQTT.getValue()) {
                    esp8266CodeArduino.append("  mqttConn.setServer(server, 1883);\n\n");
                }

                esp8266CodeArduino.append("  // **********************************************************************************************\n")
                        .append("  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n\n\n")
                        .append("  // **********************************************************************************************\n")
                        .append("}\n")
                        .append("void loop() {\n")
                        .append("  StaticJsonBuffer<1000> jsonBuffer;\n")
                        .append("  JsonObject& json = jsonBuffer.parseObject(Serial);\n")
                        .append("\n")
                        .append("  if (json.success()) {\n")
                        .append("    for (int i = 0; i < dataNumber; i++) {\n")
                        .append("      String key = \"data[\" + String(i) + \"]\";\n")
                        .append("      if (json.containsKey(key.c_str())) {\n")
                        .append("        data[i] = json[key.c_str()].as<float>();\n")
                        .append("      }\n")
                        .append("    }\n")
                        .append("  }\n")
                        .append("\n\n// **********************************************************************************************\n");

// Enviar os dados via protocolo escolhido
                if (protocol == ProtocolType.HTTP.getValue()) {
                    esp8266CodeArduino.append("  // Enviar os dados via HTTP\n")
                            .append("  httpConn.sendData(project, device, dataNumber, desc, data, URL);\n");
                } else {
                    esp8266CodeArduino.append("  // Enviar dados via MQTT\n")
                            .append("  mqttConn.sendData(project, device, desc, data, \"sensorsync\");\n");
                }

                esp8266CodeArduino.append("  delay(1000);  // Atraso para o próximo ciclo de aquisição de dados\n")
                        .append("}\n");

                String esp8266CodeArduinoString = esp8266CodeArduino.toString();
                codes.add(esp8266CodeArduinoString);


                break;
            default:
        }

        return codes;
    }

//  *********************************************************************************************

}
