package com.prelude.klasm.dealership.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DealershipRepository extends JpaRepository<DealershipEntity, UUID> {

    @Query(
            "select distinct d from DealershipEntity d left join fetch d.workingHours where d.active = true order by d.name")
    List<DealershipEntity> findActiveWithWorkingHours();

    boolean existsByIdAndActiveTrue(UUID id);
}
