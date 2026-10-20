package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Role;
import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.repositories.UserHumanRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserHumanService {

    public static final int MIN_PASSWORD = 8;

    private final UserHumanRepository repo;
    private final ProjectRepository projects;
    private final PasswordEncoder encoder;

    public UserHumanService(UserHumanRepository repo, ProjectRepository projects, PasswordEncoder encoder) {
        this.repo = repo;
        this.projects = projects;
        this.encoder = encoder;
    }

    /** Dados que o admin envia para criar/editar um usuario. */
    public record UserForm(String login, String firstName, String lastName, String email,
                           String password, Role role, Boolean enabled) {
    }

    public List<UserHuman> findAll() {
        return repo.findAllByOrderById();
    }

    public UserHuman findById(long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario " + id + " nao encontrado"));
    }

    @Transactional
    public UserHuman create(UserForm f) {
        String login = f.login() == null ? "" : f.login().trim();
        if (!login.matches("[A-Za-z0-9._-]{3,64}")) {
            throw ApiException.badRequest("Login deve ter de 3 a 64 caracteres (letras, numeros, . _ -)");
        }
        if (repo.findFirstByLoginOrderByIdDesc(login).isPresent()) {
            throw ApiException.conflict("Ja existe um usuario com o login " + login);
        }
        checkPassword(f.password());
        UserHuman u = new UserHuman();
        u.setLogin(login);
        u.setFirstName(f.firstName());
        u.setLastName(f.lastName());
        u.setEmail(f.email());
        u.setPassword(encoder.encode(f.password()));
        u.setRole(f.role() == null ? Role.USER : f.role());
        u.setEnabled(f.enabled() == null || f.enabled());
        return repo.save(u);
    }

    @Transactional
    public UserHuman update(long id, UserForm f, UserHuman actingUser) {
        UserHuman u = findById(id);
        if (f.firstName() != null) u.setFirstName(f.firstName());
        if (f.lastName() != null) u.setLastName(f.lastName());
        if (f.email() != null) u.setEmail(f.email());
        if (f.password() != null && !f.password().isEmpty()) {
            checkPassword(f.password());
            u.setPassword(encoder.encode(f.password()));
        }
        boolean losingAdmin = (f.role() != null && f.role() != Role.ADMIN) || Boolean.FALSE.equals(f.enabled());
        if (u.isAdmin() && losingAdmin && activeAdmins() <= 1) {
            throw ApiException.conflict("Este e o unico administrador ativo; crie outro admin antes");
        }
        if (u.getId() == actingUser.getId() && losingAdmin) {
            throw ApiException.conflict("Voce nao pode remover o seu proprio acesso de administrador");
        }
        if (f.role() != null) u.setRole(f.role());
        if (f.enabled() != null) u.setEnabled(f.enabled());
        return repo.save(u);
    }

    @Transactional
    public void delete(long id, UserHuman actingUser) {
        UserHuman u = findById(id);
        if (u.getId() == actingUser.getId()) {
            throw ApiException.conflict("Voce nao pode excluir o seu proprio usuario");
        }
        if (u.isAdmin() && activeAdmins() <= 1) {
            throw ApiException.conflict("Este e o unico administrador ativo");
        }
        if (projects.countByOwnerId(u.getId()) > 0) {
            throw ApiException.conflict("O usuario possui projetos; transfira ou exclua os projetos antes");
        }
        repo.delete(u);
    }

    @Transactional
    public void changeOwnPassword(UserHuman u, String current, String next) {
        if (current == null || !encoder.matches(current, u.getPassword())) {
            throw ApiException.badRequest("Senha atual incorreta");
        }
        checkPassword(next);
        u.setPassword(encoder.encode(next));
        repo.save(u);
    }

    private long activeAdmins() {
        return repo.findByRoleOrderById(Role.ADMIN).stream().filter(UserHuman::isEnabled).count();
    }

    private static void checkPassword(String p) {
        if (p == null || p.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("A senha deve ter pelo menos " + MIN_PASSWORD + " caracteres");
        }
    }
}
