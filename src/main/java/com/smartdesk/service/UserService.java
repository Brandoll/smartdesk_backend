package com.smartdesk.service;

import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.dto.UserDTO;
import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.UserArea;
import com.smartdesk.repository.TenantRepository;
import com.smartdesk.repository.UserAreaRepository;
import com.smartdesk.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserAreaRepository userAreaRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserRepository userRepository, UserAreaRepository userAreaRepository,
                       PasswordEncoder passwordEncoder, TenantRepository tenantRepository,
                       JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.userAreaRepository = userAreaRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToDTO);
    }

    public UserDTO getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO createUser(UserDTO dto, String password) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está registrado en este tenant");
        }

        // Validate password
        if (password == null || password.length() < 8) {
            throw new RuntimeException("La contraseña debe tener al menos 8 caracteres");
        }

        String currentTenant = TenantContext.getCurrentTenant();

        // 1. Create user in tenant schema
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(dto.getRole() != null ? dto.getRole() : User.Role.COLABORADOR);
        user.setStatus(User.Status.INVITADO); // Always starts as INVITADO
        user.setFailedAttempts(0);

        user = userRepository.save(user);

        // 2. Assign areas if provided
        if (dto.getAreaIds() != null) {
            for (UUID areaId : dto.getAreaIds()) {
                UserArea userArea = UserArea.builder()
                        .userId(user.getId())
                        .areaId(areaId)
                        .build();
                userAreaRepository.save(userArea);
            }
        }

        // 3. Register in public.user_global via JdbcTemplate (reliable, bypasses Hibernate context)
        try {
            var tenant = tenantRepository.findBySubdomain(currentTenant);
            if (tenant.isPresent()) {
                jdbcTemplate.update(
                    "INSERT INTO public.user_global (id, email, tenant_id) VALUES (?, ?, ?)",
                    UUID.randomUUID(),
                    dto.getEmail(),
                    tenant.get().getId()
                );
                log.info("User {} registered in public.user_global for tenant {}", dto.getEmail(), currentTenant);
            }
        } catch (Exception e) {
            log.error("Error registering user in user_global: {}", e.getMessage());
            // Don't fail the whole operation - user is created in tenant
        }

        return mapToDTO(user);
    }

    @Transactional
    public UserDTO updateUser(UUID id, UserDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setName(dto.getName());

        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }

        // Update areas
        if (dto.getAreaIds() != null) {
            userAreaRepository.deleteByUserId(id);
            for (UUID areaId : dto.getAreaIds()) {
                UserArea userArea = UserArea.builder()
                        .userId(id)
                        .areaId(areaId)
                        .build();
                userAreaRepository.save(userArea);
            }
        }

        user = userRepository.save(user);
        return mapToDTO(user);
    }

    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());

        // Load areas
        try {
            List<UserArea> userAreas = userAreaRepository.findByUserId(user.getId());
            dto.setAreaIds(userAreas.stream().map(UserArea::getAreaId).collect(Collectors.toList()));
        } catch (Exception e) {
            dto.setAreaIds(Collections.emptyList());
        }

        return dto;
    }
}
