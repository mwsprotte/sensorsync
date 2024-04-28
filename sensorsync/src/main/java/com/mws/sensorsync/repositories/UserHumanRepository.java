package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.UserHuman;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserHumanRepository extends JpaRepository<UserHuman,Long> {

    @Query(value = "SELECT * FROM user_human WHERE login = :login order by id DESC LIMIT 1", nativeQuery = true)
    UserHuman findByLogin(@Param("login") String login);

}
