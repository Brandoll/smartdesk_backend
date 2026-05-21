package com.smartdesk.service;

import com.smartdesk.exception.EmailAlreadyExistsException;
import com.smartdesk.mapper.UserMapper;
import com.smartdesk.model.dto.ActivationDTO;
import com.smartdesk.model.dto.InitRegistrationDTO;
import com.smartdesk.model.dto.UserResponseDTO;
import com.smartdesk.model.entity.Tenant;
import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.VerificationToken;
import com.smartdesk.repository.UserRepository;
import com.smartdesk.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final RoleAssignmentService roleAssignmentService;
    private final EmailNotificationService emailNotificationService;

    @Transactional
    public void initRegistration(InitRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("El correo electrónico ya está registrado");
        }

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setIsActive(false); // Usuario inactivo inicialmente
        
        user = userRepository.save(user);

        // Generar token (24h validez)
        VerificationToken verificationToken = VerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .build();
        
        tokenRepository.save(verificationToken);

        // Enviar email
        emailNotificationService.sendVerificationEmail(user, verificationToken);
    }

    @Transactional
    public UserResponseDTO activateTenant(ActivationDTO dto) {
        VerificationToken token = tokenRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o no encontrado"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El token ha expirado");
        }

        User user = token.getUser();
        if (user.getIsActive()) {
            throw new IllegalArgumentException("El usuario ya está activo");
        }

        // Activar usuario
        user.setIsActive(true);
        userRepository.save(user);

        // Crear Tenant
        Tenant tenant = tenantService.createSandboxTenant(dto.getCompanyName());

        // Asignar Rol Administrador
        roleAssignmentService.assignAdminRole(user, tenant);

        // Opcional: Eliminar o marcar token como usado
        tokenRepository.delete(token);

        return userMapper.toResponseDto(user);
    }
}
