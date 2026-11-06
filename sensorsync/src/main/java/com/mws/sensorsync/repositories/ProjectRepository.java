package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerIdOrderById(long ownerId);

    List<Project> findAllByOrderById();

    List<Project> findByOwnerIsNull();

    boolean existsByNameIgnoreCase(String name);

    long countByOwnerId(long ownerId);
}
