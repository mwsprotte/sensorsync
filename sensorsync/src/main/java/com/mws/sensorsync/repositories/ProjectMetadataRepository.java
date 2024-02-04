package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.ProjectMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMetadataRepository extends JpaRepository<ProjectMetadata, Long> {
}
