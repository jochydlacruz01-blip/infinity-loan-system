# Seguridad y control de acceso

## Estado verificado

Esta documentación separa los controles locales existentes de las medidas que siguen pendientes para operar con una cartera compartida y clientes reales.

### Cimientos implementados localmente

- `StaffRole` define administrador, supervisor, cajero y cobrador.
- `Permission` define capacidades individuales y `AccessPolicy` aplica denegaciones explícitas antes de concesiones y permisos predeterminados.
- `PasswordHasher` implementa hashes versionados PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y comparación en tiempo constante.
- Room **versión 15** persiste cuentas de empleados, intentos fallidos, bloqueos temporales, eventos de auditoría, reversos de pagos, promesas y visitas de cobranza.
- El primer inicio permite crear el administrador sin una contraseña predeterminada; la interfaz exige autenticación local antes de abrir el panel.
- La sesión de la aplicación se mantiene en memoria. La autenticación actual pertenece al dispositivo y no crea una identidad compartida entre teléfonos.
- Existe administración local de empleados, restricciones contra escalamiento de privilegios y protección del último administrador. Los permisos individuales permiten concesiones y denegaciones; las denegaciones prevalecen.
- La auditoría local es append-only desde la API de aplicación y registra operaciones principales de clientes, préstamos, pagos, caja y administración.
- Los flujos integrados de pagos, caja, reversos y visitas realizan validaciones de sesión/permisos en sus servicios y mantienen los cambios financieros relacionados dentro de transacciones Room cuando corresponde.
- La reversión de pagos conserva el pago original y registra compensaciones; la agenda solo permite marcar una visita como pagada cuando existe un pago positivo, del mismo préstamo y no reversado.

### Límites y riesgos pendientes

La autenticación y la auditoría son locales a este dispositivo. Todavía no hay backend compartido, sesiones de servidor ni sincronización multi-dispositivo. El bloqueo local después de intentos fallidos no sustituye limitación de intentos por IP/usuario en servidor.

La autorización local no es una frontera de seguridad contra manipulación del APK o de la base de datos del dispositivo. Aunque los servicios integrados validan operaciones protegidas, todavía se debe auditar cada ruta de lectura y escritura directa, cerrar accesos no encapsulados y verificar revocación de sesiones en todos los flujos. No se considera seguridad de producción.

## Reglas obligatorias para la integración

1. Comprobar permisos en la capa de dominio/servicio, no solamente ocultar botones.
2. Aplicar autorización en backend para toda operación remota; nunca confiar en el rol enviado por el dispositivo.
3. Guardar únicamente hashes de contraseña, nunca contraseñas ni claves en texto plano.
4. Implementar limitación de intentos, bloqueo progresivo, recuperación segura y revocación de sesiones en servidor.
5. Usar sesiones de corta duración, renovación rotativa y almacenamiento protegido en Android.
6. Auditar altas, cambios de permisos, pagos, reversos, sincronizaciones y operaciones administrativas.
7. No registrar contraseñas, tokens, secretos ni datos financieros sensibles en logs.
8. Mantener separación de datos por empresa y sucursal y validar el alcance de cada consulta en el servidor.
9. Probar accesos permitidos y denegados para cada rol, incluso al invocar APIs directamente.
10. Mantener movimientos financieros inmutables; corregirlos mediante reversos o ajustes compensatorios autorizados.

## Matriz inicial de permisos

Los permisos predeterminados son una base conservadora, no una política final aprobada. Los perfiles podrán recibir concesiones y denegaciones individuales.

- **Administrador:** todos los permisos predeterminados; las denegaciones individuales siguen prevaleciendo.
- **Supervisor:** supervisión de clientes, préstamos, pagos, caja, informes, personal de lectura y auditoría; sin administración global del sistema.
- **Cajero:** consulta operativa, registro de pagos, aplicación de crédito y operaciones de caja; sin creación de préstamos ni administración de personal.
- **Cobrador:** consulta de clientes y préstamos y registro de pagos; con acceso limitado por asignaciones; sin administración de préstamos, caja ni personal.

## Privacidad y GPS

El GPS no debe activarse en segundo plano ni recopilarse por defecto. Antes de implementar seguimiento se requiere:

- Solicitud de permiso contextual y consentimiento explícito, con explicación del propósito.
- Captura limitada al horario y tarea de cobranza autorizados.
- Indicador visible cuando se esté registrando ubicación y opción de detenerlo.
- Minimización de precisión y frecuencia según la necesidad operativa.
- Política de retención, acceso restringido, auditoría y eliminación conforme a la política aprobada.
- No incluir coordenadas en reportes generales ni compartirlas con otros empleados sin autorización.

## Puertas antes de producción

- Implementar autenticación real y sesiones revocables en backend.
- Trasladar protección contra fuerza bruta al backend y añadir recuperación segura y controles de enrolamiento del administrador.
- Añadir autorización en todos los endpoints y pruebas de aislamiento por empresa/sucursal.
- Revisar cada ruta local que acceda directamente a Room y encapsular escrituras sensibles.
- Proteger credenciales y tokens con mecanismos seguros del sistema operativo.
- Implementar política de privacidad, consentimiento y controles de retención para datos personales y ubicación.
- Completar pruebas de migración, integridad financiera, caja, recuperación de errores y pruebas de seguridad.
- Realizar revisión legal/regulatoria aplicable antes de ofrecer el producto a clientes reales.
