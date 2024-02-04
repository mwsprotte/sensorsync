package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.ProjectMetadata;
import com.mws.sensorsync.repositories.ProjectMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class ProjectMetadataServices {

    private Logger logger = Logger.getLogger(ProjectMetadataServices.class.getName());

    @Autowired
    ProjectMetadataRepository repository;

    public List<ProjectMetadata> findAll() {
        logger.info("Buscando todos projetos");
        return repository.findAll();
    }

    public ProjectMetadata findById(Long id) {
        logger.info("Buscando o Projeto de ID " + id);
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foi encontrado um projeto correspondente ao ID " + id));
    }

    public ProjectMetadata save(ProjectMetadata projectMetadata) {
        logger.info("Salvando o projeto de ID " + projectMetadata.getId());
        return repository.save(projectMetadata);
    }


    public ProjectMetadata update(ProjectMetadata projectMetadata) {
        logger.info("Atualizando o projeto de ID " + projectMetadata.getId());
        return repository.save(projectMetadata);
    }

    public void delete(Long id) {
        logger.info("Deletando o projeto de ID " + id);
        var entity = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foi encontrado o projeto de ID " + id));;
        repository.delete(entity);
    }

}
