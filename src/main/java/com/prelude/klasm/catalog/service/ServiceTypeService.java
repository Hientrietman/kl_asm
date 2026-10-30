package com.prelude.klasm.catalog.service;

import com.prelude.klasm.catalog.repository.ServiceTypeEntity;
import com.prelude.klasm.catalog.repository.ServiceTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the catalog module. */
@Service
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypes;

    public ServiceTypeService(ServiceTypeRepository serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeView> listActive() {
        return serviceTypes.findByActiveTrueOrderByCode().stream()
                .map(ServiceTypeService::toView)
                .toList();
    }

    private static ServiceTypeView toView(ServiceTypeEntity entity) {
        return new ServiceTypeView(entity.getId(), entity.getCode(), entity.getName(), entity.getDurationMinutes());
    }
}
