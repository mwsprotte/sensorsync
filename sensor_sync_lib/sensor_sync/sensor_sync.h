#ifndef SENSOR_SYNC_H
#define SENSOR_SYNC_H

// Declaração da classe HTTPConnection
class HTTPConnection {
  private:
    WiFiClient espClient;
    HTTPClient httpClient;

  public:
    void sendData(int project, int device, int dataNumber, String desc[], float data[], const char* URL);
};

// Declaração da classe MQTTConnection
class MQTTConnection {
  private:
    WiFiClient espClient;
    PubSubClient client;

  public:
    MQTTConnection();
    void reconnect();
    void sendData(int project, int device, String desc[], float data[], const char* topicAPI);

    // Métodos públicos para configurar o servidor e o callback
    void setServer(const char* server, uint16_t port);
    PubSubClient& getClient();  // Método para acessar o client (opcional)
};

// Função para configurar a conexão Wi-Fi
void setup_wifi(const char* wifi_ssid, const char* wifi_password);

#endif // SENSOR_SYNC_H
