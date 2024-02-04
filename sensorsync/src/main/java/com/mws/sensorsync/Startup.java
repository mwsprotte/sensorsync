package com.mws.sensorsync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.services.DataPackageServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

@SpringBootApplication
public class Startup {

	
	
	public static void main(String[] args) {

		SpringApplication.run(Startup.class, args);
		MQTTClientInit mqtt = new MQTTClientInit();
		mqtt.init();

	}

}
