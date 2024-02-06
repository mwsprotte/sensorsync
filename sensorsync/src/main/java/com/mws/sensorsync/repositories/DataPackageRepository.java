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

//    @Query(value = "SELECT * FROM product_ttp WHERE productid = :productID", nativeQuery = true)
//    List<ProductTTP> findByProductID(@Param("productID") String pot);

}
