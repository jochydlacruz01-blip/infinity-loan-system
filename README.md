# INFINITY LOAN SYSTEM

Aplicación Android nativa en desarrollo para gestión de préstamos. El proyecto se trabaja en GitHub, rama `main`, con Kotlin, Jetpack Compose y Room.

> **Estado:** prototipo funcional en evolución. No es todavía un sistema financiero listo para operar con clientes reales ni una versión publicable en Google Play.

## Base técnica actual

- Android nativo: Kotlin + Jetpack Compose.
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9 y JDK 17.
- `compileSdk 35`, `targetSdk 35`, `minSdk 26`.
- Persistencia local Room, esquema versión 15.
- Diseño corporativo azul marino y dorado.
- Monedas modeladas: DOP, USD y EUR.
- Motor financiero con amortización francesa, italiana y americana.
- Tasas expresadas por período de pago y cálculos monetarios con `BigDecimal`.
- Calendarios de cuotas semanales, quincenales y mensuales.
- Registro local de clientes, préstamos, cuotas y pagos.
- Distribución de pagos por antigüedad de cuotas; excedentes registrados como movimientos de crédito del cliente.
- Caja local con sesiones, movimientos, cierres, conteo físico y revisión de diferencias.
- Reversos transaccionales con motivo, permiso específico, validación de dependencias y movimientos compensatorios; el pago original permanece en el historial.
- Agenda de cobranza con asignación, estados, filtros y cierre condicionado a evidencia de un pago válido, positivo, del mismo préstamo y no reversado.
- Reportes locales de cartera, recaudación mensual por moneda, antigüedad de cuotas vencidas, prioridad de cobro y conciliación de caja.
- Cimientos de seguridad local: RBAC por capacidades, hash PBKDF2 versionado, cuentas de empleados, creación única del administrador inicial, bloqueo temporal y auditoría local append-only. No equivale a seguridad de producción ni a identidad compartida.

## Validación automatizada

GitHub Actions ejecuta:
- Android lint.
- Pruebas unitarias.
- Compilación de APK debug.
- Compilación de Android App Bundle (AAB) release sin firma.
- Pruebas instrumentadas en emulador Android.

El workflow actual **no publica ni sube APK/AAB como artefactos de GitHub Actions**. La compilación correcta confirma que las tareas de build finalizaron, pero no crea un enlace de descarga. La distribución externa y firma protegida deberán configurarse por separado.

Una AAB sin firma sirve para validar la compilación, pero **no se puede publicar en Google Play**. Nunca se deben guardar contraseñas, tokens, keystores ni claves privadas en el repositorio.

## Funciones pendientes antes de producción

- Backend central compartido entre Android y la futura plataforma web, con cartera multi-dispositivo opcional por empresa.
- Sincronización segura, cola offline, idempotencia, resolución de conflictos, revocación de dispositivos y aislamiento por empresa/sucursal.
- Identidad central, sesiones revocables y autorización del lado servidor.
- Reglas contractuales aprobadas e implementadas para mora, cargos, períodos de gracia, pagos anticipados y reversos relacionados.
- Integración contable completa y conciliación financiera independiente de extremo a extremo.
- Portal del cliente y pasarela de pagos digitales.
- GPS de cobradores con consentimiento explícito, permisos contextuales, límites de retención, auditoría y opción de detener el seguimiento.
- Copias de seguridad, restauración, monitoreo, respuesta a incidentes y recuperación ante desastres.
- Revisión de seguridad, privacidad, protección de datos y requisitos regulatorios aplicables en República Dominicana.
- Firma de release, pruebas internas/cerradas y preparación de ficha de Google Play.

## Reglas de integridad financiera

- Los importes se almacenan en unidades menores enteras y se convierten a `BigDecimal` para cálculos.
- La tasa guardada es la tasa decimal por período: `0.05` representa 5% por cuota/período. La periodicidad y tasa contractual deben coincidir.
- Los pagos se distribuyen en cuotas vencidas, priorizando mora, cargos, intereses y capital; las reglas automáticas de mora/cargos todavía requieren política contractual aprobada.
- Los importes no aplicados se conservan como crédito del cliente, separados por moneda.
- Los préstamos heredados con pagos históricos no distribuidos requieren conciliación antes de aceptar nuevos pagos.
- Los movimientos contabilizados no deben editarse; las correcciones se registran mediante reversos o ajustes compensatorios auditados.
- Los informes deben excluir pagos reversados de los totales operativos y nunca sumar monedas distintas.

## Desarrollo y control de cambios

- Repositorio: `jochydlacruz01-blip/infinity-loan-system`
- Rama: `main`

Los cambios se validan mediante GitHub Actions. No se deben guardar credenciales, secretos ni archivos de firma en el repositorio.

Consulta:
- [Lista de control de publicación en Google Play](docs/PLAY_STORE_RELEASE_CHECKLIST.md).
- [Requisitos de seguridad, privacidad y control de acceso](docs/SECURITY_AND_ACCESS_CONTROL.md).
- [Contrato inicial del backend compartido](docs/BACKEND_API_CONTRACT.md).

Estos documentos distinguen las funciones locales ya implementadas de los componentes pendientes; describir una API o un requisito no significa que el backend, el GPS, el portal o la sincronización estén disponibles.
