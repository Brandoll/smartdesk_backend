-- SmartDesk: tenant empresarial de demostración
-- Empresa: NovaTech Solutions Demo
-- Subdominio/esquema: empresa_demo
-- Contraseña para TODOS los usuarios: SmartDesk2026!
--
-- Ejecutar dentro de la base smartdesk_db:
--   docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < seed_demo_company.sql
--
-- El script aborta sin modificar datos si el tenant, esquema o alguno de los
-- correos de demostración ya existe.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.tenants WHERE subdomain = 'empresa_demo' OR name = 'NovaTech Solutions Demo') THEN
        RAISE EXCEPTION 'El tenant empresa_demo ya existe';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'empresa_demo') THEN
        RAISE EXCEPTION 'El esquema empresa_demo ya existe';
    END IF;
    IF EXISTS (SELECT 1 FROM public.user_global WHERE email LIKE '%@novatech-demo.com') THEN
        RAISE EXCEPTION 'Ya existen usuarios de novatech-demo.com';
    END IF;
END $$;

INSERT INTO public.tenants (id, name, subdomain, is_active, created_at)
VALUES ('de000000-0000-0000-0000-000000000001', 'NovaTech Solutions Demo', 'empresa_demo', true, CURRENT_TIMESTAMP);

CREATE SCHEMA empresa_demo;

CREATE TABLE empresa_demo.users (
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

CREATE TABLE empresa_demo.areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE empresa_demo.user_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES empresa_demo.users(id),
    area_id UUID NOT NULL REFERENCES empresa_demo.areas(id),
    UNIQUE(user_id, area_id)
);

CREATE TABLE empresa_demo.tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ABIERTO',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIA',
    client_id UUID NOT NULL REFERENCES empresa_demo.users(id),
    assigned_to_id UUID REFERENCES empresa_demo.users(id),
    area_id UUID REFERENCES empresa_demo.areas(id),
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

CREATE TABLE empresa_demo.ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES empresa_demo.tickets(id),
    user_id UUID REFERENCES empresa_demo.users(id),
    message TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT false,
    sender_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    sender_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE empresa_demo.ticket_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES empresa_demo.tickets(id),
    user_id UUID REFERENCES empresa_demo.users(id),
    event_type VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE empresa_demo.ticket_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES empresa_demo.tickets(id),
    user_id UUID NOT NULL REFERENCES empresa_demo.users(id),
    file_url VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE empresa_demo.audit_logs (
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

CREATE INDEX idx_demo_tickets_created_at ON empresa_demo.tickets(created_at DESC);
CREATE INDEX idx_demo_tickets_client ON empresa_demo.tickets(client_id);
CREATE INDEX idx_demo_tickets_assigned ON empresa_demo.tickets(assigned_to_id);
CREATE INDEX idx_demo_tickets_area ON empresa_demo.tickets(area_id);
CREATE INDEX idx_demo_messages_ticket ON empresa_demo.ticket_messages(ticket_id);

INSERT INTO empresa_demo.areas (id, name, description) VALUES
('ae000000-0000-0000-0000-000000000001', 'Tecnología', 'Sistemas, accesos, equipos y conectividad'),
('ae000000-0000-0000-0000-000000000002', 'Recursos Humanos', 'Nómina, beneficios y gestión de personas'),
('ae000000-0000-0000-0000-000000000003', 'Finanzas', 'Facturación, pagos y reembolsos'),
('ae000000-0000-0000-0000-000000000004', 'Operaciones', 'Procesos internos, logística y proveedores'),
('ae000000-0000-0000-0000-000000000005', 'Comercial', 'Clientes, ventas y oportunidades');

-- Hash BCrypt de SmartDesk2026! generado con Spring Security BCrypt.
INSERT INTO empresa_demo.users (id, name, email, password, role, status, created_at) VALUES
('ad000000-0000-0000-0000-000000000001', 'Valeria Torres', 'admin@novatech-demo.com', '$2a$10$D.yxGbIKmrojsXrcW2SH5eP6GcAWrzfSDKreb6DeSdMze.4lxqOG6', 'ADMIN_TENANT', 'ACTIVO', NOW() - INTERVAL '180 days'),
('ad000000-0000-0000-0000-000000000002', 'Carlos Mendoza', 'admin2@novatech-demo.com', '$2a$10$D.yxGbIKmrojsXrcW2SH5eP6GcAWrzfSDKreb6DeSdMze.4lxqOG6', 'ADMIN_TENANT', 'ACTIVO', NOW() - INTERVAL '170 days');

INSERT INTO empresa_demo.users (id, name, email, password, role, status, created_at)
SELECT gen_random_uuid(),
       (ARRAY['Lucía Ramos','Diego Castro','Andrea Salazar','Mateo Ruiz','Sofía Herrera','Gabriel Flores','Camila Vega','Joaquín Silva'])[g],
       'resolutor' || g || '@novatech-demo.com',
       '$2a$10$D.yxGbIKmrojsXrcW2SH5eP6GcAWrzfSDKreb6DeSdMze.4lxqOG6',
       'COLABORADOR_RESOLUTOR', 'ACTIVO', NOW() - (g * INTERVAL '12 days')
FROM generate_series(1, 8) AS g;

INSERT INTO empresa_demo.users (id, name, email, password, role, status, created_at)
SELECT gen_random_uuid(),
       (ARRAY['Alejandro Soto','Mariana Paredes','Sebastián Rojas','Daniela Cruz','Nicolás Peña',
              'Fernanda León','Martín Campos','Paula Navarro','Emilio Vargas','Renata Fuentes',
              'Samuel Ortiz','Antonia Reyes','Felipe Acosta','Isabella Núñez','Tomás Medina',
              'Julieta Cabrera','Bruno Espinoza','Catalina Mora','Lorenzo Vidal','Amanda Lozano'])[g],
       'colaborador' || g || '@novatech-demo.com',
       '$2a$10$D.yxGbIKmrojsXrcW2SH5eP6GcAWrzfSDKreb6DeSdMze.4lxqOG6',
       'COLABORADOR',
       CASE WHEN g IN (18, 19, 20) THEN 'INVITADO' ELSE 'ACTIVO' END,
       NOW() - (g * INTERVAL '5 days')
FROM generate_series(1, 20) AS g;

INSERT INTO public.user_global (id, email, tenant_id)
SELECT gen_random_uuid(), email, 'de000000-0000-0000-0000-000000000001'
FROM empresa_demo.users;

-- Cada resolutor recibe un área principal y algunos una segunda área.
WITH resolvers AS (
    SELECT id, row_number() OVER (ORDER BY email) AS rn
    FROM empresa_demo.users WHERE role = 'COLABORADOR_RESOLUTOR'
), area_pool AS (
    SELECT array_agg(id ORDER BY name) AS ids FROM empresa_demo.areas
)
INSERT INTO empresa_demo.user_areas (user_id, area_id)
SELECT r.id, a.ids[1 + ((r.rn - 1) % array_length(a.ids, 1))]
FROM resolvers r CROSS JOIN area_pool a;

WITH resolvers AS (
    SELECT id, row_number() OVER (ORDER BY email) AS rn
    FROM empresa_demo.users WHERE role = 'COLABORADOR_RESOLUTOR'
), area_pool AS (
    SELECT array_agg(id ORDER BY name) AS ids FROM empresa_demo.areas
)
INSERT INTO empresa_demo.user_areas (user_id, area_id)
SELECT r.id, a.ids[1 + (r.rn % array_length(a.ids, 1))]
FROM resolvers r CROSS JOIN area_pool a
WHERE r.rn IN (1, 3, 5);

-- 120 tickets con distribución suficiente para dashboard, filtros y listados.
WITH pools AS (
    SELECT
      array_agg(id ORDER BY email) FILTER (WHERE role = 'COLABORADOR') AS clients,
      array_agg(id ORDER BY email) FILTER (WHERE role = 'COLABORADOR_RESOLUTOR') AS resolvers
    FROM empresa_demo.users
), area_pool AS (
    SELECT array_agg(id ORDER BY name) AS areas FROM empresa_demo.areas
), cases AS (
    SELECT g,
           (ARRAY['ASIGNADO','EN_PROCESO','PROPUESTO','RESUELTO','CERRADO','ABIERTO'])[1 + ((g - 1) % 6)] AS ticket_status,
           (ARRAY['MEDIA','ALTA','BAJA','MEDIA','CRITICA','ALTA','MEDIA','BAJA'])[1 + ((g - 1) % 8)] AS ticket_priority
    FROM generate_series(1, 120) AS g
)
INSERT INTO empresa_demo.tickets (
    id, title, description, status, priority, client_id, assigned_to_id, area_id,
    ai_suggested_title, ai_suggested_area_id, ai_suggested_priority, ai_classified,
    ai_suggested_solution, resolution_comment, rating, rating_comment,
    created_at, updated_at, resolved_at, closed_at
)
SELECT
    gen_random_uuid(),
    (ARRAY[
      'No puedo ingresar al correo corporativo', 'Solicitud de acceso al sistema contable',
      'Equipo portátil con rendimiento lento', 'Consulta sobre pago de nómina',
      'Error al conectarme a la VPN', 'Factura pendiente de aprobación',
      'Actualización de datos de un proveedor', 'Cliente solicita cambio en su contrato',
      'Impresora de oficina sin conexión', 'Solicitud de certificado laboral',
      'Aplicación interna muestra pantalla en blanco', 'Reembolso de gastos aún no recibido'
    ])[1 + ((c.g - 1) % 12)] || ' #' || LPAD(c.g::text, 3, '0'),
    'Caso empresarial de demostración número ' || c.g || '. Incluye información suficiente para probar asignación, seguimiento, prioridades, áreas y métricas del dashboard.',
    c.ticket_status,
    c.ticket_priority,
    p.clients[1 + ((c.g - 1) % array_length(p.clients, 1))],
    CASE WHEN c.ticket_status = 'ABIERTO' OR c.g % 11 = 0 THEN NULL
         ELSE p.resolvers[1 + ((c.g - 1) % array_length(p.resolvers, 1))] END,
    CASE WHEN c.g % 9 = 0 THEN NULL
         ELSE a.areas[1 + ((c.g - 1) % array_length(a.areas, 1))] END,
    'Sugerencia IA para el caso #' || LPAD(c.g::text, 3, '0'),
    CASE WHEN c.g % 9 = 0 THEN a.areas[1] ELSE a.areas[1 + ((c.g - 1) % array_length(a.areas, 1))] END,
    c.ticket_priority,
    true,
    'Validar el contexto con el usuario, aplicar el procedimiento del área responsable y documentar la solución.',
    CASE WHEN c.ticket_status IN ('RESUELTO','CERRADO') THEN 'Caso atendido y solución validada con el colaborador.' END,
    CASE WHEN c.ticket_status IN ('RESUELTO','CERRADO') THEN 3 + (c.g % 3) END,
    CASE WHEN c.ticket_status IN ('RESUELTO','CERRADO') THEN 'Atención registrada para datos de demostración.' END,
    NOW() - (c.g * INTERVAL '6 hours'),
    NOW() - (c.g * INTERVAL '5 hours'),
    CASE WHEN c.ticket_status IN ('RESUELTO','CERRADO') THEN NOW() - (c.g * INTERVAL '6 hours') + INTERVAL '2 hours' END,
    CASE WHEN c.ticket_status = 'CERRADO' THEN NOW() - (c.g * INTERVAL '6 hours') + INTERVAL '4 hours' END
FROM cases c CROSS JOIN pools p CROSS JOIN area_pool a;

INSERT INTO empresa_demo.ticket_history (ticket_id, user_id, event_type, old_value, new_value, timestamp)
SELECT id, client_id, 'CREATED', NULL, status, created_at
FROM empresa_demo.tickets;

INSERT INTO empresa_demo.ticket_messages (ticket_id, user_id, message, is_internal, sender_type, sender_name, created_at)
SELECT t.id, t.client_id, 'Hola, necesito apoyo con este caso. Gracias.', false, 'USER', u.name, t.created_at + INTERVAL '10 minutes'
FROM empresa_demo.tickets t
JOIN empresa_demo.users u ON u.id = t.client_id
WHERE MOD(ABS(HASHTEXT(t.id::text)), 3) = 0;

INSERT INTO empresa_demo.ticket_messages (ticket_id, user_id, message, is_internal, sender_type, sender_name, created_at)
SELECT t.id, t.assigned_to_id, 'Estamos revisando la solicitud y compartiremos una actualización.', false, 'USER', u.name, t.created_at + INTERVAL '45 minutes'
FROM empresa_demo.tickets t
JOIN empresa_demo.users u ON u.id = t.assigned_to_id
WHERE t.assigned_to_id IS NOT NULL AND MOD(ABS(HASHTEXT(t.id::text)), 4) = 0;

COMMIT;

-- Credenciales principales:
-- admin@novatech-demo.com       / SmartDesk2026!
-- admin2@novatech-demo.com      / SmartDesk2026!
-- resolutor1@novatech-demo.com  / SmartDesk2026!
-- colaborador1@novatech-demo.com / SmartDesk2026!
