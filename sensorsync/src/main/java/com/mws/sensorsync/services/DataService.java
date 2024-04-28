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

//    *********************************************************************************************
//todo: métodos específicos para essa classe

    //Método para um dado a partir de seu id de projeto, índice de sensor e índice de dados
    public Data findForCard(Long projectID, Long sensorIndex, Long dataIndex) {
        logger.info("Buscando o dado " + dataIndex + " para o projeto " + projectID + " correspondente ao sensor " + sensorIndex);
        return dataRepository.findByProjectAndNodeForCard(projectID, sensorIndex, dataIndex);
    }

    public List<Data> findForChart(Long projectID, Long sensorIndex, Long dataIndex, Long length) {
        logger.info("Buscando os dados para o projeto " + projectID + " correspondente ao sensor " + sensorIndex);
        return dataRepository.findByProjectAndDeviceForChart(projectID, sensorIndex, dataIndex, length);
    }

    //Método para deletar dodos os dados para um respectivo projeto
    public void deleteAllData(Long projectID) {
        dataRepository.deleteAlLByProjectID(Math.toIntExact(projectID));
    }

//    *********************************************************************************************


}
