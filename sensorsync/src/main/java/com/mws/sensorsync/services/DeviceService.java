package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.Device;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.repositories.DataRepository;
import com.mws.sensorsync.repositories.DeviceRepository;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.security.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    /** Resposta que carrega o token em texto puro: so existe no momento da criacao/regeneracao. */
    public record DeviceWithToken(Device device, String token) {
    }

    private final DeviceRepository devices;
    private final ProjectRepository projects;
    private final DataRepository data;
    private final TokenService tokens;

    public DeviceService(DeviceRepository devices, ProjectRepository projects, DataRepository data, TokenService tokens) {
        this.devices = devices;
        this.projects = projects;
        this.data = data;
        this.tokens = tokens;
    }

    public List<Device> list(Project p) {
        return devices.findByProjectIdOrderByDeviceIndex(p.getId());
    }

    public Device get(Project p, long deviceId) {
        Device d = devices.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispositivo nao encontrado"));
        if (d.getProject().getId() != p.getId()) {
            throw new ResourceNotFoundException("Dispositivo nao encontrado");
        }
        return d;
    }

    @Transactional
    public DeviceWithToken create(Project p, String name) {
        long index = devices.maxIndex(p.getId()) + 1;
        Device d = new Device();
        d.setProject(p);
        d.setDeviceIndex(index);
        d.setName(name == null || name.isBlank() ? "Dispositivo " + index : name.trim());
        d.setCreatedAt(LocalDateTime.now());
        String token = tokens.newToken();
        d.setTokenHash(tokens.hash(token));
        d.setTokenPrefix(tokens.displayPrefix(token));
        d = devices.save(d);
        syncCount(p);
        return new DeviceWithToken(d, token);
    }

    /** Cria um dispositivo sem token (usado pela migracao de bancos antigos). */
    @Transactional
    public Device createWithoutToken(Project p, long index) {
        Device d = new Device();
        d.setProject(p);
        d.setDeviceIndex(index);
        d.setName("Dispositivo " + index);
        d.setCreatedAt(LocalDateTime.now());
        d = devices.save(d);
        syncCount(p);
        return d;
    }

    @Transactional
    public DeviceWithToken regenerateToken(Device d) {
        String token = tokens.newToken();
        d.setTokenHash(tokens.hash(token));
        d.setTokenPrefix(tokens.displayPrefix(token));
        return new DeviceWithToken(devices.save(d), token);
    }

    @Transactional
    public Device rename(Device d, String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Nome vazio");
        }
        d.setName(name.trim());
        return devices.save(d);
    }

    @Transactional
    public void delete(Device d, boolean deleteData) {
        Project p = d.getProject();
        if (deleteData) {
            data.deleteAllByProjectAndDevice(p.getId(), d.getDeviceIndex());
        }
        devices.delete(d);
        devices.flush();
        syncCount(p);
    }

    public Optional<Device> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return devices.findByTokenHash(tokens.hash(token));
    }

    @Transactional
    public void touch(Device d) {
        LocalDateTime now = LocalDateTime.now();
        // Evita uma escrita extra a cada pacote: atualiza no maximo a cada 30 s
        if (d.getLastSeenAt() == null || d.getLastSeenAt().isBefore(now.minusSeconds(30))) {
            d.setLastSeenAt(now);
            devices.save(d);
        }
    }

    private void syncCount(Project p) {
        p.setSensorNumber(devices.countByProjectId(p.getId()));
        projects.save(p);
    }
}
