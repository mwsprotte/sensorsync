package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByTokenHash(String tokenHash);

    List<Device> findByProjectIdOrderByDeviceIndex(long projectId);

    Optional<Device> findByProjectIdAndDeviceIndex(long projectId, long deviceIndex);

    long countByProjectId(long projectId);

    @Query("SELECT COALESCE(MAX(d.deviceIndex), -1) FROM Device d WHERE d.project.id = :projectId")
    long maxIndex(@Param("projectId") long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Device d WHERE d.project.id = :projectId")
    void deleteAllByProject(@Param("projectId") long projectId);
}
