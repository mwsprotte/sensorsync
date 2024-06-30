package com.mws.sensorsync;

import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.services.DataService;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.services.ProjectService;
import com.mws.sensorsync.views.CardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/data")
public class DataController {

    @Autowired
    private DataService dataServices;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private ProjectService projectService;

    /**
     * Endpoints do Crud básico
     */

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

    @DeleteMapping(value = "cleanProject/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteAll(@PathVariable(value = "id") Long id) {
        dataServices.deleteAllData(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Recebendo uma lista de dados para salvar no banco
     */
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

    /**
     * RETORNA TODOS OS CARDS PARA UM PROJETO
     */
    @GetMapping(value = "/cards/{projectID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CardView> findDataCards(@PathVariable(value = "projectID") Long id) {
        List<CardView> cards = new ArrayList<>();
        for (Long i = 0L; i < projectService.findById(id).getSensorNumber(); i++) {
            for (Long j = 0L; j < (projectService.findById(id).getDataNumber()); j++) {
                if (metadataService.findByProjectIdAndDataIndex(id, j).isUseCard()) {
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
        }
        return cards;
    }

    /**
     * RETORNA TODOS OS CARDS PARA UM PROJETO DE ACORDO COM O DISPOSITIVO ESCOLHIDO
     */
    @GetMapping(value = "/cards/{projectID}/device/{device}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CardView> findDataCardsByDevice(@PathVariable(value = "projectID") Long id, @PathVariable(value = "device") Long device) {
        List<CardView> cards = new ArrayList<>();
        for (Long j = 0L; j < (projectService.findById(id).getDataNumber()); j++) {
            if (metadataService.findByProjectIdAndDataIndex(id, j).isUseCard()) {
                var entity = dataServices.findForCard(id, device, j);
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

    /**
     * RETORNA OS DADOS PARA O GRÁFICO DE UM PROJETO DE ACORDO COM O DISPOSITIVO ESCOLHIDO
     */
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
            if (metadataService.findByProjectId(id).get(i).isUseChart()) {
                List<String> labelsY = new ArrayList<>();
                var entityY = dataServices.findForChart(id, device, Long.valueOf(i), length);
                for (int j = 0; j < entityY.size(); j++) {
                    labelsY.add(String.valueOf(entityY.get(j).getData()));
                }
                Collections.reverse(labelsY);
                dataForCharts.add(labelsY.stream().toList());

            }

        }
        return dataForCharts;
    }

    /**
     * Retornar todos os dados de determinado projeto para gerar um relatório
     */
    @GetMapping(value = "/report/{projectID}/device/{device}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<String>> findAllDataReport(@PathVariable(value = "projectID") Long id, @PathVariable(value = "device") Long device) {
        List<List<String>> dataForReport = new ArrayList<>();

        List<String> labels = new ArrayList<>();
        labels.add("Instante");

        var projectInfo = projectService.findById(id);
        var entityDesc = metadataService.findByProjectId(id);
        for (int i = 0; i < entityDesc.size(); i++) {
            labels.add(entityDesc.get(i).getDataDesc());
        }

        dataForReport.add(labels);

        List<Data> allData = dataServices.findAllByProjectAndSensor(id, device);

        for (int i = 0; i < allData.size(); i++) {
            HashMap<Integer, String> map = new HashMap<>();
            map.put(0, allData.get(i).getReadTime());
            for (int j = i; j < allData.size(); j++) {
                map.put((int) allData.get(j).getDataIndex() + 1, String.valueOf(allData.get(j).getData()));
                if (map.size() == projectInfo.getDataNumber() + 1) {
                    i = j;
                    break;
                }
            }
            dataForReport.add(map.values().stream().toList());
        }

        return dataForReport;
    }

    /**
     * Endpoints da interface máquina
     */
    @GetMapping(value = "project/{project_id}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectID(@PathVariable(value = "project_id") Long project_id, @PathVariable(value = "n") Long length) {
        return dataServices.findByProject(project_id, length);
    }

    @GetMapping(value = "project/{project_id}/device/{y}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectIDandDevice(@PathVariable(value = "project_id") Long project_id, @PathVariable(value = "y") Long device, @PathVariable(value = "n") Long length) {
        return dataServices.findByProjectIDandDevice(project_id, device, length);
    }

    @GetMapping(value = "project/{project_id}/device/{y}/data/{z}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectIDandDeviceandData(@PathVariable(value = "project_id") Long project_id, @PathVariable(value = "y") Long device, @PathVariable(value = "z") Long dataIndex, @PathVariable(value = "n") Long length) {
        return dataServices.findByProjectIDandDeviceandData(project_id, device, dataIndex, length);
    }

}
