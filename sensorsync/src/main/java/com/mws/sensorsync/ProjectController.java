package com.mws.sensorsync;

import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.services.DataServices;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.services.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("project")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private DataServices dataServices;


    //    *********************************************************************************************
//    Endpoints do Crud básico

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Project> findall() {
        return projectService.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Project findByID(@PathVariable(value = "id") Long id) {
        return projectService.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Project save(@RequestBody Project dataPackage) {
        return projectService.save(dataPackage);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Project update(@RequestBody Project dataPackage) {
        return projectService.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
//    *********************************************************************************************

    //Endpoint para deletar todos os dados relacionados a um id específico
    @DeleteMapping(value = "/deleteAll/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean deleteAll(@PathVariable(value = "id") Long projectID) {
        projectService.delete(projectID);
        metadataService.deleteAllMetadata(projectID);
        dataServices.deleteAllData(projectID);
        return true;
    }


}
