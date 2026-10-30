package com.prelude.klasm.customer.service;

import com.prelude.klasm.customer.repository.CustomerVehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the customer module. Resolution is idempotent and never modifies existing records (A-13). */
@Service
public class CustomerService {

    private final CustomerVehicleRepository repository;

    public CustomerService(CustomerVehicleRepository repository) {
        this.repository = repository;
    }

    /** Must join the caller's booking transaction so resolution and insert succeed or fail together (FR-3.3). */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID resolveCustomer(String fullName, String email, String phone) {
        return repository.findOrCreateCustomer(fullName, email, phone);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID resolveVehicle(String vin, String make, String model, Integer year) {
        return repository.findOrCreateVehicle(vin, make, model, year);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findVehicleIdByVin(String vin) {
        return repository.findVehicleIdByVin(vin);
    }
}
