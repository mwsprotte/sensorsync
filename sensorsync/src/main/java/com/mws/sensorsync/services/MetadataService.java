package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.repositories.MetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class MetadataService {

    private Logger logger = Logger.getLogger(MetadataService.class.getName());


    @Autowired
    MetadataRepository metadataRepository;


//    *********************************************************************************************
//    Crud básico

    public List<Metadata> findAll() {
        return metadataRepository.findAll();
    }

    public Metadata findById(Long id) {
        return metadataRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
    }

    public Metadata save(Metadata metadata) {
        return metadataRepository.save(metadata);
    }

    public Metadata update(Metadata metadata) {
        var entity = metadataRepository.findById(metadata.getId()).orElseThrow(() -> new ResourceNotFoundException("Não encontrado um dado correspondente ao ID " + metadata.getId()));
        return save(metadata);
    }

    public void delete(Long id){
        var entity = metadataRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
        metadataRepository.delete(entity);
    }

//    *********************************************************************************************
//todo: métodos específicos para essa classe

    public Metadata findByProjectIdAndDataIndex(Long projectID, Long dataIndex) {
        return metadataRepository.findByProjectAndIndexForCard(projectID,dataIndex);
    }

    public List<Metadata> findByProjectId(Long projectID) {
        return metadataRepository.findByProject(projectID);
    }

    //Método para deletar todos os metadados para um id de projeto
    public void deleteAllMetadata(Long projectID){
        metadataRepository.deleteAlLByProjectID(Math.toIntExact(projectID));
    }

//    *********************************************************************************************


}
