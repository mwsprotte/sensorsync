package com.mws.sensorsync;

import com.mws.sensorsync.model.ProjectMetadata;
import com.mws.sensorsync.services.ProjectMetadataServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projectmetadata")
public class ProjectMetadataController {

    @Autowired
    private ProjectMetadataServices services;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProjectMetadata> findAll(){return services.findAll();}

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public  ProjectMetadata save(@RequestBody ProjectMetadata projectMetadata){return services.save(projectMetadata);}

}
