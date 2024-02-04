package com.mws.sensorsync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mws.sensorsync.model.DataPackage;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;

public class MQTTClientInit {

    public void init() {
        //		String broker = "tcp://broker.emqx.io:1883";
        String broker = "tcp://localhost:1883";
//		String topic = "mqtt/test";
        String topic = "teste";
//		String username = "emqx";
//		String password = "public";
        String clientid = "subscribe_client";
        int qos = 0;

        try {
            MqttClient client = new MqttClient(broker, clientid, new MemoryPersistence());
            // connect options
            MqttConnectOptions options = new MqttConnectOptions();
//		Como nos testes preliminares não há senha, não são necessárias essas etapas de autenticação
//			options.setUserName(username);
//			options.setPassword(password.toCharArray());
            options.setConnectionTimeout(60);
            options.setKeepAliveInterval(60);
            // setup callback
            client.setCallback(new MqttCallback() {

                public void connectionLost(Throwable cause) {
                    System.out.println("connectionLost: " + cause.getMessage());
                }

                public void messageArrived(String topic, MqttMessage message) throws IOException {
//                    System.out.println("topic: " + topic);
//                    System.out.println("Qos: " + message.getQos());
//                    System.out.println("message content: " + new String(message.getPayload()));
                    ObjectMapper mapper = new ObjectMapper();
                    DataPackage dataPackage = null;
                    try {
                        dataPackage = mapper.readValue(new String(message.getPayload()).replace("'", "\""), DataPackage.class);
                        MQTTPost(dataPackage);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println(dataPackage.getData0());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("deliveryComplete---------" + token.isComplete());
                }

            });
            client.connect(options);
            client.subscribe(topic, qos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void MQTTPost(DataPackage dataPackage) throws IOException {
        String postUrl = "http://localhost:8080/datapackage";
        Gson gson = new Gson();
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(postUrl);
        StringEntity postingString = new StringEntity(gson.toJson(dataPackage));
        post.setEntity(postingString);
        post.setHeader("Content-type", "application/json");
        HttpResponse response = httpClient.execute(post);
    }
}



