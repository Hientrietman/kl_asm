package com.prelude.klasm.catalog.service;

import com.prelude.klasm.catalog.repository.ServiceTypeEntity;
import com.prelude.klasm.catalog.repository.ServiceTypeRepository;
import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public ServiceTypeView getActive(UUID serviceTypeId) {
        return serviceTypes
                .findById(serviceTypeId)
                .filter(ServiceTypeEntity::isActive)
                .map(ServiceTypeService::toView)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, "Service type " + serviceTypeId + " not found."));
    }

    private static ServiceTypeView toView(ServiceTypeEntity entity) {
        return new ServiceTypeView(entity.getId(), entity.getCode(), entity.getName(), entity.getDurationMinutes());
    }
}
