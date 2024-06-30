package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.repositories.DataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service

public class DataService {

    private Logger logger = Logger.getLogger(DataService.class.getName());

    @Autowired
    DataRepository dataRepository;

//    *********************************************************************************************
//    Crud básico

    public List<Data> findAll() {
        return dataRepository.findAll();
    }

    public Data findById(Long id) {
        return dataRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
    }

    public Data save(Data data) {
        return dataRepository.save(data);
    }

    public Data update(Data data) {
        var entity = dataRepository.findById(data.getId()).orElseThrow(() -> new ResourceNotFoundException("Não encontrado um dado correspondente ao ID " + data.getId()));
        return save(data);
    }

    public void delete(Long id) {
        var entity = dataRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
        dataRepository.delete(entity);
    }


    /**
     * Método para um dado a partir de seu id de projeto, índice de sensor e índice de dados
     */
    public Data findForCard(Long projectID, Long sensorIndex, Long dataIndex) {
        return dataRepository.findByProjectAndNodeForCard(projectID, sensorIndex, dataIndex);
    }

    public List<Data> findForChart(Long projectID, Long sensorIndex, Long dataIndex, Long length) {
        return dataRepository.findByProjectAndDeviceForChart(projectID, sensorIndex, dataIndex, length);
    }

    public List<Data> findAllForReport(Long projectID, Long sensorIndex, Long dataIndex) {
        return dataRepository.findAllByProjectAndDeviceAndIndex(projectID, sensorIndex, dataIndex);
    }

    public List<Data> findAllByProjectAndSensor(Long projectID, Long sensorIndex) {
        return dataRepository.findAllByProjectAndDeviceAndIndex(projectID, sensorIndex);
    }

    /**
     * Método para deletar dodos os dados para um respectivo projeto
     */
    public void deleteAllData(Long projectID) {
        dataRepository.deleteAlLByProjectID(Math.toIntExact(projectID));
        logger.info("Todos os dados para o projeto " + projectID + " removidos!");
    }

    public List<Data> findByProject(Long projectId, Long length) {
        logger.info("Buscando os dados para o projeto " + projectId);
        return dataRepository.findByProject(projectId, length);
    }

    public List<Data> findByProjectIDandDevice(Long projectId, Long device, Long length) {
        logger.info("Buscando os dados para o projeto " + projectId + "e dispositivo " + device);
        return dataRepository.findByProjectIDandDecvice(projectId, device, length);
    }

    public List<Data> findByProjectIDandDeviceandData(Long projectId, Long device, Long dataIndex, Long length) {
        logger.info("Buscando os dados para o projeto " + projectId + "e dispositivo " + device);
        return dataRepository.findByProjectIDandDecviceandData(projectId, device, dataIndex, length);
    }

//    *********************************************************************************************


}
