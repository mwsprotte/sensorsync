#include "sensor_sync.h"

// Implementação da classe HTTPConnection
void HTTPConnection::sendData(int project, int device, int dataNumber, String desc[], float data[], const char* URL) {
  String dataToSend = "[";

  for (int i = 0; i < dataNumber; i++) {
    dataToSend += "{\"projectID\": " + String(project) + ",\"sensorIndex\": " + String(device) + ",\"dataIndex\": " + String(i) + ",\"sensorDescription\": \"" + desc[i] + "\",\"data\": " + String(data[i]) + "}";

    if (i < dataNumber - 1) {
      dataToSend += ",";
    }
  }

  dataToSend += "]";
  Serial.println(dataToSend);

  httpClient.begin(espClient, URL);
  httpClient.addHeader("Content-Type", "application/json");
  httpClient.POST(dataToSend);
  String content = httpClient.getString();
  httpClient.end();
  Serial.println(content);
}

// Implementação da classe MQTTConnection
MQTTConnection::MQTTConnection()
  : client(espClient) {
  // Configurando o tamanho do buffer do MQTT Client
  client.setBufferSize(65536);  // 64 KB
}

void MQTTConnection::reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    if (client.connect("arduinoClient")) {
      Serial.println("connected");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void MQTTConnection::sendData(int project, int device, String desc[], float data[], const char* topicAPI) {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  String dataToSend = "[";

  for (int i = 0; i < sizeof(data) / sizeof(data[0]); i++) {
    dataToSend += "{'projectID': " + String(project) + ",'sensorIndex': " + String(device) + ",'dataIndex': " + String(i) + ",'sensorDescription': '" + desc[i] + "','data': " + String(data[i]) + "}";

    if (i < sizeof(data) / sizeof(data[0]) - 1) {
      dataToSend += ",";
    }
  }

  dataToSend += "]";
  Serial.println(dataToSend);
  client.publish(topicAPI, dataToSend.c_str());
}

// Função para configurar o servidor MQTT
void MQTTConnection::setServer(const char* server, uint16_t port) {

  client.setServer(server, port);
}

// Método para acessar o client (opcional)
PubSubClient& MQTTConnection::getClient() {
  return client;
}

// Função para configurar a conexão Wi-Fi (função global)
void setup_wifi(const char* wifi_ssid, const char* wifi_password) {
  delay(10);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(wifi_ssid);
  WiFi.begin(wifi_ssid, wifi_password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}
