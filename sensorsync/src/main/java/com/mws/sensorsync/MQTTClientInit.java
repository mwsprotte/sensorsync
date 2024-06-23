package com.mws.sensorsync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.services.DataService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * Essa classe faz a API virar um subscribe do tópico sensorsync
 * <p>
 * Exemplo de requisicao: mosquitto_pub -t sensorsync -m "{'description':'Simulacao','data0':0.25,'data1':4.58,'data2':999.9}"
 * <p>
 * Exemplo de requisicao completa: mosquitto_pub -t sensorsync -m "{'description':'Teste','projectID':1,'nodeIndex':1,'data0':4.2,'data1':4.2,'data2':4.2,'data3':4.2,'data4':4.2,'data5':4.2,'data6':4.2,'data7':4.2,'data8':4.2,'data9':4.2,'data10':4.2,'data11':4.2,'data12':4.2,'data13':4.2,'data14':4.2,'data15':4.2,'data16':4.2,'data17':4.2,'data18':4.2,'data19':4.2}"
 * <p>
 * Exemplo de requisição no novo formato (lista de dados):  mosquitto_pub -t sensorsync -m "[{'sensorDescription':'Teste 1 MQTT','projectID': 1,'sensorIndex': 0,'dataIndex': 0,'data':60},{'sensorDescription':'Teste 2 MQTT','projectID': 1,'sensorIndex': 0,'dataIndex': 1,'data': 30}]"
 */

public class MQTTClientInit {


    private Logger logger = Logger.getLogger(DataService.class.getName());

    static String HOST = "localhost";
    static String TOPIC = "sensorsync";
    static String CLIENT_ID = "subscribe_client";


    public void init() {
        String broker = "tcp://" + HOST + ":1883";
        int qos = 0;

        try {
            MqttClient client = new MqttClient(broker, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(60);
            options.setKeepAliveInterval(60);
            client.setCallback(new MqttCallback() {

                public void connectionLost(Throwable cause) {
                    logger.info("connectionLost: " + cause.getMessage());
                    init();// Subindo novamente o subscribe em casos de erro
                }

                public void messageArrived(String topic, MqttMessage message) throws IOException {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Data> dataList = new ArrayList<>();
                    try {
                        dataList = mapper.readValue(new String(message.getPayload()).replace("'", "\""), mapper.getTypeFactory().constructCollectionType(List.class, Data.class));
//                        logger.info("Requisicao MQTT recebida");
                        MQTTPost(dataList);
                    } catch (JsonProcessingException e) {
                        logger.info("Não foi possivel interpretar a requisicao MQTT! Erro: " + e);
                    }
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    logger.info("deliveryComplete---------" + token.isComplete());
                }

            });
            client.connect(options);
            client.subscribe(TOPIC, qos);
            logger.info("Cliente MQTT inicializado, Topico => \"" + TOPIC + "\", qos => " + qos + ", broker => \"" + broker + "\"");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void MQTTPost(List<Data> dataList) throws IOException {
        String postUrl = "http://" + HOST + ":8080/data/saveList";
        Gson gson = new Gson();
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(postUrl);
        StringEntity postingString = new StringEntity(gson.toJson(dataList));
        post.setEntity(postingString);
        post.setHeader("Content-type", "application/json");
        HttpResponse response = httpClient.execute(post);
        logger.info("Requisicao MQTT salva! Status: " + response.getStatusLine().toString());
    }
}



