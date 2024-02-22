unsigned long previousMillis = 0;
const long interval = 1000;

#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include "blink.h"

//Use a hotspot
#define wifi_ssid "Matheus_191"
#define wifi_password "johnnycash2023"

#define server "192.168.100.166"
#define mqtt_user ""
#define mqtt_password ""
#define topicAPI "sensorsync"

#define THREE_SECONDS 3000

unsigned long current_time;
unsigned long last_run = 0;

WiFiClient espClient;
void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
}

PubSubClient client(espClient);

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

void setup() {
  Serial.begin(57600);  //when you open serial terminal, chnge 9600
  client.setServer(server, 1883);
  client.setCallback(callback);
  setup_wifi();
  // Allow the hardware to sort itself out
  delay(1500);

  blinkSetup();
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
  //------------------------------------------
  //post after every 3 secondS ---------------------
  //figure out how to post after every few seconds (based on earlier work)
  //------------------------------------------
  current_time = millis();

  if (current_time - last_run >= THREE_SECONDS) {

    last_run = current_time;

    int sensorValue = 1231;  // read the input on analog pin 0
    String ldrValue = String(sensorValue);

    long data0 = random(0, 99);
    long data1 = random(0, 99);
    String t = "{'description':'Teste MQTT ESP','projectID':2,'nodeIndex':1,'data0':" + (String)data0 + ",'data1':" + (String)data1 + "}";
    String temp = String(t);

    ledON();
    client.publish(topicAPI, temp.c_str());
    Serial.println(t);
    Serial.println("Message published");
    ledOFF();
  }

  //  client.subscribe("sensorsync");
}

void setup_wifi() {
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