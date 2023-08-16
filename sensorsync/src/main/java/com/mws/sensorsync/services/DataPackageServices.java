package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.repositories.DataPackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class DataPackageServices {

    private Logger logger = Logger.getLogger(DataPackageServices.class.getName());

    @Autowired
    DataPackageRepository repository;

    public List<DataPackage> findAll() {
        logger.info("Finding all data packages...");
        return repository.findAll();
    }


    public DataPackage findById(Long id) {

        logger.info("Finding the data...");

//        DataPackage dataPackage = new DataPackage();
//        dataPackage.setId(counter.incrementAndGet());
//        dataPackage.setDescription("Sensor 1");
//        dataPackage.setData1(123);
//        dataPackage.setData1(456);
//        dataPackage.setData2(789);

        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No records for this id!"));
    }

    public DataPackage save(DataPackage dataPackage) {

        logger.info("Saving the data package...");

        return repository.save(dataPackage);
    }

    public DataPackage update(DataPackage dataPackage) {

        logger.info("Updating one data package...");

        var entity = repository.findById(dataPackage.getId()).orElseThrow(() -> new ResourceNotFoundException("No records for this id!"));

        return repository.save(dataPackage);
    }

    public void delete(Long id) {

        logger.info("Deleting one data package...");

        var entity =  repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No records for this id!"));

        repository.delete(entity);
    }

}
