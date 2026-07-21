#!/bin/bash

echo "=========================================="
echo "🚀 Iniciando Despliegue de Smartdesk Backend"
echo "=========================================="

# Comprobar si openssl está instalado para generar claves
if ! command -v openssl &> /dev/null
then
    echo "openssl no está instalado. Instalándolo..."
    sudo apt-get update && sudo apt-get install -y openssl
fi

echo "Generando credenciales seguras..."
# Generar contraseñas aleatorias seguras
DB_PASSWORD=$(openssl rand -base64 16)
JWT_SECRET=$(openssl rand -base64 48)

# Configurar dominios permitidos (CORS)
# Cambia 'https://smartdeskcloud.com' por tu dominio frontend principal
ALLOWED_ORIGINS="https://smartdeskcloud.com,https://www.smartdeskcloud.com"

echo "Creando archivo .env..."
cat <<EOF > .env
# Configuración de Base de Datos
DATABASE_USER=postgres
DB_USERNAME=postgres
DATABASE_PASSWORD=${DB_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
DATABASE_URL=jdbc:postgresql://db:5432/smartdesk_db

# Configuración del Servidor
PORT=8080
CORS_ALLOWED_ORIGINS=${ALLOWED_ORIGINS}

# Configuración de Seguridad
JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION=86400000

# Integraciones de Terceros (Reemplazar con tus verdaderas API keys)
RESEND_API_KEY=tu_api_key_de_resend
GEMINI_API_KEY=tu_api_key_de_gemini
EOF

echo "✅ Archivo .env generado correctamente."

# Iniciar Docker Compose
echo "Levantando servicios con Docker Compose..."
if command -v docker-compose &> /dev/null
then
    docker-compose up -d --build
elif docker compose version &> /dev/null
then
    docker compose up -d --build
else
    echo "❌ Error: Docker Compose no está instalado."
    exit 1
fi

echo "=========================================="
echo "✅ ¡Backend desplegado correctamente!"
echo "📍 Puerto expuesto: 8080"
echo "=========================================="
