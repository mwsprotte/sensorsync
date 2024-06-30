package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DataRepository extends JpaRepository<Data, Long> {

    //QUERY para resgatar a última gravação de um dado a partir do projeto, seu sensor e seu index
    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex and data_index = :dataIndex order by id DESC LIMIT 1", nativeQuery = true)
    Data findByProjectAndNodeForCard(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex, @Param("dataIndex") Long dataIndex);


    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex and data_index = :dataIndex order by id DESC LIMIT :length", nativeQuery = true)
    List<Data> findByProjectAndDeviceForChart(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex, @Param("dataIndex") Long dataIndex, @Param("length") Long length);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM data WHERE projectid = :projectid", nativeQuery = true)
    void deleteAlLByProjectID(@Param("projectid") int projectId);

    //QUERY para resgatar a última gravação de um dado a partir do projeto, seu sensor e seu index
    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex and data_index = :dataIndex order by id DESC", nativeQuery = true)
    List<Data> findAllByProjectAndDeviceAndIndex(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex, @Param("dataIndex") Long dataIndex);

    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex order by id DESC", nativeQuery = true)
    List<Data> findAllByProjectAndDeviceAndIndex(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex);


    @Query(value = "SELECT * FROM data WHERE projectid = :projectid order by id DESC LIMIT :length", nativeQuery = true)
    List<Data> findByProject(@Param("projectid") Long projectId, @Param("length") Long length);

    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex order by id DESC LIMIT :length", nativeQuery = true)
    List<Data> findByProjectIDandDecvice(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex, @Param("length") Long length);

    @Query(value = "SELECT * FROM data WHERE projectid = :projectid and sensor_index = :sensorIndex and data_index = :dataIndex order by id DESC LIMIT :length", nativeQuery = true)
    List<Data> findByProjectIDandDecviceandData(@Param("projectid") Long projectId, @Param("sensorIndex") Long sensorIndex, @Param("dataIndex") Long dataIndex, @Param("length") Long length);
}
