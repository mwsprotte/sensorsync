package com.mws.sensorsync;

import com.mws.sensorsync.model.ProjectMetadata;
import com.mws.sensorsync.services.DataPackageServices;
import com.mws.sensorsync.services.ProjectMetadataServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projectmetadata")
public class ProjectMetadataController {

    @Autowired
    private ProjectMetadataServices services;

    @Autowired
    private DataPackageServices dataPackageServices;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProjectMetadata> findAll() {
        return services.findAll();
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProjectMetadata save(@RequestBody ProjectMetadata projectMetadata) {
        return services.save(projectMetadata);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectMetadata findByID(@PathVariable(value = "id") Long id) {
        return services.findById(id);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        services.delete(id);
        return ResponseEntity.noContent().build();
    }

    //todo: testa esse endpoint [deletar tanto o projeto quanto seus dados gravados]
    @DeleteMapping(value = "/deleteAll/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean deleteAll(@PathVariable(value = "id") Long projectID) {
        services.delete(projectID);
        dataPackageServices.deleteAll(projectID.intValue());
        return true;
    }

}

