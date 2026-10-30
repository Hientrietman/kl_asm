package com.prelude.klasm.dealership.repository;

import java.util.List;
import java.util.Optional;
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

    @Query("select t from TechnicianEntity t left join fetch t.serviceTypeIds where t.id = :id")
    Optional<TechnicianEntity> findWithQualifications(@Param("id") UUID id);

    @Query("""
            select count(t) > 0 from TechnicianEntity t join t.serviceTypeIds s
            where t.dealershipId = :dealershipId and t.active = true and s = :serviceTypeId
            """)
    boolean existsQualified(@Param("dealershipId") UUID dealershipId, @Param("serviceTypeId") UUID serviceTypeId);
}
