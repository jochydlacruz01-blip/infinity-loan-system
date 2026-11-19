# Contrato inicial del backend compartido

## Propósito y estado

Este documento fija un contrato neutral respecto al proveedor para que Android y la futura plataforma web utilicen el mismo motor financiero y los mismos registros. Es una especificación de trabajo: **el backend todavía no está implementado ni desplegado**.

No se debe conectar una aplicación real hasta elegir y configurar infraestructura, base de datos, dominios, copias de seguridad, gestión de secretos y monitoreo.

## Reglas de arquitectura

- El servidor es la autoridad para identidad, permisos, sucursal, saldos y contabilización.
- Android y web son clientes; nunca calculan ni confirman por sí solos el saldo oficial.
- Toda operación financiera se ejecuta en una transacción del servidor.
- Los movimientos contabilizados son inmutables. Una corrección se registra mediante reverso o ajuste auditado, nunca editando el pago original.
- Cada recurso operativo lleva `branchId`; el servidor comprueba que el usuario tenga acceso a esa sucursal.
- Los importes se transmiten como enteros en unidad menor y siempre acompañados de `currency`.
- Las tasas se transmiten como decimal en texto y con `ratePeriod` explícito.
- Fechas contractuales usan `YYYY-MM-DD`; marcas de auditoría usan UTC ISO-8601.
- Las operaciones de escritura financiera requieren `Idempotency-Key`.

## Cabeceras

- `Authorization: Bearer <access-token>` para endpoints autenticados.
- `Idempotency-Key: <unique-operation-id>` en creación de pagos, créditos, reversos y cierres de caja.
- `X-Client-Device-ID` como identificador no secreto del dispositivo para auditoría y gestión de sesiones.

El cliente no puede elegir su propio `userId`, rol efectivo ni sucursal autorizada en el cuerpo de una petición.

## Rutas previstas

### Identidad y sesión

- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `POST /v1/auth/logout`
- `GET /v1/auth/me`
- `GET /v1/auth/sessions`
- `DELETE /v1/auth/sessions/{sessionId}`

El backend devuelve permisos efectivos y sucursales accesibles. Las contraseñas nunca se devuelven ni se almacenan en texto plano.

### Personal y permisos

- `GET /v1/staff`
- `POST /v1/staff`
- `PATCH /v1/staff/{staffId}`
- `PUT /v1/staff/{staffId}/permissions`

Cada cambio de rol o permiso requiere autorización administrativa y genera un evento de auditoría.

### Clientes y préstamos

- `GET|POST /v1/clients`
- `GET|PATCH /v1/clients/{clientId}`
- `GET|POST /v1/loans`
- `GET /v1/loans/{loanId}`
- `GET /v1/loans/{loanId}/schedule`
- `GET /v1/loans/{loanId}/ledger`

La creación de préstamo devuelve el calendario contractual calculado por el servidor. La tasa y la periodicidad se validan juntas.

### Pagos y créditos

- `POST /v1/loans/{loanId}/payments`
- `GET /v1/payments/{operationId}`
- `POST /v1/payments/{operationId}/reversals`
- `GET /v1/clients/{clientId}/credits`
- `POST /v1/clients/{clientId}/credits/applications`

El servidor aplica FIFO por vencimiento y prioridad contractual. No se implementarán reglas de mora, cargos o pagos anticipados hasta aprobar las políticas de negocio. Los pagos no se deben aceptar fuera de la sucursal y permisos autorizados.

### Caja y auditoría

- `POST /v1/cash-sessions`
- `GET /v1/cash-sessions/current`
- `POST /v1/cash-sessions/{sessionId}/close`
- `GET /v1/audit-events`

Cierres, diferencias, reversos y cambios de permisos deben registrar usuario, sucursal, dispositivo, fecha y referencia de operación, sin guardar secretos.

## Formato monetario

Ejemplo conceptual de un importe:

```json
{
  "amountMinor": 125000,
  "currency": "DOP"
}
```

No se deben sumar monedas distintas ni usar números binarios de punto flotante para dinero. La conversión entre monedas requiere una tasa de cambio explícita, fecha, fuente y autorización.

## Errores estables

El servidor debe devolver códigos de error legibles por máquina, entre ellos:

- `UNAUTHENTICATED`
- `PERMISSION_DENIED`
- `BRANCH_ACCESS_DENIED`
- `VALIDATION_ERROR`
- `DUPLICATE_OPERATION`
- `INSUFFICIENT_BALANCE`
- `LOAN_NOT_ACTIVE`
- `LEGACY_RECONCILIATION_REQUIRED`
- `POLICY_NOT_CONFIGURED`
- `CONFLICT`

Los mensajes de error no deben revelar datos de otros clientes ni detalles internos.

## Requisitos antes de conectar clientes

- Elegir y aprobar proveedor de backend y base de datos.
- Definir modelo de sucursales y aislamiento de datos.
- Implementar autenticación, sesiones y autorización servidor.
- Implementar motor financiero en el servidor y pruebas de conciliación contra Android.
- Configurar TLS, gestión de secretos, backups y recuperación.
- Definir sincronización offline, reintentos, conflictos y deduplicación.
- Realizar pruebas de penetración y revisión de privacidad/regulación.


## Sincronización de cartera entre varios dispositivos (requisito aprobado)

La cartera debe ser compartida por dos o más celulares y por los clientes web futuros, bajo la misma sucursal y permisos. No se permite crear carteras independientes por dispositivo.

### Flujo esperado

- Los dispositivos autenticados consultan y sincronizan clientes, préstamos, calendarios, pagos, créditos y estado operativo desde el backend central.
- El backend mantiene la versión oficial; Room conserva una copia local/cache para lectura y resiliencia temporal.
- Un pago se registra como una operación idempotente: UUID generado por cliente, clave de idempotencia, usuario, dispositivo, hora de captura, sucursal y estado de sincronización.
- Una escritura financiera se confirma como contabilizada solo después de la aceptación transaccional del servidor. Offline debe mostrarse claramente como pendiente/no contabilizada hasta sincronizar; nunca debe mostrarse como saldo oficial.
- La sincronización recupera cambios desde un cursor/versionado y también admite paginación inicial; reintentos con backoff y deduplicación son obligatorios.
- Los cambios simultáneos de datos no financieros pueden usar control de versión y conflicto explícito. Los movimientos financieros no se sobrescriben: conflictos requieren rechazo, reconciliación o movimiento correctivo auditable.
- La app debe mostrar última sincronización, estado de conexión, operaciones pendientes y errores de sincronización; permitir reintentar.
- Revocación de sesión/dispositivo y cambio de permisos invalidan acceso de ese dispositivo; el servidor verifica permisos en cada petición.

### Rutas de sincronización previstas

- `GET /v1/sync/bootstrap` (carga inicial, paginada y filtrada por permisos/sucursal)
- `GET /v1/sync/changes?cursor={cursor}` (cambios desde el cursor válido)
- `POST /v1/sync/operations` (envío de operaciones idempotentes; respuesta individual por operación)
- `GET /v1/devices` y `DELETE /v1/devices/{deviceId}` (gestión/revocación de dispositivos, sujeto a permisos)

Las rutas son propuestas contractuales, no endpoints disponibles aún. Se debe implementar aislamiento por sucursal, TLS, autorización y trazabilidad antes de conectar celulares reales.


## Compartición de cartera: opcional por empresa (requisito aprobado)

La sincronización entre varios celulares y cobradores será una función optativa, controlada por configuración de la empresa; no se activará automáticamente para todas las organizaciones.

- En la configuración inicial, la empresa elige entre modo individual/local-organizado y modo cartera compartida multiusuario (cuando el backend esté disponible).
- Un administrador autorizado podrá activar o desactivar la compartición, con confirmación, registro de auditoría y advertencia sobre el alcance de los datos.
- La opción compartida permite que los empleados autorizados consulten y trabajen la cartera de la empresa, en los límites de roles, permisos, sucursal y asignaciones definidos.
- La empresa podrá restringir cobradores a clientes, grupos o préstamos asignados, en vez de compartir toda la cartera; permisos de supervisores y administradores podrán ser más amplios.
- Compartir no significa acceso irrestricto: cada petición se valida en servidor por empresa (tenant), sucursal, usuario, rol, permiso y asignación.
- Empresas distintas quedan aisladas. Ninguna opción de compartir puede exponer clientes, pagos, documentos o balances a otra empresa.
- Desactivar el modo compartido detiene el acceso sincronizado de los dispositivos y aplica la política de la empresa; no borra automáticamente registros ni historial. Se define un proceso seguro para conservar y exportar registros y evitar pérdida de datos.
- El modo y las asignaciones deben reflejarse en web y Android. Deben probarse casos de compartir, limitar, revocar y desactivar.

El ajuste de compartición es un requisito de diseño, no una función disponible hasta que el backend, configuración, autorización y sincronización estén implementados y validados.
