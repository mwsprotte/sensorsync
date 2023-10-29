#include <Arduino.h>

#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include "blink.h"

// const char *WIFI_SSID = "Rede_IoT_Matheus";
// const char *WIFI_PASSWORD = "senhasenha";
// const char *URL = "http://192.168.0.100:8080/datapackage";
const char *WIFI_SSID = "Matheus_191";
const char *WIFI_PASSWORD = "johnnycash2023";
const char *URL = "http://192.168.100.166:8080/datapackage";

WiFiClient client;
HTTPClient httpClient;

void setup() {
  Serial.begin(9600);
  blinkSetup();
   WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("Connected");
}

// CONCATENA A STRING PARA MANDAR PARA O SERVIDOR
void loop() {
  String data = "{\"description\": \"rasp test4\",\"data0\": 3.3,\"data1\": 33.3,\"data2\": 333.3}";
  httpClient.begin(client, URL);
  httpClient.addHeader("Content-Type", "application/json");
  httpClient.POST(data);
  String content = httpClient.getString();
  httpClient.end();
  blink(250);
  Serial.println(content);
  // delay(2000);
}
