package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.repositories.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class ProjectService {

    private Logger logger = Logger.getLogger(ProjectService.class.getName());

    @Autowired
    ProjectRepository projectRepository;

//    @Autowired
//    private DataServices dataServices;
//
//    @Autowired
//    private MetadataService metadataService;


//    *********************************************************************************************
//    Crud básico

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public Project findById(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
    }

    public Project save(Project project) {
        boolean isNew = true;
        List<Project> projects = projectRepository.findAll();
        for (Project value : projects) {
            if (value.getName().equals(project.getName())) {
                isNew = false;
                break;
            }
        }
        if (isNew) {
            return projectRepository.save(project);
        } else {
            return null;
        }
    }

    public Project update(Project project) {
        var entity = projectRepository.findById(project.getId()).orElseThrow(() -> new ResourceNotFoundException("Não encontrado um dado correspondente ao ID " + project.getId()));
        return save(project);
    }

    public void delete(Long id) {
        var entity = projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
        projectRepository.delete(entity);
    }


//    *********************************************************************************************


}
