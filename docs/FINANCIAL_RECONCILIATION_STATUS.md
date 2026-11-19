# Conciliación financiera — estado verificado

## Alcance local actual

Este documento describe los controles que existen en la aplicación Android con Room. No representa conciliación central multi-dispositivo ni certificación contable.

### Invariantes que deben conservarse

1. **Pago idempotente:** un `operationId` no puede contabilizar dos pagos ni reducir dos veces el principal.
2. **Distribución auditable:** cada pago aplicado a cuotas debe conservar filas de asignación por cuota y por componente (mora, cargos, interés y capital).
3. **Saldo de préstamo:** `balanceMinor` representa principal pendiente. Solo disminuye por capital efectivamente asignado; interés, mora y cargos no se descuentan del principal.
4. **Excedente:** la parte no aplicada a cuotas vencidas se conserva como crédito del cliente en la misma moneda. Un pago anticipado se conserva como crédito hasta que exista una política contractual explícita.
5. **Crédito aplicado:** el débito del crédito y el pago al préstamo se ejecutan en una transacción Room; si falla cualquiera de las dos escrituras, ambas deben revertirse.
6. **Moneda:** no se mezclan DOP, USD y EUR. El saldo de crédito se consulta por cliente y moneda.
7. **Caja:** un cobro de préstamo registrado mediante la operación integrada debe crear el pago y su entrada de caja dentro de la misma transacción; la sesión debe estar abierta y corresponder al empleado/moneda.
8. **Aritmética:** los importes usan enteros en unidades menores y las sumas críticas usan operaciones exactas para detectar overflow.

## Evidencia automatizada conocida

- GitHub Actions #325 (commit `9d7baa403296441d86ee7eaec0a2ddf5761b0a23`): éxito en lint, pruebas unitarias, compilación APK/AAB y pruebas instrumentadas; incluye persistencia de cierre de caja y restricción de sesión abierta duplicada.
- GitHub Actions #326 (commit `fb5ab965b4e5c1093f94f2539890ae9ff52bf797`): éxito; incluye verificación de cálculo financiero desde principal redondeado a escala monetaria.
- GitHub Actions #327 (commit `a80c2720292135077133611278b3b1c522f8aa14`): éxito; incluye prueba instrumentada de rollback cuando colisiona el identificador del débito de crédito. El job de build y sus pasos de lint, pruebas, APK/AAB e instrumentación finalizaron correctamente.

Los resultados anteriores prueban los casos automatizados del repositorio en esos commits; no sustituyen auditoría independiente ni pruebas en producción.

## Reversos — primera etapa de control

Se añadió `PaymentReversalPolicy`, un control de elegibilidad puro (sin escrituras contables) con pruebas unitarias. Rechaza importes inválidos, actor o motivo vacíos, libros de crédito inconsistentes, pagos posteriores en el mismo préstamo, crédito excedente ya consumido y reversos de cobros en efectivo cuando la caja original ya está cerrada.

**Límite importante:** esto todavía no contabiliza reversos ni modifica pagos, cuotas, principal, crédito, caja o auditoría. La siguiente etapa debe implementar una operación Room atómica y append-only, exigir sesión/permisos vigentes y registrar el reverso y la auditoría sin borrar el pago original. La política exige que el llamador suministre hechos verificados desde la base de datos; no debe confiar en valores proporcionados por la interfaz.

## Conciliaciones adicionales requeridas antes de producción

- Ejecutar una conciliación integral por préstamo: principal original, suma de capital asignado, principal pendiente y saldo de préstamo.
- Verificar que la suma de componentes asignados nunca exceda el pago recibido y que el remanente coincida exactamente con el crédito generado.
- Conciliar cada pago en efectivo con una única entrada de caja, y detectar pagos sin movimiento o movimientos sin pago.
- Incorporar reversos inmutables y auditables que reviertan asignaciones, saldos, crédito y caja en una operación controlada.
- Implementar cierre con aprobación de diferencias materiales, segregación de funciones y trazabilidad de supervisor.
- Añadir fecha operativa autoritativa por sucursal desde el servidor. La contabilización local actual usa `LocalDate.now()`; depende del reloj/zona del dispositivo y no es autoridad financiera central.
- Implementar conciliación central del backend y sincronización idempotente multi-dispositivo antes de tratar saldos locales como oficiales.

## Políticas todavía no configuradas

No calcular ni cobrar mora o cargos contractuales hasta que se aprueben y persistan, por contrato: método (fijo/diario/porcentaje), base de cálculo, tasa, gracia, calendario, límites, redondeo, orden de aplicación y reglas de reverso. Los campos de importes pagados por mora/cargos no constituyen por sí solos una política de cobro.

## Regla de publicación

No anunciar reversos, conciliación central, autoridad de fecha del servidor, mora/cargos automáticos ni sincronización como funciones disponibles hasta que su implementación y pruebas puedan verificarse.
