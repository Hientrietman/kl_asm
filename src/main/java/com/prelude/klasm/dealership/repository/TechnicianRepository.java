package com.prelude.klasm.dealership.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnicianRepository extends JpaRepository<TechnicianEntity, UUID> {

    @Query("""
            select distinct t from TechnicianEntity t left join fetch t.serviceTypeIds
            where t.dealershipId = :dealershipId and t.active = true
            order by t.fullName
            """)
    List<TechnicianEntity> findActiveWithQualifications(@Param("dealershipId") UUID dealershipId);
}
