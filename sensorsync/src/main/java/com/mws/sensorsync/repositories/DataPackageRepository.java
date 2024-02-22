package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.DataPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataPackageRepository extends JpaRepository<DataPackage, Long> {

    @Query(value = "SELECT * FROM datapackage WHERE projectid = :projectid", nativeQuery = true)
    List<DataPackage> findByProject(@Param("projectid") Long projectId);

    @Query(value = "SELECT * FROM datapackage WHERE projectid = :projectid and node_index = :nodeid", nativeQuery = true)
    List<DataPackage> findByProjectAndNode(@Param("projectid") Long projectId, @Param("nodeid") Long nodeId);

    @Query(value = "SELECT * FROM datapackage WHERE projectid = :projectid and node_index = :nodeid order by id DESC LIMIT 0, 1", nativeQuery = true)
    DataPackage findByProjectAndNodeLast(@Param("projectid") Long projectId, @Param("nodeid") Long nodeId);


    @Query(value = "DELETE FROM datapackage WHERE projectid = :projectid", nativeQuery = true)
    DataPackage deleteAlLByProjectID(@Param("projectid") Long projectId);

}
