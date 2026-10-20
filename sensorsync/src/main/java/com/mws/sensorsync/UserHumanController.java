package com.mws.sensorsync;

import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.security.AccessService;
import com.mws.sensorsync.services.UserHumanService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gerenciamento de usuarios. Somente ADMIN (regra em SecurityConfig). A senha nunca e devolvida. */
@RestController
@RequestMapping("user")
public class UserHumanController {

    private final UserHumanService userHumanService;
    private final AccessService access;

    public UserHumanController(UserHumanService userHumanService, AccessService access) {
        this.userHumanService = userHumanService;
        this.access = access;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UserHuman> findAll() {
        return userHumanService.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman findByID(@PathVariable("id") Long id) {
        return userHumanService.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman create(@RequestBody UserHumanService.UserForm form) {
        return userHumanService.create(form);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman update(@PathVariable("id") Long id, @RequestBody UserHumanService.UserForm form) {
        return userHumanService.update(id, form, access.currentUser());
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userHumanService.delete(id, access.currentUser());
        return ResponseEntity.noContent().build();
    }
}
