# Módulo de caja — estado verificado

## Estado actual

El módulo de caja ya no es solamente un modelo de dominio. La base Room (esquema v9) contiene persistencia de sesiones y movimientos, y InfinityDatabase expone operaciones transaccionales para apertura, movimientos, cierre y cobros de préstamos.

### Funciones locales implementadas

- Monedas admitidas: DOP, USD y EUR.
- Apertura de sesión con fondo inicial no negativo.
- Una sola sesión abierta por empleado y moneda; se rechaza una segunda apertura.
- Persistencia de entradas y salidas con identificador único, importe positivo, motivo y fecha.
- Rechazo de movimientos duplicados y operaciones sobre sesiones cerradas.
- Validación de que la sesión utilizada sea la sesión abierta del empleado para esa moneda.
- Validación del saldo esperado para impedir egresos superiores al efectivo disponible.
- Cierre transaccional con conteo físico persistido.
- Cálculo de sobrante o faltante a partir del conteo físico frente al saldo esperado.
- Integración transaccional del cobro de préstamo con la entrada correspondiente en caja; la operación se ejecuta dentro de una transacción Room.
- Importes almacenados como enteros en unidades monetarias menores, sin aritmética binaria de punto flotante.

## Límites que siguen pendientes

- La persistencia local Room no equivale a una caja central compartida entre dispositivos.
- El backend compartido, la sincronización multi-dispositivo y la autoridad financiera del servidor todavía no están implementados ni desplegados.
- La aprobación de diferencias materiales por supervisor y el flujo de resolución auditada están pendientes.
- Los reversos financieros auditables y la reapertura autorizada de una caja cerrada están pendientes.
- El reporte imprimible/exportable de arqueo está pendiente.
- La interfaz y los flujos operativos deben someterse a revisión integral de usabilidad y permisos antes de producción.
- Las pruebas automatizadas aprobadas no sustituyen una auditoría contable, de seguridad o regulatoria.

## Procedimiento de arqueo

1. Confirmar empleado, sesión y moneda.
2. Revisar fondo inicial y movimientos registrados.
3. Conciliar cada cobro de préstamo con su pago y movimiento de entrada.
4. Contar físicamente el efectivo por moneda.
5. Registrar el conteo y cerrar la sesión.
6. Investigar y documentar cualquier diferencia; no crear movimientos ficticios para forzar un saldo cero.

**Efectivo esperado** = fondo inicial + entradas − salidas.

**Diferencia** = conteo físico − efectivo esperado.

Una diferencia positiva representa sobrante; una negativa representa faltante.

## Evidencia y alcance

La integración se encuentra en InfinityDatabase, con migración Room 8→9 para las tablas cash_sessions y cash_movements. Las pruebas de integración de caja cubren la persistencia del conteo y el cierre, así como el rechazo de dos sesiones abiertas para el mismo empleado y moneda.

La ejecución de GitHub Actions #325, asociada al commit 9d7baa403296441d86ee7eaec0a2ddf5761b0a23, finalizó con resultado **success**. Esto confirma el flujo automatizado configurado para ese commit; no certifica por sí solo preparación para producción.

## Regla de publicación

No presentar como disponibles la sincronización central, la aprobación de diferencias, los reversos ni los reportes exportables hasta que exista implementación verificable y pruebas correspondientes.
