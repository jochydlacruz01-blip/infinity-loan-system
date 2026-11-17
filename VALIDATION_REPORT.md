# Informe de revisión — Base 0.1.0

## Comprobaciones realizadas
- Estructura Android presente: configuración Gradle, manifest, actividad Compose, motor financiero, pruebas unitarias y workflow de GitHub Actions.
- El motor financiero usa `BigDecimal`; no usa `Float` ni `Double` para dinero.
- Los tres métodos están implementados: francés, italiano y americano.
- Se valida principal positivo, tasa no negativa, plazo mayor que cero y escala monetaria entre 0 y 6.
- Se redondean intereses e importes a la escala configurada con `HALF_UP`.
- La última cuota ajusta el principal pendiente para reconciliar el total amortizado.
- Se añadió una prueba de referencia de la cuota francesa y del ajuste de la última cuota.

## Contraste numérico independiente
Para un principal de RD$50,000.00, tasa de 5% por período y 12 períodos:
- Francés: cuota matemática sin redondear ≈ RD$5,641.270501; cuota regular redondeada RD$5,641.27. Por el redondeo acumulado, la última cuota queda RD$5,641.29.
- Italiano: primera cuota RD$6,666.67.
- Americano: interés periódico RD$2,500.00 y cuota final RD$52,500.00.

**Corrección importante:** una referencia anterior indicaba RD$5,641.02 para el método francés. Ese valor era incorrecto. El resultado de la fórmula estándar es RD$5,641.27. El código y la prueba de esta entrega usan el valor corregido.

## Qué no se pudo certificar
- No se ejecutó una compilación Android real ni Gradle/JUnit en este entorno. El workflow de GitHub queda configurado para ejecutar `gradle testDebugUnitTest assembleDebug`; el resultado real debe confirmarse en la pestaña Actions.
- La revisión estática y el contraste numérico independiente no sustituyen la ejecución de JUnit, pruebas de integración, pruebas de interfaz ni auditoría financiera.
- Esta es una base de arranque. No es todavía el sistema productivo ni una APK final.
