package com.mws.sensorsync.repositories;

import com.mws.sensorsync.model.DataPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataPackageRepository extends JpaRepository<DataPackage, Long> {



}
