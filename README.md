# INFINITY LOAN SYSTEM — Base Android 0.1.0

Esta es una **base inicial de desarrollo**, no el sistema financiero completo ni una APK ya compilada.

## Incluye
- Proyecto Android nativo con Kotlin y Jetpack Compose.
- Dashboard inicial azul marino/dorado con indicadores vacíos de demostración.
- Motor financiero separado con amortización francesa, italiana y americana.
- `BigDecimal` para importes; no usa `Float` ni `Double` en el motor.
- Redondeo monetario explícito `HALF_UP` y ajuste del capital en la última cuota.
- Pruebas unitarias iniciales de los tres métodos, tasa cero y entradas inválidas.
- GitHub Actions para ejecutar pruebas y generar una APK debug.

## Requisitos
- Android Studio compatible con Android Gradle Plugin 8.7.3.
- JDK 17, Gradle 8.9 y Android SDK 35.
- Dispositivo Android 8.0 (API 26) o superior.

## Abrir el proyecto
1. Descarga y descomprime el ZIP.
2. En Android Studio selecciona **Open** y abre la carpeta `INFINITY_LOAN_SYSTEM_BASE`.
3. Espera la sincronización de Gradle.
4. Ejecuta `gradle testDebugUnitTest`.
5. Genera la APK debug con `gradle assembleDebug`.

## Subirlo a GitHub
1. Descomprime el ZIP.
2. Sube **el contenido** de la carpeta a la raíz del repositorio `infinity-loan-system`, rama `main`.
3. Haz Commit.
4. Abre **Actions**. El workflow `Android build` debe ejecutarse al recibir el push.
5. Si termina correctamente, descarga el artefacto `infinity-loan-system-debug`.

## Límites importantes
- Dashboard sin API ni datos reales.
- Aún no están implementados backend, PostgreSQL, autenticación, permisos efectivos, Room, sincronización offline, GPS activo, mapas, pagos digitales, portal web, contabilidad ni reportes productivos.
- No usar para prestar dinero ni registrar operaciones reales.
- La tasa recibida por el motor es **por período**: `0.05` significa 5% por cuota/período. La conversión de tasa nominal/efectiva y las frecuencias diarias, semanales, quincenales y mensuales deben configurarse en una capa contractual posterior.
- Antes de producción hacen falta pruebas de integración y extremo a extremo, revisión contable y validación legal/regulatoria en República Dominicana.

## Validación de esta entrega
Se inspeccionó la estructura y se contrastaron las fórmulas con cálculos numéricos independientes. La compilación Android no se ejecutó en este entorno; el workflow de GitHub está preparado para hacer la compilación y las pruebas en GitHub Actions. No se declara que esta APK ya esté compilada.
