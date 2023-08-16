package com.mws.sensorsync;

import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.services.DataPackageServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datapackage")
public class DataPackageController {

    @Autowired
    private DataPackageServices service;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DataPackage> findall() {
        return service.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage findByID(@PathVariable(value = "id") Long id) {
        return service.findById(id);
    }

    //TO SAVE A VALUE DIRECTLY BY A REQUEST
    @GetMapping(value = "/save/{description}/{data0}/{data1}/{data2}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage saveByGet(@PathVariable(value = "description") String description,
                                 @PathVariable(value = "data0") Double data0,
                                 @PathVariable(value = "data1") Double data1,
                                 @PathVariable(value = "data2") Double data2) {

        DataPackage dataPackage = new DataPackage();

        dataPackage.setDescription(description);
        dataPackage.setData0(data0);
        dataPackage.setData1(data1);
        dataPackage.setData2(data2);

        return save(dataPackage);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage save(@RequestBody DataPackage dataPackage) {
        return service.save(dataPackage);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage update(@RequestBody DataPackage dataPackage) {
        return service.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
