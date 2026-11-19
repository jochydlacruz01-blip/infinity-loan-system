# Seguridad y control de acceso

## Estado

Esta documentación distingue los cimientos ya implementados de los controles que todavía no existen en la aplicación.

### Cimientos implementados

- `StaffRole` define administrador, supervisor, cajero y cobrador.
- `Permission` define capacidades individuales y `AccessPolicy` aplica denegaciones explícitas antes de concesiones y permisos predeterminados.
- `PasswordHasher` implementa hashes versionados PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y comparación en tiempo constante.
- Room v7 persiste cuentas de empleados; el primer inicio permite crear el administrador sin contraseña predeterminada.
- La interfaz Android exige autenticación antes de abrir el panel y mantiene la sesión en memoria.
- La navegación y las acciones principales de clientes, préstamos y pagos se filtran por permisos; los manejadores de escritura vuelven a comprobar la autorización.
- Existe administración local de empleados, con restricciones para evitar escalamiento de privilegios y desactivar el último administrador.
- Room v7 incorpora un registro de auditoría de solo inserción. La interfaz muestra los últimos 200 eventos a perfiles autorizados.
- Se registran altas y modificaciones de clientes, creación de préstamos, pagos y cambios de cuentas de empleados.

### Límites actuales

La autenticación y la auditoría son locales a este dispositivo. Todavía no hay backend compartido, sesiones de servidor ni sincronización multi-dispositivo. La base local aplica un bloqueo temporal de 15 minutos después de cinco contraseñas incorrectas para cuentas existentes; el control por dispositivo no sustituye limitación de intentos por IP/usuario en el servidor. La pantalla de personal asigna perfiles predeterminados; la edición granular de permisos aún no está integrada en la interfaz. La auditoría actual cubre eventos principales, no todos los accesos y cambios posibles. No se considera una autorización de producción hasta completar backend, revisión de seguridad y pruebas de extremo a extremo.

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
- Migrar la identidad local a un proveedor de identidad/backend compartido, con sesiones revocables y autorización del lado servidor.
- Trasladar la protección contra fuerza bruta al backend y añadir recuperación segura y controles de enrolamiento del administrador.
- Añadir autorización en todos los endpoints y pruebas de seguridad.
- Revisar privacidad, protección de datos y requisitos regulatorios aplicables.
