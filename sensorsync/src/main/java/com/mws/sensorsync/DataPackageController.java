package com.mws.sensorsync;

import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.services.DataPackageServices;
import com.mws.sensorsync.services.ProjectMetadataServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

//    //Método para salvar os dados a partir de uma get request [DEPRECIADO, PREFERÍVEL POST]
//    @GetMapping(value = "/save/{description}/{data0}/{data1}/{data2}", produces = MediaType.APPLICATION_JSON_VALUE)
//    public DataPackage saveByGet(@PathVariable(value = "description") String description,
//                                 @PathVariable(value = "data0") Double data0,
//                                 @PathVariable(value = "data1") Double data1,
//                                 @PathVariable(value = "data2") Double data2) {
//
//        DataPackage dataPackage = new DataPackage();
//
//        dataPackage.setDescription(description);
//        dataPackage.setData0(data0);
//        dataPackage.setData1(data1);
//        dataPackage.setData2(data2);
//
//        return save(dataPackage);
//    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage save(@RequestBody DataPackage dataPackage) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        dataPackage.setReadTime(dtf.format(LocalDateTime.now()));
//        System.out.println("yyyy/MM/dd HH:mm:ss-> " + dtf.format(LocalDateTime.now()));
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

//    @DeleteMapping(value = "/deleteAll/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
//    public boolean deleteAll(@PathVariable(value = "id") int projectID) {
//        service.deleteAll(projectID);
//        return true;
//    }

    //    Retornando todos os valores de um ID de projeto específico [ex: http://localhost:8080/datapackage/project/1]
    @GetMapping(value = "/project/{projectID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DataPackage> findByProject(@PathVariable(value = "projectID") Long projectID) {
        return service.findByProject(projectID);
    }

    //    Retornando todos os valores de um ID de projeto específico e de um nó específico [ex: http://localhost:8080/datapackage/project/1/node/2]
    @GetMapping(value = "/project/{projectID}/node/{nodeID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DataPackage> findByProjectAndNode(@PathVariable(value = "projectID") Long projectID, @PathVariable(value = "nodeID") Long nodeID) {
        return service.findByProjectAndNode(projectID, nodeID);
    }

    //    todo: refornar o último pacote de dados recebido [SELECT LAST_INSERT_ID]
//    Retornando todos os valores de um ID de projeto específico e de um nó específico [ex: http://localhost:8080/datapackage/project/1/node/2/last]
    @GetMapping(value = "/project/{projectID}/node/{nodeID}/last", produces = MediaType.APPLICATION_JSON_VALUE)
    public DataPackage findByProjectAndNodeLast(@PathVariable(value = "projectID") Long projectID, @PathVariable(value = "nodeID") Long nodeID) {
        return service.findByProjectAndNodeLast(projectID, nodeID);
    }

 //    Retornando todos os valores de um ID de projeto específico e para todos os nós [ex: http://localhost:8080/datapackage/project/2/lastNodes/2]
    @GetMapping(value = "/project/{projectID}/lastNodes/{nodeNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DataPackage> findByProjectAndNodeLastAllNodes(@PathVariable(value = "projectID") Long projectID, @PathVariable(value = "nodeNumber") Long nodeNumber) {
        List<DataPackage> dataPackages = new ArrayList<>();
        for (Long i = 1L; i < nodeNumber + 1; i++) {
            dataPackages.add(service.findByProjectAndNodeLast(projectID, i));
        }
        return dataPackages;
    }


}
