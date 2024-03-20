package com.mws.sensorsync;

import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.services.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;

//    *********************************************************************************************
//    Endpoints do Crud básico

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Metadata> findall() {
        return metadataService.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Metadata findByID(@PathVariable(value = "id") Long id) {
        return metadataService.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Metadata save(@RequestBody Metadata dataPackage) {
        return metadataService.save(dataPackage);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Metadata update(@RequestBody Metadata dataPackage) {
        return metadataService.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        metadataService.delete(id);
        return ResponseEntity.noContent().build();
    }

//    ********************************************************************************************
//todo: testar esse novo método que recebe uma lista de metadados

    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveList(@RequestBody List<Metadata> metadataList) {
        try {
            for (Metadata metadata : metadataList) {
                save(metadata);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.toString());
        }
    }
}
//    ********************************************************************************************
