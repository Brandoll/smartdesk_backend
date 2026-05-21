package com.smartdesk.service;

import com.smartdesk.model.entity.Tenant;
import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.UserTenantRole;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Service
public class RoleAssignmentService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void assignAdminRole(User user, Tenant tenant) {
        UserTenantRole role = UserTenantRole.builder()
                .user(user)
                .tenant(tenant)
                .role("ADMINISTRADOR")
                .build();
        entityManager.persist(role);
    }
}
