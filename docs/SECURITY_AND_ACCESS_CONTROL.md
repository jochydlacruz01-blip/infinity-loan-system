# Seguridad y control de acceso

## Estado

Esta documentación distingue los cimientos ya implementados de los controles que todavía no existen en la aplicación.

### Cimientos en el código

- `StaffRole` define administrador, supervisor, cajero y cobrador.
- `Permission` define capacidades individuales, en vez de depender únicamente de nombres de pantallas.
- `AccessProfile` permite conceder o denegar permisos individuales.
- `AccessPolicy` aplica denegaciones explícitas antes de concesiones y permisos predeterminados.
- `PasswordHasher` implementa hashes versionados PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y comparación de hash en tiempo constante.
- Pruebas unitarias cubren los límites de permisos y la verificación de contraseñas.

### Límites actuales

Estos componentes son primitives de dominio y todavía no equivalen a un sistema de inicio de sesión completo. La interfaz Android aún no exige credenciales, los perfiles no se guardan en Room y los casos de uso no están protegidos por una sesión autenticada. No deben usarse para autorizar operaciones reales hasta completar la integración.

## Reglas obligatorias para la integración

1. Comprobar permisos en la capa de dominio/servicio, no solamente ocultar botones.
2. Aplicar autorización en el backend para toda operación remota; nunca confiar en el rol enviado por el dispositivo.
3. Guardar únicamente el hash de contraseña, nunca contraseñas ni claves en texto plano.
4. Implementar limitación de intentos, bloqueo progresivo, recuperación segura y revocación de sesiones.
5. Usar sesiones de corta duración, renovación rotativa y almacenamiento protegido en Android.
6. Auditar altas, cambios de permisos, pagos, reversos y operaciones administrativas.
7. No registrar contraseñas, tokens, secretos ni datos financieros sensibles en logs.
8. Mantener separación de datos por sucursal y validar el alcance de cada consulta en el servidor.
9. Probar explícitamente accesos permitidos y denegados para cada rol, incluso cuando se invoquen APIs directamente.

## Matriz inicial de permisos

Los permisos predeterminados son una base conservadora, no una política final aprobada. Los perfiles podrán recibir concesiones y denegaciones individuales.

- Administrador: todos los permisos predeterminados; las denegaciones individuales siguen prevaleciendo.
- Supervisor: supervisión de clientes, préstamos, pagos, caja, informes, personal de lectura y auditoría; sin administración global del sistema.
- Cajero: consulta operativa, registro de pagos, aplicación de crédito y operaciones de caja; sin creación de préstamos ni administración de personal.
- Cobrador: consulta de clientes y préstamos y registro de pagos; sin administración de préstamos, caja ni personal.

## Puertas antes de producción

- Integrar autenticación real y sesiones.
- Persistir cuentas, perfiles y permisos con migración de Room y sincronización backend.
- Crear el primer administrador mediante un proceso de enrolamiento seguro, no una contraseña predeterminada.
- Añadir autorización en todos los endpoints y pruebas de seguridad.
- Revisar privacidad, protección de datos y requisitos regulatorios aplicables.
