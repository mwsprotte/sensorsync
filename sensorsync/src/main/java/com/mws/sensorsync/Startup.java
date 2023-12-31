package com.mws.sensorsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

@SpringBootApplication
public class Startup {

	public static void main(String[] args) {
		SpringApplication.run(Startup.class, args);

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

				public void messageArrived(String topic, MqttMessage message) {
					System.out.println("topic: " + topic);
					System.out.println("Qos: " + message.getQos());
					System.out.println("message content: " + new String(message.getPayload()));

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

}
