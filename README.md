# INFINITY LOAN SYSTEM

Aplicación Android nativa en desarrollo para gestión de préstamos. El proyecto se trabaja en GitHub, rama `main`, con Kotlin, Jetpack Compose y Room.

> **Estado:** prototipo funcional en evolución. No es todavía un sistema financiero listo para operar con clientes reales ni una versión publicable en Google Play.

## Base técnica actual

- Android nativo: Kotlin + Jetpack Compose.
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9 y JDK 17.
- `compileSdk 35`, `targetSdk 35`, `minSdk 26`.
- Persistencia local Room, esquema versión 15, con cuentas de empleados, auditoría local, bloqueo temporal tras cinco intentos fallidos, visitas de cobranza y libros de pagos/reversos. Las migraciones deben seguir validándose en emulador al introducir cambios de esquema.
- Diseño corporativo azul marino y dorado.
- Monedas modeladas: DOP, USD y EUR.
- Motor financiero con amortización francesa, italiana y americana.
- Tasas expresadas por período de pago y cálculos monetarios con `BigDecimal`.
- Calendarios de cuotas semanales, quincenales y mensuales.
- Registro local de clientes, préstamos, cuotas y pagos.
- Distribución de pagos por antigüedad de cuotas; excedentes registrados como movimientos de crédito del cliente.
- Auditoría local de operaciones de clientes, préstamos, pagos y administración de empleados; historial de solo inserción expuesto para perfiles autorizados.
- Sumas de importes en el panel y saldos de crédito con aritmética comprobada para evitar desbordamientos.
- Cimientos de seguridad: política RBAC por capacidades, hash PBKDF2 versionado, cuentas de empleados persistidas en Room y servicio de autenticación local con creación única del administrador inicial. La interfaz de inicio de sesión, administración de empleados, permisos individuales y controles de sesión están integrados localmente; no se considera seguridad de producción.

## Validación automatizada

GitHub Actions ejecuta:
- Lint Android (incluido en el workflow más reciente).
- Pruebas unitarias.
- Compilación de APK debug.
- Compilación de Android App Bundle (AAB) release sin firma.
- Pruebas instrumentadas en emulador Android.

El workflow compila un APK debug y un AAB release sin firma para validar el código. Por política de almacenamiento, no publica APK/AAB como artefactos de Actions. No se debe afirmar que existe un instalador descargable hasta verificar una distribución externa. Un AAB sin firma **no se puede publicar en Google Play**; la firma requiere una clave protegida fuera del repositorio.

## Funciones pendientes antes de producción

- Backend central compartido entre la aplicación Android y la plataforma web, con compartición de cartera entre varios celulares/cobradores opcional por empresa (y restricciones por sucursal, permisos y asignación); sincronización de cambios, cola offline, idempotencia, resolución de conflictos y revocación de dispositivos aún por implementar.
- Completar autorización en cada caso de uso/operación sensible; migrar la identidad local a sesiones seguras y autenticación centralizada en backend para varios dispositivos.
- Sincronización segura entre dispositivos, manejo de conflictos y operación sin conexión.
- Caja local: sesiones persistidas, movimientos de entrada/salida, validación de saldo disponible, cierre con arqueo físico; los cobros desde el formulario de pagos requieren una sesión abierta en la moneda del préstamo y se vinculan transaccionalmente a entradas de caja, con auditoría. Requiere ampliar conciliación/reportes y controles operativos antes de producción.
- Reversos de pagos: flujo con motivo obligatorio, permiso específico, protección contra duplicados y validación de dependencias; restaura cuotas, principal, crédito y caja cuando corresponde. Las migraciones del esquema Room versión 15 deben seguir validándose en emulador desde todas las versiones históricas admitidas. Procedimiento documentado en `docs/CASH_RECONCILIATION.md`.
- Mora y cargos: pendientes de política contractual aprobada; no se aplican cargos automáticos. Recibos formales, refinanciamientos y reglas completas de pagos anticipados también requieren diseño y validación.
- GPS y geolocalización de cobradores: todavía no implementados. La agenda local de visitas ya permite asignación, seguimiento de estados y cierre únicamente con evidencia de un pago válido; el uso de GPS requiere permisos, controles de privacidad y auditoría.
- Portal del cliente y pasarela de pagos digitales.
- Informes financieros y operativos completos; los paneles y reportes actuales son parciales y requieren conciliación integral.
- Revisión de seguridad, privacidad, protección de datos y requisitos regulatorios aplicables en República Dominicana.
- Firma de release, pruebas internas/ cerradas y preparación de ficha de Google Play.

## Funciones locales ya integradas\n\n- Agenda de visitas de cobranza ligada a cliente y préstamo, con asignación por cobrador, filtros y auditoría. El estado pagada exige un pago existente, positivo, del mismo préstamo y no reversado; no puede marcarse manualmente.\n- Los pagos en efectivo se registran dentro de una transacción junto con su entrada de caja y distribución por cuotas.\n- La pantalla de pagos muestra cliente, recibo, referencia completa de operación, estado de reverso y distribución aplicada a cuotas.\n- Los reversos validan actor, permiso, dependencias, crédito disponible y caja original; conservan el pago original y registran movimientos compensatorios.\n\n## Reglas de integridad financiera

- Los importes se almacenan en unidades menores enteras (por ejemplo, centavos) y se convierten a `BigDecimal` para cálculos.
- La tasa guardada es la tasa decimal por período: `0.05` representa 5% por cuota/período. La periodicidad y tasa contractual deben coincidir.
- Los pagos se distribuyen en cuotas vencidas, priorizando mora, cargos, intereses y capital; los campos de mora/cargos todavía no tienen una política contractual implementada.
- Los importes no aplicados se conservan como crédito del cliente, separados por moneda.
- Los préstamos heredados con pagos históricos no distribuidos requieren conciliación antes de aceptar nuevos pagos.

## Desarrollo y control de cambios

El repositorio y la rama de trabajo son:
- Repositorio: `jochydlacruz01-blip/infinity-loan-system`
- Rama: `main`

Los cambios se realizan directamente en GitHub y se validan mediante Actions. No se deben guardar contraseñas, tokens, claves privadas ni archivos de firma en el repositorio.

Consulta:
- [Lista de control de publicación en Google Play](docs/PLAY_STORE_RELEASE_CHECKLIST.md).
- [Requisitos de seguridad y control de acceso](docs/SECURITY_AND_ACCESS_CONTROL.md).
- [Contrato inicial del backend compartido](docs/BACKEND_API_CONTRACT.md).

Estos documentos describen trabajo pendiente; no significan que el backend, autenticación o publicación estén completados.
