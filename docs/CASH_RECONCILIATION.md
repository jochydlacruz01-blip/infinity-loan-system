# Control operativo y conciliación de caja

## Alcance actual
La aplicación mantiene sesiones de caja locales por empleado y moneda (DOP, USD y EUR). El fondo inicial y los movimientos se almacenan en unidades menores enteras. Los cobros de préstamos deben vincularse a una sesión abierta del empleado en la moneda del préstamo.

## Procedimiento de arqueo
1. Confirmar empleado, sesión y moneda de la caja.
2. Revisar fondo inicial y movimientos de entrada y salida.
3. Confirmar que los cobros de préstamos estén vinculados a movimientos de entrada y al registro de pago correspondiente.
4. Contar físicamente el efectivo, separado por moneda.
5. Registrar el conteo físico y cerrar la sesión.
6. Revisar la diferencia entre efectivo esperado y conteo físico.
7. Si hay diferencia, investigarla y documentarla; no registrar movimientos ficticios para forzar un saldo cero.

## Fórmula
Efectivo esperado = fondo inicial + entradas - salidas.

Diferencia = conteo físico - efectivo esperado.

Una diferencia positiva representa sobrante; una negativa representa faltante.

## Recomendaciones operativas pendientes de implementación
- Requerir autorización de supervisor para aprobar o resolver diferencias materiales.
- Incorporar un reporte de arqueo imprimible/exportable con movimientos, cobros, fondo inicial, efectivo esperado, conteo y diferencia.
- Implementar reversos con referencia a la operación original y auditoría, sin borrar operaciones financieras.
- Restringir reapertura de caja cerrada a una operación autorizada y auditada.
- Centralizar y respaldar los registros cuando esté disponible el backend.
- Verificar conciliación de caja y pagos mediante pruebas instrumentadas y revisión independiente antes de uso real.

## Estado
Este documento describe el procedimiento de control recomendado y las funciones locales existentes. No certifica controles de producción ni sustituye auditoría contable o regulatoria.
