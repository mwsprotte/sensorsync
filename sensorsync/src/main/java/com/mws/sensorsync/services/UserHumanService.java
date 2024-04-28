package com.mws.sensorsync.services;

import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.UserHumanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserHumanService {

    @Autowired
    UserHumanRepository userHumanRepository;

    public List<UserHuman> findAll() {
        return userHumanRepository.findAll();
    }

    public UserHuman findById(Long id) {
        return userHumanRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados usuarios correspondente ao ID " + id));
    }

    public UserHuman save(UserHuman userHuman) {
        return userHumanRepository.save(userHuman);
    }

    public UserHuman update(UserHuman userHuman) {
        var entity = userHumanRepository.findById(userHuman.getId()).orElseThrow(() -> new ResourceNotFoundException("Não encontrado um usuario correspondente ao ID " + userHuman.getId()));
        return save(userHuman);
    }

    public void delete(Long id){
        var entity = userHumanRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Não foram encontrados dados correspondente ao ID " + id));
        userHumanRepository.delete(entity);
    }

//    Método para retornar um user a partir do seu login
    public UserHuman getUserByLogin(String login){
        return userHumanRepository.findByLogin(login);
    }

}
