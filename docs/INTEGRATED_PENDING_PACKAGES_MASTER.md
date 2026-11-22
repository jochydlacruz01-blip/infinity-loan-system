# Paquetes integrados pendientes — INFINITY LOAN SYSTEM

**Base revisada:** `main` en `c2f54a264d5dcc74b40530d8264fbe7c7b304893` (22 nov 2026).  
**Estado:** plan de integración técnico consolidado; este documento no certifica que las funcionalidades enumeradas estén implementadas ni aprobadas.

## Condición de validación actual
- `lintDebug`, `testDebugUnitTest`, `assembleDebug` y `bundleRelease` terminaron con éxito en la etapa 5 del run #679.
- La etapa de pruebas instrumentadas falló. El diagnóstico sigue en proceso; aún no hay artefactos confirmados.
- La corrección de espacio liberó el paso de limpieza, pero no resolvió por sí sola el fallo instrumentado.
- No anunciar APK listo hasta confirmar la subida del artefacto y comprobarlo.

## Paquetes de integración pendientes

### P01 — CI/emulador y artefactos
- Diagnosticar la causa real de fallo del emulador a partir del registro completo.
- Separar diagnóstico de infraestructura de fallos de aplicación.
- Confirmar que los pasos de subida de APK y AAB se ejecutan incluso tras fallo de instrumentación.
- Evitar repetir runs con cambios no relacionados; consolidar commits y controlar concurrencia.
- Criterio: lint, unit tests, build APK/AAB; instrumentación o bloqueo de infraestructura documentado; artefactos comprobados.

### P02 — Integridad de pagos, asignaciones y reversos
- Auditar las transacciones de pago y reversión dentro de transacciones DB.
- Validar asignaciones de pago, cuotas, saldo y crédito excedente; reconciliar sumas y evitar doble contabilización.
- Revisar asignaciones huérfanas, pagos inexistentes, cuotas equivocadas, importes negativos, desbordamientos y reversos.
- Confirmar el historial/audit log y tests de casos límite.
- Criterio: consistencia del libro financiero y tests para rutas válidas/ inválidas; no alterar datos históricos sin migración.

### P03 — Caja, cierre y revisión supervisora
- Contrastar pagos en efectivo, entradas/salidas, apertura y cierre de caja y reversos.
- Separar efectivo esperado, contado y diferencias; impedir cierre duplicado y cambios no autorizados.
- Validar flujo de revisión del supervisor, trazabilidad y exportación.
- Criterio: pruebas de integración para sesión abierta/cerrada, diferencia, reverso, permisos y cierre.

### P04 — Clientes, préstamos, agenda y cobranzas
- Verificar navegación cliente→préstamo→cuotas→pagos, y que cada acción use el cliente/préstamo correcto.
- Revisar registro de visita, asignación y seguimiento, y enlaces de pagos a expediente.
- Referencias visibles numéricas simples; preservar identificadores internos existentes.
- Criterio: pruebas de flujo y de datos; corregir botones o pantallas desconectadas.

### P05 — Seguridad, permisos y auditoría
- Revisar roles administrador/supervisor/cajero/cobrador, autorización en capa de datos, sesiones y contraseñas.
- Comprobar acciones sensibles y registro auditable; minimizar datos personales y permisos de ubicación.
- Criterio: tests negativos de permisos y trazabilidad de acciones sensibles.

### P06 — Backend y sincronización multidispositivo
- Definir e implementar de forma gradual API, identidad, sincronización y conflictos; no habilitar sincronización simulada.
- Establecer idempotencia, estrategia offline, conflicto de pagos, controles de acceso y cifrado de transporte.
- Criterio: contrato API y pruebas de sincronización/conflictos; no declarar multi-dispositivo listo antes de un backend funcional.

### P07 — Informes, lanzamiento y revisión comercial
- Revisar informes, moneda, totales, mora, saldo, pagos, cartera y exportación.
- Checklist de Play Store, privacidad, copias/restauración, firma y release.
- Criterio: cifras conciliadas, pruebas, artefacto de release firmado y checklist completo.

## Orden de trabajo sugerido
1. P01 desbloqueo CI y evidencia.
2. P02 auditoría del libro financiero y reversos.
3. P03 caja y supervisor.
4. P04 flujos de cliente, préstamo y cobranza.
5. P05 seguridad.
6. P06 backend/sync.
7. P07 informes y lanzamiento.

## Reglas de entrega
- Cambios consolidados en commits coherentes; evitar cambios de esquema innecesarios.
- Revisar archivos existentes y tests antes de reemplazar o duplicar módulos.
- No borrar historial, no hacer force push, no subir secretos.
- Marcar un paquete como terminado solamente cuando el código, pruebas y revisión de cambios respalden esa conclusión.
