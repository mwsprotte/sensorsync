package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.model.Metadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MetadataRepository extends JpaRepository<Metadata,Long> {

    @Query(value = "SELECT * FROM metadata WHERE projectid = :projectid and data_index = :dataIndex order by id DESC LIMIT 0, 1", nativeQuery = true)
    Metadata findByProjectAndIndexForCard(@Param("projectid") Long projectId, @Param("dataIndex") Long dataIndex);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM metadata WHERE projectid = :projectid", nativeQuery = true)
    void deleteAlLByProjectID(@Param("projectid") int projectId);
}
