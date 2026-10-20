package com.mws.sensorsync;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Device;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.security.AccessService;
import com.mws.sensorsync.services.CodeGenerator;
import com.mws.sensorsync.services.DeviceService;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.services.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("project")
public class ProjectController {

    private final ProjectService projectService;
    private final MetadataService metadataService;
    private final DeviceService deviceService;
    private final CodeGenerator codeGenerator;
    private final AccessService access;
    private final String publicHost;
    private final int mqttPublicPort;

    public ProjectController(ProjectService projectService, MetadataService metadataService, DeviceService deviceService,
                             CodeGenerator codeGenerator, AccessService access,
                             @Value("${sensorsync.public-host:}") String publicHost,
                             @Value("${sensorsync.mqtt.public-port:1883}") int mqttPublicPort) {
        this.projectService = projectService;
        this.metadataService = metadataService;
        this.deviceService = deviceService;
        this.codeGenerator = codeGenerator;
        this.access = access;
        this.publicHost = publicHost;
        this.mqttPublicPort = mqttPublicPort;
    }

    /** Dispositivo com o token em texto puro (so aparece nesta resposta). */
    public record DeviceTokenView(Device device, String token) {
        static DeviceTokenView of(DeviceService.DeviceWithToken d) {
            return new DeviceTokenView(d.device(), d.token());
        }
    }

    public record CreatedProjectView(Project project, List<DeviceTokenView> devices) {
    }

    // ---- Projetos ---------------------------------------------------------------

    /** Projetos do usuario logado (admin ve todos). */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Project> findAll() {
        return projectService.findVisibleTo(access.currentUser());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Project findByID(@PathVariable("id") Long id) {
        return access.requireProject(id);
    }

    /**
     * Cria projeto + variaveis + dispositivos em uma unica operacao.
     * Corpo: {"name": "...", "devices": 2, "variables": [{"dataDesc": "Temperatura", "useCard": true, "useChart": true}]}
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public CreatedProjectView create(@RequestBody ProjectService.CreateProjectForm form) {
        ProjectService.CreatedProject created = projectService.create(form, access.currentUser());
        return new CreatedProjectView(created.project(), created.devices().stream().map(DeviceTokenView::of).toList());
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Project rename(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        return projectService.rename(access.requireProject(id), body.get("name"));
    }

    /** Exclui o projeto com todos os dados, variaveis e dispositivos. */
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        projectService.deleteAll(access.requireProject(id));
        return ResponseEntity.noContent().build();
    }

    /** Apaga apenas as leituras gravadas, mantendo projeto e dispositivos. */
    @DeleteMapping(value = "/{id}/data")
    public ResponseEntity<Void> deleteData(@PathVariable("id") Long id) {
        projectService.deleteData(access.requireProject(id));
        return ResponseEntity.noContent().build();
    }

    // ---- Dispositivos -----------------------------------------------------------

    @GetMapping(value = "/{id}/devices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Device> devices(@PathVariable("id") Long id) {
        return deviceService.list(access.requireProject(id));
    }

    @PostMapping(value = "/{id}/devices", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeviceTokenView addDevice(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> body) {
        Project p = access.requireProject(id);
        if (deviceService.list(p).size() >= ProjectService.MAX_DEVICES) {
            throw ApiException.badRequest("Limite de " + ProjectService.MAX_DEVICES + " dispositivos por projeto");
        }
        return DeviceTokenView.of(deviceService.create(p, body == null ? null : body.get("name")));
    }

    @PutMapping(value = "/{id}/devices/{deviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Device renameDevice(@PathVariable("id") Long id, @PathVariable("deviceId") Long deviceId,
                               @RequestBody Map<String, String> body) {
        Project p = access.requireProject(id);
        return deviceService.rename(deviceService.get(p, deviceId), body.get("name"));
    }

    /** Gera um novo token; o anterior deixa de funcionar imediatamente. */
    @PostMapping(value = "/{id}/devices/{deviceId}/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeviceTokenView regenerateToken(@PathVariable("id") Long id, @PathVariable("deviceId") Long deviceId) {
        Project p = access.requireProject(id);
        return DeviceTokenView.of(deviceService.regenerateToken(deviceService.get(p, deviceId)));
    }

    @DeleteMapping(value = "/{id}/devices/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable("id") Long id, @PathVariable("deviceId") Long deviceId,
                                             @RequestParam(value = "deleteData", defaultValue = "false") boolean deleteData) {
        Project p = access.requireProject(id);
        if (deviceService.list(p).size() <= 1) {
            throw ApiException.conflict("O projeto precisa de pelo menos um dispositivo");
        }
        deviceService.delete(deviceService.get(p, deviceId), deleteData);
        return ResponseEntity.noContent().build();
    }

    // ---- Codigo embarcado -------------------------------------------------------

    @GetMapping(value = "/{id}/code", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CodeGenerator.GeneratedFile> generateCode(@PathVariable("id") Long id,
                                                          @RequestParam("board") CodeGenerator.Board board,
                                                          @RequestParam("protocol") CodeGenerator.Protocol protocol,
                                                          @RequestParam(value = "deviceId", required = false) Long deviceId,
                                                          HttpServletRequest request) {
        Project p = access.requireProject(id);
        List<Device> devices = deviceService.list(p);
        Device device = deviceId == null
                ? devices.stream().findFirst().orElseThrow(() -> ApiException.badRequest("Projeto sem dispositivos"))
                : deviceService.get(p, deviceId);
        String host = publicHost == null || publicHost.isBlank() ? request.getServerName() : publicHost;
        CodeGenerator.Endpoint ep = new CodeGenerator.Endpoint(host, request.getServerPort(), mqttPublicPort);
        return codeGenerator.generate(p, metadataService.findByProjectId(id), device, board, protocol, ep);
    }
}
