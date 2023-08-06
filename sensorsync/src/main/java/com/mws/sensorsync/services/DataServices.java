package com.mws.sensorsync.services;

import com.mws.sensorsync.model.DataPackage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@Service
public class DataServices {

    private final AtomicLong counter = new AtomicLong();
    private Logger logger = Logger.getLogger(DataServices.class.getName());

    public List<DataPackage> findAll() {
        List<DataPackage> datapackages = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            DataPackage dataPackage = mockDataPackage(i);
            datapackages.add(dataPackage);
        }
        return datapackages;
    }

    private DataPackage mockDataPackage(int i) {

        logger.info("Finding all data packages...");

        DataPackage dataPackage = new DataPackage();
        dataPackage.setId(counter.incrementAndGet());
        dataPackage.setDescription("Sensor " + i);
        dataPackage.setData1(1 * i);
        dataPackage.setData1(2 * i);
        dataPackage.setData2(3 * i);

        return dataPackage;
    }


    public DataPackage findById(String id) {

        logger.info("Finding the data...");

        DataPackage dataPackage = new DataPackage();
        dataPackage.setId(counter.incrementAndGet());
        dataPackage.setDescription("Sensor 1");
        dataPackage.setData1(123);
        dataPackage.setData1(456);
        dataPackage.setData2(789);

        return dataPackage;
    }

    public DataPackage save(DataPackage dataPackage) {

        logger.info("Saving the data package...");

        return dataPackage;
    }

    public DataPackage update(DataPackage dataPackage) {

        logger.info("Updating one data package...");

        return dataPackage;
    }

    public void delete(String id) {

        logger.info("Deleting one data package...");
    }

}
