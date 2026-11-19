# Diseño de fases operativas — INFINITY LOAN SYSTEM

Este plan organiza la implementación en bloques paralelos, manteniendo los movimientos financieros inmutables y el backend central como autoridad futura. Es diseño y planificación técnica; no declara funcionalidades aún desplegadas.

## A. Caja y control diario
- Apertura de caja por empleado/dispositivo/sucursal/moneda, con saldo inicial declarado.
- Movimientos tipados: cobro de cuota, ingreso autorizado, gasto/retiro autorizado, devolución/reverso, transferencia entre cajas cuando la política lo permita.
- Cada movimiento conserva UUID idempotente, actor, hora, sucursal, moneda, importe menor entero, referencia, evidencia opcional y estado.
- Cierre: efectivo esperado = apertura + entradas contabilizadas - salidas contabilizadas; efectivo contado se captura aparte; diferencia se calcula, nunca se ajusta silenciosamente.
- Una caja no puede cerrarse dos veces ni contabilizar movimientos después del cierre sin autorización de reapertura auditada.
- Separación por moneda; no mezclar ni convertir sin tasa aprobada y registro explícito.

## B. Mora y cargos contractuales
- Mora solo cuando exista una política explícita asociada al contrato, con método, base, frecuencia, límites, tolerancia/gracia y fechas.
- No aplicar automáticamente mora sobre mora ni cargos compuestos salvo que el contrato y la política lo permitan.
- Mostrar cálculo desglosado, fechas, base y fórmula; permitir simulación antes de contabilizar.
- En pagos, orden de aplicación acordado: mora vencida, cargos aprobados, interés, principal; FIFO entre cuotas vencidas. Ajustes y reversos mediante movimientos auditados.
- Pruebas de borde: gracia, días festivos si aplica, cuotas parcialmente pagadas, saldo cero, pago tardío, cambios de fecha, límites y precisión por moneda.

## C. Informes y conciliación
- Reportes de cartera activa, vencida, por cobrador, sucursal, moneda y rango de fechas.
- Indicadores con definición explícita: saldo de principal, interés pendiente, mora/cargos contabilizados, crédito de clientes, pagos cobrados y montos pendientes.
- No sumar DOP/USD/EUR como un único total sin conversión registrada.
- Conciliación diaria: pagos y asignaciones, movimientos de caja, crédito del cliente, reversos y diferencias.
- Exportaciones deben respetar permisos y registrar quién exportó, cuándo y filtros utilizados.

## D. GPS, rutas y privacidad
- GPS optativo por política empresarial y consentimiento/permisos visibles, no vigilancia oculta.
- Modos definidos: jornada autorizada o registro de visita; no capturar ubicación fuera de la ventana habilitada.
- Captura mínima necesaria, controles de precisión, frecuencia configurable, señalización visible y acceso restringido.
- Retención limitada, eliminación/anonimización conforme a política y requisitos legales; no usar ubicación para decisiones automáticas no revisadas.
- Eventos de ruta/visita deben vincularse a empleado, cliente asignado, hora, motivo y estado de sincronización.

## E. Portal de cliente y canales de pago
- Portal con autenticación reforzada, consulta de préstamos, calendario, pagos, recibos y saldo por moneda.
- No revelar datos de clientes distintos; sesión, dispositivo, recuperación de cuenta y rate limiting.
- Integración de pagos digitales por proveedor aprobado: intentos, webhook firmado, idempotencia, conciliación y estado pendiente/confirmado/rechazado.
- Nunca declarar un pago como liquidado únicamente por retorno del navegador; requiere confirmación verificable del proveedor y conciliación.

## F. Backend, sincronización y empresas
- Tenant/empresa y sucursal son límites de seguridad obligatorios; validar permisos en servidor en todas las operaciones.
- Sync opcional por empresa, con selección de carteras/asignaciones; revocación de dispositivos y sesiones.
- Offline muestra estado pendiente, no saldo oficial; reconciliación por idempotencia y versión.
- Backups cifrados, recuperación probada, monitoreo, gestión de secretos y TLS.

## G. Seguridad, QA y release
- Pruebas de roles y denegaciones, tenant isolation, revocación, idempotencia, reversos, migraciones y escenarios offline.
- Pruebas de caja/conciliación y sumas de dinero con overflow, escala y precisión.
- No usar datos reales en pruebas; datos sintéticos.
- Firma release mediante secretos de CI fuera del repositorio, revisión de dependencias, política de privacidad y aceptación operativa antes de publicar.

## Orden de ejecución recomendado (bloques paralelos)
1. Caja/ledger y tests de integridad; diseño de mora y política contractual.
2. Reportes/conciliación y auditoría; seguridad de permisos.
3. Backend tenant/auth/sync; portal cliente.
4. GPS/visitas y pagos digitales después de permisos, privacidad y proveedor.
5. Integración, pruebas instrumentadas, validación y release.
