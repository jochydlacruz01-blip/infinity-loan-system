# Seguridad y control de acceso

## Estado

Esta documentación distingue los cimientos ya implementados de los controles que todavía no existen en la aplicación.

### Cimientos implementados

- `StaffRole` define administrador, supervisor, cajero y cobrador.
- `Permission` define capacidades individuales y `AccessPolicy` aplica denegaciones explícitas antes de concesiones y permisos predeterminados.
- `PasswordHasher` implementa hashes versionados PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y comparación en tiempo constante.
- Room v9 persiste cuentas de empleados, intentos fallidos y bloqueos temporales; el primer inicio permite crear el administrador sin contraseña predeterminada.
- La interfaz Android exige autenticación antes de abrir el panel y mantiene la sesión en memoria.
- La navegación y las acciones principales se filtran por permisos; los manejadores de escritura vuelven a comprobar autorización en los flujos integrados.
- Existe administración local de empleados, restricciones contra escalamiento de privilegios y protección del último administrador. Los permisos individuales permiten concesiones y denegaciones; las denegaciones prevalecen.
- Room incluye un registro de auditoría de solo inserción. La interfaz muestra eventos recientes a perfiles autorizados.
- Se registran operaciones principales de clientes, préstamos, pagos y administración de empleados.
- Las operaciones de caja exigen sesión válida y permisos en la interfaz; el cobro de préstamo se registra transaccionalmente con el movimiento de caja.

### Límites actuales

La autenticación y auditoría son locales a este dispositivo. Todavía no hay backend compartido, sesiones de servidor ni sincronización multi-dispositivo. El bloqueo local de 15 minutos después de cinco contraseñas incorrectas no sustituye limitación de intentos por IP/usuario en servidor. La cobertura de autorización y auditoría no está demostrada para todas las rutas de escritura; debe completarse en la capa de dominio/servicio y probarse invocando operaciones directamente. Los permisos comprobados en la UI no constituyen una frontera de seguridad contra manipulación del cliente. No se considera seguridad de producción.

## Reglas obligatorias para la integración

1. Comprobar permisos en la capa de dominio/servicio, no solamente ocultar botones.
2. Aplicar autorización en backend para toda operación remota; nunca confiar en el rol enviado por el dispositivo.
3. Guardar únicamente hashes de contraseña, nunca contraseñas ni claves en texto plano.
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
- Trasladar protección contra fuerza bruta al backend y añadir recuperación segura y controles de enrolamiento del administrador.
- Añadir autorización en todos los endpoints y pruebas de seguridad.
- Revisar privacidad, protección de datos y requisitos regulatorios aplicables.
- Completar pruebas de migración, integridad financiera, caja y recuperación de errores.
