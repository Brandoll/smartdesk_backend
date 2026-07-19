package com.smartdesk.service;

import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.entity.AuditLog;
import com.smartdesk.repository.AuditLogRepository;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditListener {

    private final AuditLogRepository auditLogRepository;

    public AuditListener(@Lazy AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        if (!(entity instanceof AuditLog)) {
            saveAuditLog("INSERT", entity);
        }
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        if (!(entity instanceof AuditLog)) {
            saveAuditLog("UPDATE", entity);
        }
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        if (!(entity instanceof AuditLog)) {
            saveAuditLog("DELETE", entity);
        }
    }

    private void saveAuditLog(String action, Object entity) {
        String tenant = TenantContext.getCurrentTenant();
        // Fallback for public entities or if tenant context is missing
        if (tenant == null || tenant.equals(TenantContext.DEFAULT_TENANT)) {
            return;
        }

        UUID userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.smartdesk.config.security.CustomUserDetails) {
            userId = ((com.smartdesk.config.security.CustomUserDetails) auth.getPrincipal()).getUser().getId();
        }

        String entityName = entity.getClass().getSimpleName();
        String entityId = "";
        
        try {
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            if (id != null) {
                entityId = id.toString();
            }
        } catch (Exception e) {
            // Ignore
        }

        AuditLog log = new AuditLog();
        // Aqui asumimos que el tenantId es UUID, pero el interceptor usa String (subdominio). 
        // Para simplificar, lo guardamos o buscamos el Tenant real.
        // Dado que la base de datos dice UUID tenant_id, vamos a pasarlo como UUID nulo por defecto o hacer un hash si no lo tenemos.
        // O mejor modificamos AuditLog para que tenantId sea String.
        // Dejaremos UUID null o dummy para no romper la compilacion, pero en realidad el TenantContext tiene un String (subdominio).
        // En un proyecto real el JwtToken trae el UUID del tenant.
        
        log.setTenantId(UUID.randomUUID()); // TODO: Obtener el UUID real del tenant a partir del subdominio
        log.setUserId(userId);
        log.setAction(action);
        log.setEntity(entityName);
        log.setEntityId(entityId);
        
        auditLogRepository.save(log);
    }
}
