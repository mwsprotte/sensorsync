package com.mws.sensorsync;

import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.security.AccessService;
import com.mws.sensorsync.services.UserHumanService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Informacoes e senha do proprio usuario logado. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccessService access;
    private final UserHumanService users;

    public MeController(AccessService access, UserHumanService users) {
        this.access = access;
        this.users = users;
    }

    public record PasswordChange(String currentPassword, String newPassword) {
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman me() {
        return access.currentUser();
    }

    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChange body) {
        users.changeOwnPassword(access.currentUser(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }
}
