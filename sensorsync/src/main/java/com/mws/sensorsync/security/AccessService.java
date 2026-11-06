package com.mws.sensorsync.security;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.repositories.UserHumanRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Centraliza "quem esta logado" e "esse usuario pode mexer neste projeto?". */
@Service
public class AccessService {

    private final UserHumanRepository users;
    private final ProjectRepository projects;

    public AccessService(UserHumanRepository users, ProjectRepository projects) {
        this.users = users;
        this.projects = projects;
    }

    public UserHuman currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.unauthorized("Login necessario");
        }
        return users.findFirstByLoginOrderByIdDesc(auth.getName())
                .orElseThrow(() -> ApiException.unauthorized("Usuario nao encontrado"));
    }

    public boolean canAccess(UserHuman user, Project project) {
        return user.isAdmin() || (project.getOwner() != null && project.getOwner().getId() == user.getId());
    }

    /** Devolve o projeto se o usuario logado for o dono ou admin; senao 404 (nao revela que existe). */
    public Project requireProject(long projectId) {
        Project p = projects.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto " + projectId + " nao encontrado"));
        if (!canAccess(currentUser(), p)) {
            throw new ResourceNotFoundException("Projeto " + projectId + " nao encontrado");
        }
        return p;
    }
}
