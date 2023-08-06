package com.mws.sensorsync;

import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.services.DataServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datapackage")
public class DataController {

    @Autowired
    private DataServices service;

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DataPackage> findall() {
        return service.findAll();
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage findByID(@PathVariable(value = "id") String id) {
        return service.findById(id);
    }

    @RequestMapping(method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage save(@RequestBody DataPackage dataPackage) {
        return service.save(dataPackage);
    }
    @RequestMapping(method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage update(@RequestBody DataPackage dataPackage) {
        return service.update(dataPackage);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    public void delete(@PathVariable(value = "id") String id) {
        service.delete(id);
    }
}
