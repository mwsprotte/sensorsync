package com.mws.sensorsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Startup {

    public static void main(String[] args) {
        SpringApplication.run(Startup.class, args);
        MQTTClientInit mqtt = new MQTTClientInit();
        mqtt.init();
    }

}
