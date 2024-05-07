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
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp8266Code = "#include <Arduino.h>\n#include <ESP8266WiFi.h>\n#include <ESP8266HTTPClient.h>\n#include \"sensor_sync.h\"\n#include\"blink.h\"\n" +
                            "\nconst char *WIFI_SSID = \"Rede_IoT_Matheus\";\nconst char *WIFI_PASSWORD = \"senhasenha\";\nconst char *URL = \"http://192.168.0.100:8080/datapackage\"" +
                            "\nString desc[2];\nString data[2];\n" +
                            "\nWiFiClient client;\nHTTPClient httpClient;\n" +
                            "\n\nvoid setup () {\nsetup_wifi(WIFI_SSID, WIFI_PASSWORD);\n}\n" +
                            "\nvoid loop() {\n" +
                            dataToSend + "\n" +
                            "\nsend_HTTP_data(1, 0, desc, data, URL);\n}";

                    codes.add(esp8266Code);
                } else {
                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp8266Code = "#include <Arduino.h>\n#include <ESP8266WiFi.h>#\ninclude <ESP8266HTTPClient.h>#\ninclude <PubSubClient.h>#\ninclude \"blink.h\"#\ninclude \"sensor_sync.h\"\n" +
                            "\n#define wifi_ssid \"Matheus_191\"\n#define wifi_password \"johnnycash2023\"\nchar *URL = \"http://192.168.100.166:8080/datapackage\"\n" +
                            "\nString desc[2];\nfloat data[2];\n" +
                            "\nvoid setup() {\nSerial.begin(57600);//when you open serial terminal, chnge 9600\nclient.setServer(server, 1883);\nclient.setCallback(callback);\nsetup_wifi(wifi_ssid, wifi_password);\ndelay(1500)\n" +
                            "\n// **********************************************************************************************\n" +
                            "// Espaço destinado para inicialização dos sensores\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "}\n" +
                            "\nvoid loop() {\n" +
                            "// **********************************************************************************************\n" +
                            "// Espaço destinado para aquisisção dos dados a serem enviados\n" +
                            dataToSend +
                            "\n\n// **********************************************************************************************\n" +
                            "\n" +
                            "send_MQTT_data(1,0,desc,data,URL);\n" +
                            "}\n";
                    codes.add(esp8266Code);
                }

                break;


            case 1: //ESP32


                if (protocol == 0) {

                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp32Code = "#include <Arduino.h>\n#include <WiFi.h.h>\n#include <ESP8266HTTPClient.h>\n#include \"sensor_sync.h\"\n#include\"blink.h\"\n" +
                            "\nconst char *WIFI_SSID = \"Rede_IoT_Matheus\";\nconst char *WIFI_PASSWORD = \"senhasenha\";\nconst char *URL = \"http://192.168.0.100:8080/datapackage\"" +
                            "\nString desc[2];\nString data[2];\n" +
                            "\nWiFiClient client;\nHTTPClient httpClient;\n" +
                            "\n\nvoid setup () {\nsetup_wifi(WIFI_SSID, WIFI_PASSWORD);\n}\n" +
                            "\nvoid loop() {\n" +
                            dataToSend + "\n" +
                            "\nsend_HTTP_data(1, 0, desc, data, URL);\n}";

                    codes.add(esp32Code);
                } else {
                    String dataToSend = "";
                    for (int i = 0; i < p.getDataNumber(); i++) {

                        dataToSend = dataToSend +
                                "\ndesc[" + i + "] = \"Teste " + i + "\";\ndata[" + i + "] = random(0, 99)";
                    }

                    String esp32Code = "#include <Arduino.h>\n#include <WiFi.h.h>#\ninclude <ESP8266HTTPClient.h>#\ninclude <PubSubClient.h>#\ninclude \"blink.h\"#\ninclude \"sensor_sync.h\"\n" +
                            "\n#define wifi_ssid \"Matheus_191\"\n#define wifi_password \"johnnycash2023\"\nchar *URL = \"http://192.168.100.166:8080/datapackage\"\n" +
                            "\nString desc[2];\nfloat data[2];\n" +
                            "\nvoid setup() {\nSerial.begin(57600);//when you open serial terminal, chnge 9600\nclient.setServer(server, 1883);\nclient.setCallback(callback);\nsetup_wifi(wifi_ssid, wifi_password);\ndelay(1500)\n" +
                            "\n// **********************************************************************************************\n" +
                            "// Espaço destinado para inicialização dos sensores\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "// **********************************************************************************************\n" +
                            "}\n" +
                            "\nvoid loop() {\n" +
                            "// **********************************************************************************************\n" +
                            "// Espaço destinado para aquisisção dos dados a serem enviados\n" +
                            dataToSend +
                            "\n\n// **********************************************************************************************\n" +
                            "\n" +
                            "send_MQTT_data(1,0,desc,data,URL);\n" +
                            "}\n";
                    codes.add(esp32Code);
                }


                break;
            case 2: //ESP8266 e arduino


                break;
            default:
        }

        return codes;
    }

//  *********************************************************************************************

}
