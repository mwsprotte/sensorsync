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

        switch (deviceType) {
            case 0:


                if (protocol == 0) {

                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                    }

                    String esp8266Code = "// CÓDIGO ESP8266 - HTTP | TEMPLATE GERADO VIA API SENSORSYNC\n" +
                            "\n" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"ESP8266WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "\n" +
                            "const char *WIFI_SSID = \"Matheus_191\";\n" +
                            "const char *WIFI_PASSWORD = \"johnnycash2023\";\n" +
                            "#define project " + p.getId() + "\n" +
                            "#define device 0  //ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n" +
                            "\n" +
                            "char *URL = \"http://192.168.100.166:8080/datapackage\";\n" +
                            "String desc[" + p.getDataNumber() + "];\n" +
                            "float data[" + p.getDataNumber() + "];\n" +
                            "\n" +
                            "void setup() {\n" +
                            "  setup_wifi(WIFI_SSID, WIFI_PASSWORD);\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "\n" +
                            "  send_HTTP_data(project, device, desc, data, URL);\n" +
                            "}\n";

                    codes.add(esp8266Code);
                } else {
                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                    }

                    String esp8266Code = "// CÓDIGO ESP8266 - MQTT | TEMPLATE GERADO VIA API SENSORSYNC\n" +
                            "\n" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"ESP8266WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"PubSubClient.h\"\n" +
                            "#include \"blink.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "\n" +
                            "#define wifi_ssid \"Matheus_191\"\n" +
                            "#define wifi_password \"johnnycash2023\"\n" +
                            "#define project " + p.getId() + "\n" +
                            "#define device 0  //ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n" +
                            "\n" +
                            "char *URL = \"http://192.168.100.166:8080/datapackage\";\n" +
                            "String desc[" + p.getDataNumber() + "];\n" +
                            "float data[" + p.getDataNumber() + "];\n" +
                            "\n" +
                            "void setup() {\n" +
                            "  Serial.begin(57600);  //when you open serial terminal, chnge 9600\n" +
                            "  client.setServer(server, 1883);\n" +
                            "  client.setCallback(callback);\n" +
                            "  setup_wifi(wifi_ssid, wifi_password);\n" +
                            "  delay(1500);\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "  \n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "\n" +
                            "  send_MQTT_data(project,device,desc,data,URL);\n" +
                            "}\n";
                    codes.add(esp8266Code);
                }

                break;


            case 1: //ESP32


                if (protocol == 0) {

                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                    }

                    String esp32Code = "// CÓDIGO ESP32 - HTTP | TEMPLATE GERADO VIA API SENSORSYNC\n" +
                            "\n" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "\n" +
                            "const char *WIFI_SSID = \"Matheus_191\";\n" +
                            "const char *WIFI_PASSWORD = \"johnnycash2023\";\n" +
                            "#define project " + p.getId() + "\n" +
                            "#define device 0  //ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n" +
                            "\n" +
                            "char *URL = \"http://192.168.100.166:8080/datapackage\";\n" +
                            "String desc[" + p.getDataNumber() + "];\n" +
                            "float data[" + p.getDataNumber() + "];\n" +
                            "\n" +
                            "void setup() {\n" +
                            "  setup_wifi(WIFI_SSID, WIFI_PASSWORD);\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "\n" +
                            "  send_HTTP_data(project, device, desc, data, URL);\n" +
                            "}\n";

                    codes.add(esp32Code);
                } else {
                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99);";
                    }

                    String esp32Code = "// CÓDIGO ESP32 - MQTT | TEMPLATE GERADO VIA API SENSORSYNC\n" +
                            "\n" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"PubSubClient.h\"\n" +
                            "#include \"blink.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "\n" +
                            "#define wifi_ssid \"Matheus_191\"\n" +
                            "#define wifi_password \"johnnycash2023\"\n" +
                            "#define project " + p.getId() + "\n" +
                            "#define device 0  //ATUALIZAR PARA O ÍNDICE DO DISPOSITIVO CASO EXISTA MAIS DE UM PROJETO\n" +
                            "\n" +
                            "char *URL = \"http://192.168.100.166:8080/datapackage\";\n" +
                            "String desc[" + p.getDataNumber() + "];\n" +
                            "float data[" + p.getDataNumber() + "];\n" +
                            "\n" +
                            "void setup() {\n" +
                            "  Serial.begin(57600);  //when you open serial terminal, chnge 9600\n" +
                            "  client.setServer(server, 1883);\n" +
                            "  client.setCallback(callback);\n" +
                            "  setup_wifi(wifi_ssid, wifi_password);\n" +
                            "  delay(1500);\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA INICIALIZAÇÃO DOS SENSORES\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "  \n" +
                            "  // **********************************************************************************************\n" +
                            "  // ESPAÇO DESTINADO PARA AQUISIÇÃO DOS DADOS" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "\n" +
                            "  // **********************************************************************************************\n" +
                            "\n" +
                            "  send_MQTT_data(project,device,desc,data,URL);\n" +
                            "}\n";
                    codes.add(esp32Code);
                }


                break;
            case 2: //ESP8266 e arduino

                String dataToSendArduino = "";
                for (int i = 0; i < p.getDataNumber(); i++) {

                    dataToSendArduino = dataToSendArduino +
                            "\ndata[" + i + "] = random(0, 99)";
                }


//                Código no arduino será o mesmo para os dois protocolos
                String arduinoCode = "" +
                        "#include \"SoftwareSerial.h\"\n" +
                        "#include \"ArduinoJson.h\"\n" +
                        "\n" +
                        "//*************************************************************************************************\n" +
                        "// DESIGNANDO UM PAR DE PINOS PARA O ENVIO DE DADOS VIA SERIAL\n" +
                        "SoftwareSerial ESP(10, 11); //(RX, TX)\n" +
                        "\n" +
                        "float data[]\n" +
                        "\n" +
                        "void setup() {\n" +
                        "  Serial.begin(9600);\n" +
                        "  ESP.begin(9600);\n" +
                        "  delay(1000);\n" +
                        "\n" +
                        "\n" +
                        "// **********************************************************************************************\n" +
                        "// Espaço destinado para inicialização dos sensores\n" +
                        "\n" +
                        "\n" +
                        "\n" +
                        "// **********************************************************************************************\n" +
                        "\n" +
                        "}\n" +
                        "\n" +
                        "\n" +
                        "void loop() {\n" +
                        "\n" +
                        "  StaticJsonBuffer<1000> jsonBuffer;\n" +
                        "  JsonObject& data = jsonBuffer.createObject();\n" +
                        "\n" +
                        "// **********************************************************************************************\n" +
                        "// Espaço destinado para aquisição dos dados a serem enviados\n" +
                        "\n" +
                        dataToSendArduino +
                        "\n" +
                        "// **********************************************************************************************\n" +
                        "\n" +
                        "  data.printTo(ESP);\n" +
                        "  jsonBuffer.clear();\n" +
                        "\n" +
                        "  // delay(10000);\n" +
                        "}";

                codes.add(arduinoCode);

                if (protocol == 0) {

                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp8266Code = "" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"ESP8266WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "#include \"blink.h\"\n" +
                            "#define project 1\n" +
                            "#define device 0 //atualiza para o índice do dispositivo caso exista mais de um para o projeto\n" +
                            "\n" +
                            "const char *WIFI_SSID = \"Rede_IoT_Matheus\";\n" +
                            "const char *WIFI_PASSWORD = \"senhasenha\";\n" +
                            "const char *URL = \"http://192.168.0.100:8080/datapackage\"\n" +
                            "String desc[2];\n" +
                            "String data[2];\n" +
                            "\n" +
                            "WiFiClient client;\n" +
                            "HTTPClient httpClient;\n" +
                            "\n" +
                            "void setup () {\n" +
                            "    Serial.begin(9600);\n" +
                            "setup_wifi(WIFI_SSID, WIFI_PASSWORD);\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "// Espaço destinado para inicialização das bibliotecas dos sensores\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "\n" +
                            "  StaticJsonBuffer<1000> jsonBuffer;\n" +
                            "  JsonObject& data = jsonBuffer.parseObject(Serial);\n" +
                            "\n" +
                            "  if (data == JsonObject::invalid()) {\n" +
                            "   jsonBuffer.clear();\n" +
                            "    return;\n" +
                            "  }\n" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "\n" +
                            "send_HTTP_data(project, 0, desc, data, URL);\n" +
                            "\n" +
                            "}";

                    codes.add(esp8266Code);
                } else {
                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp8266Code = "" +
                            "#include \"Arduino.h\"\n" +
                            "#include \"ESP8266WiFi.h\"\n" +
                            "#include \"ESP8266HTTPClient.h\"\n" +
                            "#include \"PubSubClient.h\"\n" +
                            "#include \"sensor_sync.h\"\n" +
                            "#include \"blink.h\"\n" +
                            "#define project 1\n" +
                            "#define device 0 //atualiza para o índice do dispositivo caso exista mais de um para o projeto\n" +
                            "\n" +
                            "const char *WIFI_SSID = \"Rede_IoT_Matheus\";\n" +
                            "const char *WIFI_PASSWORD = \"senhasenha\";\n" +
                            "const char *URL = \"http://192.168.0.100:8080/datapackage\"\n" +
                            "String desc[2];\n" +
                            "String data[2];\n" +
                            "\n" +
                            "WiFiClient client;\n" +
                            "HTTPClient httpClient;\n" +
                            "\n" +
                            "void setup () {\n" +
                            "    Serial.begin(57600);//when you open serial terminal, chnge 9600\n" +
                            "    client.setServer(server, 1883);\n" +
                            "    client.setCallback(callback);\n" +
                            "    Serial.begin(9600);\n" +
                            "    setup_wifi(WIFI_SSID, WIFI_PASSWORD);\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "// Espaço destinado para inicialização das bibliotecas dos sensores\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "\n" +
                            "}\n" +
                            "\n" +
                            "void loop() {\n" +
                            "\n" +
                            "  StaticJsonBuffer<1000> jsonBuffer;\n" +
                            "  JsonObject& data = jsonBuffer.parseObject(Serial);\n" +
                            "\n" +
                            "  if (data == JsonObject::invalid()) {\n" +
                            "   jsonBuffer.clear();\n" +
                            "    return;\n" +
                            "  }\n" +
                            "\n" +
                            dataToSend +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "\n" +
                            "send_MQTT_data(project, device, desc, data, URL);\n" +
                            "\n" +
                            "}";
                    codes.add(esp8266Code);
                }


                break;
            default:
        }

        return codes;
    }

//  *********************************************************************************************

}
