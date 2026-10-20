package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.Role;
import com.mws.sensorsync.model.UserHuman;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserHumanRepository extends JpaRepository<UserHuman, Long> {

    Optional<UserHuman> findFirstByLoginOrderByIdDesc(String login);

    List<UserHuman> findAllByOrderById();

    List<UserHuman> findByRoleOrderById(Role role);

    long countByRole(Role role);
}
