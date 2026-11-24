# INFINITY LOAN SYSTEM — release readiness

## Base técnica observada

- [x] Aplicación Android Kotlin + Jetpack Compose.
- [x] Room con esquema local versión 15. Las rutas históricas deben seguir cubriéndose con pruebas instrumentadas de migración.
- [x] Motor de amortización francesa, italiana y americana; frecuencias semanales, quincenales y mensuales.
- [x] Pagos en unidades menores, distribución a cuotas y libro de crédito del cliente.
- [x] Caja local con sesiones, movimientos, cierre y revisión de diferencias.
- [x] Reversos transaccionales con permiso, motivo, control de dependencias y movimientos compensatorios.
- [x] Agenda de visitas enlazada a cliente/préstamo; cierre pagado respaldado por pago válido.
- [x] Workflow CI ejecuta lint, pruebas unitarias, APK debug, AAB release sin firma y pruebas instrumentadas en emulador.

## Distribución y artefactos

- El workflow compila APK debug y AAB release sin firma.
- Los APK/AAB **no se publican como artefactos de Actions** en la configuración actual.
- No declarar que hay APK/AAB descargable o firmado sin verificar una entrega externa concreta.
- El AAB sin firma no es publicable en Google Play. La clave de carga debe mantenerse fuera del repositorio.

## Bloqueos antes de producción

- [ ] Implementar y desplegar backend compartido. `docs/BACKEND_API_CONTRACT.md` es una especificación, no un servicio operativo.
- [ ] Identidad de servidor, sesiones revocables, recuperación de cuenta y autorización en backend.
- [ ] Cartera compartida opcional por empresa, aislamiento por tenant/sucursal y sincronización idempotente Android/web.
- [ ] Cola offline, detección/resolución de conflictos y reconciliación de operaciones pendientes.
- [ ] Fecha/hora operativa autoritativa por empresa/sucursal y contabilización financiera en servidor.
- [ ] Política contractual aprobada e implementada para mora, cargos, períodos de gracia, pagos anticipados y reversos relacionados.
- [ ] Libro contable general y conciliación financiera integral independiente.
- [ ] Segregación de funciones y aprobaciones de supervisor para ajustes/reversos materiales.
- [ ] Portal del cliente y proveedor de pagos digitales.
- [ ] GPS de cobradores con consentimiento explícito, permisos mínimos, retención limitada y auditoría.
- [ ] Copias de seguridad, restauración, monitoreo, respuesta a incidentes y recuperación ante desastres.
- [ ] Pruebas de todas las migraciones históricas soportadas.
- [ ] Revisión de seguridad, privacidad y requisitos regulatorios aplicables en República Dominicana.
- [ ] Pruebas de accesibilidad, localización, ciclo de vida, baja memoria y dispositivos soportados.

## Publicación en Google Play

- [ ] Confirmar el nivel de API objetivo requerido por Google Play al momento de publicar.
- [ ] Configurar Play App Signing y clave de carga protegida.
- [ ] Generar y verificar AAB firmado con secretos protegidos.
- [ ] Confirmar applicationId y versionCode.
- [ ] Preparar ficha, capturas, icono, soporte y política pública de privacidad.
- [ ] Completar Data safety, clasificación, público objetivo y declaraciones de servicios financieros con información real.
- [ ] Ejecutar pruebas internas/cerradas y resolver el informe previo al lanzamiento.
- [ ] Despliegue gradual y monitoreo de fallos, ANR y reportes de seguridad.

## Regla de liberación

Una compilación correcta no demuestra que el producto esté listo para producción. No publicar mientras existan bloqueos críticos financieros, de seguridad, backend, privacidad o cumplimiento. No presentar compilaciones sin firma como versiones listas para Google Play.
