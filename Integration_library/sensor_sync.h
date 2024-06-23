#include "HardwareSerial.h"
#include "core_esp8266_features.h"
WiFiClient espClient;
HTTPClient httpClient;
PubSubClient client(espClient);

const long interval = 1000;
#define topicAPI "sensorsync"
#define server "10.0.0.104"
#define mqtt_user ""
#define mqtt_password ""


void setup_wifi(const char* wifi_ssid, const char* wifi_password) {
  delay(10);
  // We start by connecting to a WiFi network
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

void send_HTTP_data(int project, int device, int dataNUmber, String desc[], float data[], char* URL) {

  String dataToSend = "[";

  // for (int i = 0; i < sizeof(data); i++) {
  for (int i = 0; i < dataNUmber; i++) {
    dataToSend = dataToSend + "{\"projectID\": " + project + ",\"sensorIndex\": " + device + ",\"dataIndex\": " + i + ",\"sensorDescription\": \"" + desc[i] + "\",\"data\": " + data[i] + "}";

    if (i < dataNUmber - 1) {
      dataToSend = dataToSend + ",";
    }
  }

  dataToSend = dataToSend + "]";

  Serial.println(dataToSend);

  // String data = "{\"description\": \"rasp test4\",\"data0\": 3.3,\"data1\": 33.3,\"data2\": 333.3}";
  httpClient.begin(espClient, URL);
  httpClient.addHeader("Content-Type", "application/json");
  httpClient.POST(dataToSend);
  String content = httpClient.getString();
  httpClient.end();
  Serial.println(content);
}



// ********************************************************************************************************************************************
// funções usadas para integração mqtt

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Attempt to connect
    if (client.connect("arduinoClient")) {
      Serial.println("connected");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
}


void send_MQTT_data(int project, int device, int dataNumber, String desc[], float data[]) {


  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  // current_time = millis();

  // Todo: verificar se é necessário esse tempo para envio
  // if (current_time - last_run >= 3000) {
  //   last_run = current_time;

    String dataToSend = "[";

    for (int i = 0; i < dataNumber; i++) {
      dataToSend = dataToSend + "{'projectID': " + project + ",'sensorIndex': " + device + ",'dataIndex': " + i + ",'sensorDescription': '" + desc[i] + "','data': " + data[i] + "}";

      if (i < dataNumber - 1) {
        dataToSend = dataToSend + ",";
      }
    }

    dataToSend = dataToSend + "]";

    Serial.println(dataToSend);

    String aux_data = String(dataToSend);

    client.publish(topicAPI, aux_data.c_str());
  // }

  // client.subscribe("sensorsync");
}