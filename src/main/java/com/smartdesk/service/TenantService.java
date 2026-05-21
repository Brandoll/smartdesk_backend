package com.smartdesk.service;

import com.smartdesk.model.entity.Tenant;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Service
public class TenantService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Tenant createSandboxTenant(String companyName) {
        Tenant tenant = Tenant.builder()
                .name(companyName)
                .planType("SANDBOX")
                .build();
        entityManager.persist(tenant);
        return tenant;
    }
}
