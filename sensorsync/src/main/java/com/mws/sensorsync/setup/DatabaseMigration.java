package com.mws.sensorsync.setup;

import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.model.Role;
import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.DataRepository;
import com.mws.sensorsync.repositories.DeviceRepository;
import com.mws.sensorsync.repositories.ProjectRepository;
import com.mws.sensorsync.repositories.UserHumanRepository;
import com.mws.sensorsync.services.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.TreeSet;

/**
 * Roda a cada inicializacao e e idempotente. Converte um banco da versao 0.x:
 * 1. Senhas em texto puro viram hash BCrypt (os usuarios continuam entrando com a mesma senha).
 * 2. Usuarios sem papel viram ADMIN (na versao antiga todo usuario tinha acesso de administrador).
 * 3. Banco sem nenhum usuario: cria o admin inicial (senha de SENSORSYNC_ADMIN_PASSWORD ou aleatoria, mostrada no log).
 * 4. Projetos sem dono passam para o primeiro administrador.
 * 5. Projetos sem dispositivos cadastrados ganham um dispositivo por indice ja usado,
 *    SEM token (o dono gera o token pela tela "Dispositivos").
 */
@Component
public class DatabaseMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final UserHumanRepository users;
    private final ProjectRepository projects;
    private final DeviceRepository devices;
    private final DataRepository data;
    private final DeviceService deviceService;
    private final PasswordEncoder encoder;
    private final String adminLogin;
    private final String adminPassword;

    public DatabaseMigration(UserHumanRepository users, ProjectRepository projects, DeviceRepository devices,
                             DataRepository data, DeviceService deviceService, PasswordEncoder encoder,
                             @Value("${sensorsync.bootstrap.admin-login:admin}") String adminLogin,
                             @Value("${sensorsync.bootstrap.admin-password:}") String adminPassword) {
        this.users = users;
        this.projects = projects;
        this.devices = devices;
        this.data = data;
        this.deviceService = deviceService;
        this.encoder = encoder;
        this.adminLogin = adminLogin;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateUsers();
        ensureAdmin();
        assignOrphanProjects();
        createMissingDevices();
    }

    private void migrateUsers() {
        int hashed = 0, promoted = 0;
        for (UserHuman u : users.findAll()) {
            boolean changed = false;
            String pw = u.getPassword();
            if (pw != null && !pw.isEmpty() && !isBcrypt(pw)) {
                u.setPassword(encoder.encode(pw));
                hashed++;
                changed = true;
            }
            if (u.getRawRole() == null) {
                u.setRole(Role.ADMIN);
                promoted++;
                changed = true;
            }
            if (changed) {
                users.save(u);
            }
        }
        if (hashed + promoted > 0) {
            log.info("Migracao: {} senha(s) convertidas para BCrypt, {} usuario(s) antigos definidos como ADMIN", hashed, promoted);
        }
    }

    private void ensureAdmin() {
        if (users.count() > 0) {
            return;
        }
        String password = adminPassword;
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = randomPassword();
        }
        UserHuman u = new UserHuman();
        u.setLogin(adminLogin);
        u.setFirstName("Administrador");
        u.setPassword(encoder.encode(password));
        u.setRole(Role.ADMIN);
        u.setEnabled(true);
        users.save(u);
        if (generated) {
            log.warn("""

                    ==============================================================
                     Usuario administrador criado: login '{}'  senha '{}'
                     Troque a senha no primeiro acesso (menu do usuario > Alterar senha).
                    ==============================================================""", adminLogin, password);
        } else {
            log.info("Usuario administrador '{}' criado com a senha de SENSORSYNC_ADMIN_PASSWORD", adminLogin);
        }
    }

    private void assignOrphanProjects() {
        List<Project> orphans = projects.findByOwnerIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        List<UserHuman> admins = users.findByRoleOrderById(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        UserHuman owner = admins.get(0);
        for (Project p : orphans) {
            p.setOwner(owner);
            projects.save(p);
        }
        log.info("Migracao: {} projeto(s) sem dono atribuidos a '{}'", orphans.size(), owner.getLogin());
    }

    private void createMissingDevices() {
        int created = 0;
        for (Project p : projects.findAll()) {
            if (devices.countByProjectId(p.getId()) > 0) {
                continue;
            }
            TreeSet<Long> indexes = new TreeSet<>(data.findDistinctDeviceIndexes(p.getId()));
            for (long i = 0; i < Math.max(p.getSensorNumber(), 1); i++) {
                indexes.add(i);
            }
            for (Long i : indexes) {
                deviceService.createWithoutToken(p, i);
                created++;
            }
        }
        if (created > 0) {
            log.info("Migracao: {} dispositivo(s) criados sem token; gere os tokens na tela Dispositivos de cada projeto", created);
        }
    }

    private static boolean isBcrypt(String s) {
        return s.length() == 60 && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
