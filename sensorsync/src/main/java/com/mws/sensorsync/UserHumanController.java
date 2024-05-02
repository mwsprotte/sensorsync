package com.mws.sensorsync;


import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.services.UserHumanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("user")
public class UserHumanController {

    @Autowired
    private UserHumanService userHumanService;


//    *********************************************************************************************
//    Endpoints do Crud básico

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UserHuman> findall() {
        return userHumanService.findAll();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman findByID(@PathVariable(value = "id") Long id) {
        return userHumanService.findById(id);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public boolean save(@RequestBody UserHuman user) {
        if (userHumanService.getUserByLogin(user.getLogin()) == null && !user.getPassword().isEmpty()) {
            userHumanService.save(user);
            return true;
        } else {
            return false;
        }
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserHuman update(@RequestBody UserHuman dataPackage) {
        return userHumanService.update(dataPackage);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable(value = "id") Long id) {
        userHumanService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //  *********************************************************************************************
//  Endpoint para checar o login
    @GetMapping(value = "/check/login/{login}/password/{password}", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean checkUser(@PathVariable(value = "login") String login, @PathVariable(value = "password") String password) {
        var entity = userHumanService.getUserByLogin(login);

        try {
            boolean verify = (Objects.equals(entity.getPassword(), password));

            if (verify) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }


    }

}
