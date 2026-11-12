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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceService {

    /** Resposta que carrega o token em texto puro: so existe no momento da criacao/regeneracao. */
    public record DeviceWithToken(Device device, String token) {
    }

    /**
     * O que a ingestao precisa saber do dispositivo, guardado em memoria pelo hash do token. Evita uma consulta
     * ao banco (dispositivo + projeto + dono) a cada mensagem. Validade de 60 s; regenerar o token, renomear ou
     * excluir dispositivo/projeto limpa o cache na hora (depois do commit). Tokens invalidos nao sao guardados.
     */
    public static final class TokenInfo {
        public final long deviceId;
        public final long projectId;
        public final long deviceIndex;
        public final long dataNumber;
        public final String name;
        final long loadedAt;
        volatile LocalDateTime lastSeenAt;

        TokenInfo(Device d, long loadedAt) {
            this.deviceId = d.getId();
            this.projectId = d.getProject().getId();
            this.deviceIndex = d.getDeviceIndex();
            this.dataNumber = d.getProject().getDataNumber();
            this.name = d.getName();
            this.lastSeenAt = d.getLastSeenAt();
            this.loadedAt = loadedAt;
        }
    }

    private static final long TOKEN_CACHE_TTL_MS = 60_000;
    private static final int TOKEN_CACHE_MAX = 10_000;
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

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
        invalidateTokenCache();
        return new DeviceWithToken(devices.save(d), token);
    }

    @Transactional
    public Device rename(Device d, String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Nome vazio");
        }
        d.setName(name.trim());
        invalidateTokenCache();
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
        invalidateTokenCache();
        syncCount(p);
    }

    public Optional<Device> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return devices.findByTokenHash(tokens.hash(token));
    }

    /** Dispositivo do token, pelo cache (ou pelo banco, se nao estiver no cache ou tiver vencido). */
    public Optional<TokenInfo> lookupToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String hash = tokens.hash(token);
        long now = System.currentTimeMillis();
        TokenInfo t = tokenCache.get(hash);
        if (t != null && now - t.loadedAt < TOKEN_CACHE_TTL_MS) {
            return Optional.of(t);
        }
        Optional<Device> d = devices.findByTokenHash(hash);
        if (d.isEmpty()) {
            tokenCache.remove(hash);
            return Optional.empty();
        }
        if (tokenCache.size() >= TOKEN_CACHE_MAX) {
            tokenCache.clear();
        }
        TokenInfo fresh = new TokenInfo(d.get(), now);
        tokenCache.put(hash, fresh);
        return Optional.of(fresh);
    }

    /** Atualiza o "visto por ultimo", no maximo a cada 30 s por dispositivo (evita uma escrita a cada pacote). */
    @Transactional
    public void touch(TokenInfo t) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = t.lastSeenAt;
        if (last == null || last.isBefore(now.minusSeconds(30))) {
            t.lastSeenAt = now;
            devices.updateLastSeen(t.deviceId, now);
        }
    }

    /** Esquece os tokens em memoria (agora e de novo depois do commit, se houver transacao em andamento). */
    public void invalidateTokenCache() {
        tokenCache.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    tokenCache.clear();
                }
            });
        }
    }

    private void syncCount(Project p) {
        p.setSensorNumber(devices.countByProjectId(p.getId()));
        projects.save(p);
    }
}
