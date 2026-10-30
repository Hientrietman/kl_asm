package com.prelude.klasm.catalog.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceTypeEntity, UUID> {

    List<ServiceTypeEntity> findByActiveTrueOrderByCode();
}
