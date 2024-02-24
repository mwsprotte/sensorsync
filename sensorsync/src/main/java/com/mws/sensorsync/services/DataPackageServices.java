package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.DataPackage;
import com.mws.sensorsync.repositories.DataPackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.logging.Logger;

@Service
public class DataPackageServices {

    private Logger logger = Logger.getLogger(DataPackageServices.class.getName());

    @Autowired
    DataPackageRepository repository;

    public List<DataPackage> findAll() {
        logger.info("Buscando todos os pacotes de dados");
        return repository.findAll();
    }


    public DataPackage findById(Long id) {
        logger.info("Buscando o pacote de dados de ID " + id);
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foi encontrado um pacote de dados correspondente ao ID " + id));
    }


    public List<DataPackage> findByProject(Long projectId) {
        logger.info("Buscando os pacotes de dados para o projeto de ID " + projectId);
        return repository.findByProject(projectId);
    }

    public List<DataPackage> findByProjectAndNode(Long projectId, Long nodeID) {
        logger.info("Buscando os pacotes de dados para o projeto de ID " + projectId + " e nó: " + nodeID);
        return repository.findByProjectAndNode(projectId, nodeID);
    }

    public DataPackage findByProjectAndNodeLast(Long projectId, Long nodeID) {
        logger.info("Buscando os pacotes de dados para o projeto de ID " + projectId + " e nó: " + nodeID);
        return repository.findByProjectAndNodeLast(projectId, nodeID);
    }


    public DataPackage save(DataPackage dataPackage) {
        logger.info("Salvando o pacote de dados envidado pelo nó " + String.valueOf(dataPackage.getNodeIndex()) + " No projeto " + dataPackage.getProjectID());
        return repository.save(dataPackage);
    }

    public DataPackage update(DataPackage dataPackage) {
        logger.info("Atualizando o pacote de dados de ID " + dataPackage.getId());
        var entity = repository.findById(dataPackage.getId()).orElseThrow(() -> new ResourceNotFoundException("Não encontrado um pacote de dados para o ID " + dataPackage.getId()));
        return repository.save(dataPackage);
    }

    public void delete(Long id) {
        logger.info("Deletando pacote de dados de ID " + id);
        var entity = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foi encontrado o pacote de dados de ID " + id));
        repository.delete(entity);
    }

    public void deleteAll(int id) {
        logger.info("Deletando todos os pacote de dados de ID " + id);
        repository.deleteAlLByProjectID(id);
    }

}
