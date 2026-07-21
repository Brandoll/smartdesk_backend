package com.smartdesk.service;

import com.smartdesk.config.security.CustomUserDetails;
import com.smartdesk.config.security.JwtUtil;
import com.smartdesk.config.tenant.TenantContext;
import com.smartdesk.model.dto.ActivationDTO;
import com.smartdesk.model.dto.AuthResponseDTO;
import com.smartdesk.model.dto.InitRegistrationDTO;
import com.smartdesk.model.dto.LoginDTO;
import com.smartdesk.model.entity.Tenant;
import com.smartdesk.model.entity.User;
import com.smartdesk.model.entity.UserGlobal;
import com.smartdesk.model.entity.VerificationToken;
import com.smartdesk.repository.TenantRepository;
import com.smartdesk.repository.UserGlobalRepository;
import com.smartdesk.repository.UserRepository;
import com.smartdesk.repository.VerificationTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuthService {

    private final UserGlobalRepository userGlobalRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailNotificationService emailNotificationService;
    private final JdbcTemplate jdbcTemplate;

    // Password: min 8 chars, at least 1 uppercase, 1 number, 1 symbol
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$"
    );

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public AuthService(UserGlobalRepository userGlobalRepository, UserRepository userRepository,
                       TenantRepository tenantRepository, VerificationTokenRepository verificationTokenRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       EmailNotificationService emailNotificationService, JdbcTemplate jdbcTemplate) {
        this.userGlobalRepository = userGlobalRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailNotificationService = emailNotificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthResponseDTO login(LoginDTO loginDTO) {
        // 1. Find UserGlobal in public schema
        TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);
        UserGlobal userGlobal = userGlobalRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        Tenant tenant = tenantRepository.findById(userGlobal.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        if (!tenant.getIsActive()) {
            throw new RuntimeException("El entorno de la empresa no está activo");
        }

        // 2. Switch to tenant schema and validate user credentials
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        // Check if account is locked
        if (user.isAccountLocked()) {
            throw new RuntimeException("Cuenta bloqueada temporalmente. Intente nuevamente en 15 minutos.");
        }

        // Check if user is suspended
        if (user.getStatus() == User.Status.SUSPENDIDO) {
            throw new RuntimeException("Su cuenta ha sido suspendida. Contacte al administrador.");
        }

        // Validate password
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            // Increment failed attempts
            int attempts = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                userRepository.save(user);
                throw new RuntimeException("Cuenta bloqueada temporalmente. Intente nuevamente en 15 minutos.");
            }

            userRepository.save(user);
            throw new RuntimeException("Credenciales inválidas. Intentos restantes: " + (MAX_FAILED_ATTEMPTS - attempts));
        }

        // Successful login - reset failed attempts
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 3. Generate JWT
        String token = jwtUtil.generateToken(new CustomUserDetails(user), tenant.getSubdomain());

        return AuthResponseDTO.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(tenant.getSubdomain())
                .companyName(tenant.getName())
                .build();
    }

    @Transactional
    public void initRegistration(InitRegistrationDTO dto) {
        // Validate password strength
        validatePassword(dto.getPassword());

        TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);

        if (userGlobalRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está registrado");
        }

        // Generate 6 digit code
        String shortCode = String.format("%06d", new java.util.Random().nextInt(1000000));

        // Create verification token
        VerificationToken token = VerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .shortCode(shortCode)
                .email(dto.getEmail())
                .name(dto.getName())
                .password(passwordEncoder.encode(dto.getPassword()))
                .type(VerificationToken.TokenType.REGISTRATION)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        verificationTokenRepository.save(token);

        // Send verification email
        User tempUser = new User();
        tempUser.setName(dto.getName());
        tempUser.setEmail(dto.getEmail());
        emailNotificationService.sendVerificationEmail(tempUser, token);
    }

    @Transactional
    public AuthResponseDTO activateTenant(ActivationDTO dto) {
        TenantContext.setCurrentTenant(TenantContext.DEFAULT_TENANT);

        VerificationToken token = verificationTokenRepository.findByTokenOrShortCode(dto.getToken(), dto.getToken())
                .orElseThrow(() -> new RuntimeException("Token o código de verificación inválido"));

        if (token.getUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token ha expirado o ya fue usado");
        }

        // 1. Create Tenant in public
        String subdomain = dto.getCompanyName().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (tenantRepository.findBySubdomain(subdomain).isPresent()) {
            subdomain = subdomain + UUID.randomUUID().toString().substring(0, 4);
        }

        Tenant tenant = Tenant.builder()
                .name(dto.getCompanyName())
                .subdomain(subdomain)
                .isActive(true)
                .build();
        tenant = tenantRepository.save(tenant);

        // 2. Create UserGlobal
        UserGlobal userGlobal = UserGlobal.builder()
                .email(token.getEmail())
                .tenantId(tenant.getId())
                .build();
        userGlobalRepository.save(userGlobal);

        // 3. Create schema
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + subdomain + "\"");

        // 4. Create tables
        createTenantSchemaTables(subdomain);

        // 5. Create admin user via JdbcTemplate (bypasses Hibernate schema issues)
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO \"" + subdomain + "\".users (id, name, email, password, role, status, failed_attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            adminId,
            token.getName(),
            token.getEmail(),
            token.getPassword(),
            User.Role.ADMIN_TENANT.name(),
            User.Status.ACTIVO.name(),
            0,
            java.sql.Timestamp.valueOf(LocalDateTime.now())
        );

        // 6. Mark token as used
        token.setUsed(true);
        verificationTokenRepository.save(token);

        // 7. Build user object for JWT generation
        User adminUser = User.builder()
                .id(adminId)
                .name(token.getName())
                .email(token.getEmail())
                .password(token.getPassword())
                .role(User.Role.ADMIN_TENANT)
                .status(User.Status.ACTIVO)
                .build();

        String jwt = jwtUtil.generateToken(new CustomUserDetails(adminUser), subdomain);

        return AuthResponseDTO.builder()
                .token(jwt)
                .userId(adminId)
                .name(adminUser.getName())
                .email(adminUser.getEmail())
                .role(adminUser.getRole().name())
                .tenantId(subdomain)
                .companyName(tenant.getName())
                .build();
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new RuntimeException("La contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo");
        }
    }

    private void createTenantSchemaTables(String schema) {
        String sql = String.format("""
            CREATE TABLE "%s".users (
                id UUID PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                role VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'ACTIVO',
                failed_attempts INTEGER DEFAULT 0,
                locked_until TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE "%s".areas (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(255) NOT NULL UNIQUE,
                description VARCHAR(500),
                active BOOLEAN NOT NULL DEFAULT true
            );

            CREATE TABLE "%s".user_areas (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID NOT NULL REFERENCES "%s".users(id),
                area_id UUID NOT NULL REFERENCES "%s".areas(id),
                UNIQUE(user_id, area_id)
            );

            CREATE TABLE "%s".tickets (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                title VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'ABIERTO',
                priority VARCHAR(50) NOT NULL DEFAULT 'MEDIA',
                client_id UUID NOT NULL REFERENCES "%s".users(id),
                assigned_to_id UUID REFERENCES "%s".users(id),
                area_id UUID REFERENCES "%s".areas(id),
                ai_suggested_title VARCHAR(255),
                ai_suggested_area_id UUID,
                ai_suggested_priority VARCHAR(50),
                ai_classified BOOLEAN DEFAULT false,
                ai_suggested_solution TEXT,
                resolution_comment TEXT,
                rating INTEGER CHECK (rating >= 1 AND rating <= 5),
                rating_comment TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                resolved_at TIMESTAMP,
                closed_at TIMESTAMP
            );

            CREATE TABLE "%s".ticket_messages (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                ticket_id UUID NOT NULL REFERENCES "%s".tickets(id),
                user_id UUID REFERENCES "%s".users(id),
                message TEXT NOT NULL,
                is_internal BOOLEAN NOT NULL DEFAULT false,
                sender_type VARCHAR(20) NOT NULL DEFAULT 'USER',
                sender_name VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE "%s".ticket_history (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                ticket_id UUID NOT NULL REFERENCES "%s".tickets(id),
                user_id UUID REFERENCES "%s".users(id),
                event_type VARCHAR(50) NOT NULL,
                old_value TEXT,
                new_value TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE "%s".ticket_attachments (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                ticket_id UUID NOT NULL REFERENCES "%s".tickets(id),
                user_id UUID NOT NULL REFERENCES "%s".users(id),
                file_url VARCHAR(500) NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                file_type VARCHAR(50) NOT NULL,
                file_size BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE "%s".audit_logs (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                tenant_id UUID,
                user_id UUID,
                action VARCHAR(255) NOT NULL,
                entity VARCHAR(255) NOT NULL,
                entity_id VARCHAR(255),
                old_value TEXT,
                new_value TEXT,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema);

        jdbcTemplate.execute(sql);
    }
}
