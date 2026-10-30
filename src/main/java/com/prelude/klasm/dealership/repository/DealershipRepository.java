package com.prelude.klasm.dealership.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealershipRepository extends JpaRepository<DealershipEntity, UUID> {

    @Query(
            "select distinct d from DealershipEntity d left join fetch d.workingHours where d.active = true order by d.name")
    List<DealershipEntity> findActiveWithWorkingHours();

    @Query("select d from DealershipEntity d left join fetch d.workingHours where d.id = :id and d.active = true")
    Optional<DealershipEntity> findActiveWithWorkingHours(@Param("id") UUID id);

    boolean existsByIdAndActiveTrue(UUID id);
}
