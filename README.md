# PreTriage Backend

Backend de PreTriage construido con Java 21, Spring Boot y PostgreSQL.

## Requisitos para desarrollo local

- Java 21.
- Docker Desktop, para ejecutar PostgreSQL mediante `compose.yaml`.
- Una aplicación Auth0 configurada para el entorno de desarrollo.
- Ollama con `llama3.2:3b` es recomendable para probar la clasificación con IA;
  si no responde, el flujo de recepción utiliza el clasificador determinístico de
  respaldo.

## Variables de entorno

Spring Boot importa automáticamente el archivo `.env` ubicado en la raíz del
repositorio. Este archivo no debe incluirse en Git.

Configuración mínima:

```env
AUTH0_M2M_CLIENT_ID=
AUTH0_M2M_CLIENT_SECRET=
AUTH0_M2M_SCOPE=read:users update:users delete:users create:users
AUTH0_APP_CLIENT_ID=
GOOGLE_MAPS_API_KEY=
```

### Correo de invitaciones

Por defecto se utiliza `LocalInvitationEmailAdapter`: no requiere SMTP y devuelve
el secreto una sola vez al administrador para probar el enlace localmente.

Para enviar correos con `SmtpInvitationEmailAdapter`:

```env
PRETRIAGE_INVITATIONS_EMAIL_MODE=smtp
PRETRIAGE_FRONTEND_BASE_URL=http://localhost:3000
PRETRIAGE_INVITATIONS_EMAIL_FROM=no-reply@tu-dominio.com
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
```

Mailpit puede utilizarse localmente en el puerto SMTP `1025`. Cuando el modo es
`smtp`, el backend nunca incluye el token de invitación en la respuesta. Si falla
la entrega, la invitación queda pendiente y puede reenviarse desde el panel; el
reenvío rota el token y vence el enlace anterior.

## Ejecutar localmente

Desde PowerShell, en la raíz del backend:

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

El backend queda disponible en:

- API: `http://localhost:8080`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

La configuración usa `JPA_DDL_AUTO=update` por defecto para conservar los datos
locales entre reinicios. Para reconstruir deliberadamente el esquema puede
definirse otro valor antes de iniciar la aplicación.

## Almacenamiento S3 opcional

Amazon S3 se utiliza exclusivamente para descargar el archivo asociado a un
estudio clínico mediante el endpoint médico:

```http
GET /api/medico/pacientes/{pacienteId}/historial-clinico/{estudioId}/archivo
```

S3 está desactivado por defecto. Los flujos de recepción, triaje, cola y atención
médica pueden ejecutarse localmente sin variables AWS.

Si S3 está desactivado y se intenta descargar un archivo clínico, el backend
responde con un error explícito indicando que el almacenamiento no está disponible
en ese entorno.

Para habilitarlo, agregar al `.env`:

```env
PRETRIAGE_STORAGE_S3_ENABLED=true
AWS_S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
```

No deben utilizarse valores ficticios cuando S3 está habilitado: las credenciales
necesitan permiso de lectura sobre el bucket configurado por `aws.s3.bucket-name`.

## Verificación

Compilar sin ejecutar pruebas:

```powershell
.\mvnw.cmd test -DskipTests
```

Ejecutar la suite completa:

```powershell
.\mvnw.cmd test
```

Las pruebas de integración requieren PostgreSQL/Docker disponible. S3 permanece
desactivado durante las pruebas salvo que se habilite explícitamente.
