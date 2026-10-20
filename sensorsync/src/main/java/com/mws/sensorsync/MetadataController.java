package com.mws.sensorsync;

import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.security.AccessService;
import com.mws.sensorsync.services.MetadataService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Variaveis (metadados) de um projeto. A criacao acontece junto com o projeto (POST /project). */
@RestController
@RequestMapping("metadata")
public class MetadataController {

    private final MetadataService metadataService;
    private final AccessService access;

    public MetadataController(MetadataService metadataService, AccessService access) {
        this.metadataService = metadataService;
        this.access = access;
    }

    @GetMapping(value = "/project/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Metadata> findByProject(@PathVariable("projectId") Long projectId) {
        access.requireProject(projectId);
        return metadataService.findByProjectId(projectId);
    }
}
