package com.mws.sensorsync;

import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.services.DataServices;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.services.ProjectService;
import com.mws.sensorsync.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/data")
public class DataController {

    @Autowired
    private DataServices dataServices;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private ProjectService projectService;

//    *********************************************************************************************
//    Endpoints do Crud básico

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findall() {
        return dataServices.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Data findByID(@PathVariable(value = "id") Long id) {
        return dataServices.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Data save(@RequestBody Data data) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
        data.setReadTime(dtf.format(LocalDateTime.now()));
        return dataServices.save(data);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Data update(@RequestBody Data dataPackage) {
        return dataServices.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        dataServices.delete(id);
        return ResponseEntity.noContent().build();
    }
//    *********************************************************************************************

    //  Recebendo uma lista de dados para salvar no banco
    @PostMapping(value = "/saveList", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public boolean saveList(@RequestBody List<Data> dataList) {
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
            for (Data data : dataList) {
                data.setReadTime(dtf.format(LocalDateTime.now()));
                dataServices.save(data);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //todo: endpoint para retornar objetos de dados para os cards
    @GetMapping(value = "/cards/{projectID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CardView> findDataCards(@PathVariable(value = "projectID") Long id) {
        List<CardView> cards = new ArrayList<>();
        for (Long i = 0L; i < projectService.findById(id).getSensorNumber(); i++) {
            for (Long j = 0L; j < (projectService.findById(id).getDataNumber()); j++) {
                var entity = dataServices.findForCard(id, i, j);
                CardView dataToAdd = new CardView();
                dataToAdd.setData(entity.getData());
                dataToAdd.setReadTime(entity.getReadTime());
                dataToAdd.setSensorDescription(entity.getSensorDescription());
                dataToAdd.setSensorIndex(entity.getSensorIndex());
                dataToAdd.setDataDesc(metadataService.findByProjectIdAndDataIndex(id, j).getDataDesc());
                cards.add(dataToAdd);
            }
        }
        return cards;
    }
}
