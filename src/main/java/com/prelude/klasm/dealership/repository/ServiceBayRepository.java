package com.prelude.klasm.dealership.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceBayRepository extends JpaRepository<ServiceBayEntity, UUID> {

    List<ServiceBayEntity> findByDealershipIdAndActiveTrueOrderByCode(UUID dealershipId);
}
