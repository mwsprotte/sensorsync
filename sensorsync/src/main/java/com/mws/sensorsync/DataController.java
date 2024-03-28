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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    @GetMapping(value = "/charts/{projectID}/device/{device}/length/{length}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<String>> findDataCharts(@PathVariable(value = "projectID") Long id, @PathVariable(value = "device") Long device, @PathVariable(value = "length") Long length) {
        List<List<String>> dataForCharts = new ArrayList<>();

//        Iterando para carregar os títulos das curvas
        List<String> labels = new ArrayList<>();
        var entityDesc = metadataService.findByProjectId(id);
        for (int i = 0; i < entityDesc.size(); i++) {
            if (entityDesc.get(i).isUseChart()) {
                labels.add(entityDesc.get(i).getDataDesc());
            }
        }

        Collections.reverse(labels);
        dataForCharts.add(labels);

//      Iterando para carregar o eixo x
        List<String> labelsX = new ArrayList<>();
        var entityX = dataServices.findForChart(id, device, 0L, length);
        for (int i = 0; i < entityX.size(); i++) {
            labelsX.add(entityX.get(i).getReadTime());
        }
        Collections.reverse(labelsX);
        dataForCharts.add(labelsX);

//        Iterando para carregar os conjuntos de dados para cada grandeza
        int maxIndex = (int) projectService.findById(id).getDataNumber();
        for (int i = 0; i < maxIndex; i++) {
            List<String> labelsY = new ArrayList<>();
            var entityY = dataServices.findForChart(id, device, Long.valueOf(i), length);
            for (int j = 0; j < entityY.size(); j++) {
                labelsY.add(String.valueOf(entityY.get(j).getData()));
            }
            Collections.reverse(labelsY);
            dataForCharts.add(labelsY.stream().toList());
        }
        return dataForCharts;
    }

}
