package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.DataRepository;
import com.mws.sensorsync.repositories.DeviceRepository;
import com.mws.sensorsync.repositories.MetadataRepository;
import com.mws.sensorsync.repositories.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {

    public static final int MAX_DATA = 20;
    public static final int MAX_DEVICES = 50;

    private final ProjectRepository projectRepository;
    private final MetadataRepository metadataRepository;
    private final DataRepository dataRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    public ProjectService(ProjectRepository projectRepository, MetadataRepository metadataRepository,
                          DataRepository dataRepository, DeviceRepository deviceRepository, DeviceService deviceService) {
        this.projectRepository = projectRepository;
        this.metadataRepository = metadataRepository;
        this.dataRepository = dataRepository;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
    }

    /** Uma variavel do projeto, como enviada pela tela de criacao. */
    public record VariableForm(String dataDesc, boolean useCard, boolean useChart) {
    }

    /** Pedido de criacao de projeto: nome, variaveis e quantos dispositivos criar. */
    public record CreateProjectForm(String name, Integer devices, List<VariableForm> variables) {
    }

    /** Resposta da criacao: o projeto e os dispositivos com seus tokens (exibidos uma unica vez). */
    public record CreatedProject(Project project, List<DeviceService.DeviceWithToken> devices) {
    }

    public List<Project> findVisibleTo(UserHuman user) {
        return user.isAdmin() ? projectRepository.findAllByOrderById() : projectRepository.findByOwnerIdOrderById(user.getId());
    }

    public Project findById(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Projeto " + id + " nao encontrado"));
    }

    @Transactional
    public CreatedProject create(CreateProjectForm form, UserHuman owner) {
        String name = form.name() == null ? "" : form.name().trim();
        if (name.isEmpty() || name.length() > 254) {
            throw ApiException.badRequest("Informe um nome de projeto (ate 254 caracteres)");
        }
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Ja existe um projeto com o nome " + name);
        }
        List<VariableForm> vars = form.variables() == null ? List.of() : form.variables();
        if (vars.isEmpty() || vars.size() > MAX_DATA) {
            throw ApiException.badRequest("O projeto deve ter de 1 a " + MAX_DATA + " variaveis");
        }
        int nDevices = form.devices() == null ? 1 : form.devices();
        if (nDevices < 1 || nDevices > MAX_DEVICES) {
            throw ApiException.badRequest("Quantidade de dispositivos deve ser de 1 a " + MAX_DEVICES);
        }

        Project p = new Project();
        p.setName(name);
        p.setDataNumber(vars.size());
        p.setSensorNumber(0);
        p.setOwner(owner);
        p = projectRepository.save(p);

        for (int i = 0; i < vars.size(); i++) {
            VariableForm v = vars.get(i);
            Metadata m = new Metadata();
            m.setProjectID(p.getId());
            m.setDataIndex(i);
            m.setDataDesc(v.dataDesc() == null || v.dataDesc().isBlank() ? "Dado " + (i + 1) : v.dataDesc().trim());
            m.setUseCard(v.useCard());
            m.setUseChart(v.useChart());
            metadataRepository.save(m);
        }

        List<DeviceService.DeviceWithToken> created = new ArrayList<>();
        for (int i = 0; i < nDevices; i++) {
            created.add(deviceService.create(p, null));
        }
        return new CreatedProject(findById(p.getId()), created);
    }

    @Transactional
    public Project rename(Project p, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw ApiException.badRequest("Nome vazio");
        }
        if (!n.equalsIgnoreCase(p.getName()) && projectRepository.existsByNameIgnoreCase(n)) {
            throw ApiException.conflict("Ja existe um projeto com o nome " + n);
        }
        p.setName(n);
        return projectRepository.save(p);
    }

    /** Exclui o projeto com todos os seus dados, variaveis e dispositivos. */
    @Transactional
    public void deleteAll(Project p) {
        dataRepository.deleteAlLByProjectID(Math.toIntExact(p.getId()));
        metadataRepository.deleteAlLByProjectID(Math.toIntExact(p.getId()));
        deviceRepository.deleteAllByProject(p.getId());
        projectRepository.delete(p);
    }

    @Transactional
    public void deleteData(Project p) {
        dataRepository.deleteAlLByProjectID(Math.toIntExact(p.getId()));
    }
}
