# Pack integral: control de caja, arqueo y revisión de diferencias

> Alcance de integración para cerrar las brechas del módulo de caja. Este documento no declara funciones pendientes como implementadas.

## Estado base verificado en `main`

- Room persiste sesiones y movimientos de caja, con cobros de préstamos vinculados transaccionalmente a la sesión.
- El flujo de cierre captura conteo físico y calcula la diferencia.
- `CashVarianceReviewService` ya guarda decisiones append-only (APPROVED, REJECTED, REQUIRES_INVESTIGATION), exige permiso, comentario y que el revisor no sea el titular de la caja; revisa solo sesiones cerradas con diferencia.
- Auditoría reconstructiva y conciliación de cobros existentes.
- Brechas de interfaz: el flujo de revisión de diferencias no está conectado a una pantalla de revisión para supervisor; historial de sesiones en caja es resumido; exportación de arqueo no está integrada.

## Alcance del pack

1. **Arqueo por sesión y moneda**
   - Mostrar empleado, sesión, fecha, fondo inicial, lista cronológica de movimientos, efectivo esperado, conteo físico y diferencia.
   - Separar DOP/USD/EUR, nunca sumarlos sin conversión explícita.
   - Permitir filtro por sesión, empleado, moneda y rango de fechas.

2. **Revisión de supervisor**
   - Cola de sesiones cerradas con diferencias pendientes de revisión.
   - Mostrar snapshot immutable: esperado, contado, diferencia, moneda, empleado y fecha de cierre.
   - Decisiones: aprobada, rechazada o requiere investigación; comentario obligatorio.
   - Bloquear auto-revisión del empleado que cerró su caja y validar permisos/sesión en servicio, no solo en UI.
   - Mostrar revisiones anteriores sin reemplazarlas; cada decisión agrega registro.

3. **Conciliación de cobros**
   - Vincular pagos, movimiento CASH-PAY, sesión, préstamo y cliente.
   - Marcar discrepancias como diagnóstico, sin modificar ni compensar movimientos silenciosamente.
   - Reversos excluidos conforme a la lógica vigente; revisar discrepancias y operaciones no vinculadas.

4. **Informe de arqueo**
   - Vista imprimible/exportable con apertura, entradas/salidas, cobros vinculados, esperado, conteo, diferencia, cierre y revisiones.
   - Encabezado con empleado, sesión, moneda y fechas; exportación autorizada y auditada.
   - No incorporar información de otras monedas al total.

5. **Seguridad y auditoría**
   - Comprobar permisos en el servicio para cada operación.
   - Registro auditado de consultas/exportaciones sensibles, decisiones y filtros.
   - No borrar ni alterar movimientos financieros para cuadrar una caja.
   - Reapertura de caja cerrada permanece fuera del pack salvo autorización y diseño de movimientos compensatorios auditables.

## Criterios de aceptación

- [ ] Una diferencia no puede ser aprobada por el mismo empleado que cerró la sesión.
- [ ] Sin permiso, sesión caducada, comentario vacío o sesión abierta, el servicio rechaza la revisión.
- [ ] Revisiones consecutivas se conservan como historial append-only.
- [ ] Snapshot revisado corresponde a los valores reconstruidos de la sesión cerrada.
- [ ] Conciliación detecta pago sin movimiento o movimiento de cobro sin pago y presenta referencias exactas.
- [ ] Totales separados por moneda, con aritmética de unidades menores y overflow controlado.
- [ ] Exportación incluye todos los datos necesarios y se audita.
- [ ] Pruebas unitarias, instrumentadas, migraciones, Lint y build APK en CI.
- [ ] Validación visual en pantalla pequeña y grande, orientación vertical/horizontal.

## Orden de integración

1. Revisión del DAO, permisos, servicios, entidades y versión Room.
2. UI de supervisor y estados de revisión, usando el servicio existente.
3. Informe detallado de arqueo y exportación.
4. Conciliación con filtros y vínculo a expediente de pago.
5. Pruebas, revisión de seguridad, Lint y GitHub Actions.
6. Informe de evidencias; no fusionar sin autorización explícita.

