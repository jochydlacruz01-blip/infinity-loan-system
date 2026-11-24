# INFINITY LOAN SYSTEM — revisión de seguridad e integridad

Punto de revisión: 24-11-2026. Este documento refleja el código Android inspeccionado y las validaciones observadas en GitHub Actions. No certifica seguridad de producción.

## Salvaguardas verificadas en el código

- Las contraseñas locales del personal usan PBKDF2-HMAC-SHA256 versionado, sal aleatoria y comparación de hash en tiempo constante.
- La creación inicial del administrador exige contraseña de al menos 6 caracteres y se ejecuta de forma transaccional.
- El flujo de autenticación local aplica bloqueo tras cinco intentos fallidos durante 15 minutos y verifica que la cuenta esté activa.
- La política RBAC admite concesiones y denegaciones individuales; las denegaciones prevalecen.
- Los servicios de pagos, reversos, caja y visitas aplican comprobaciones de sesión/permisos en las operaciones integradas.
- Los pagos se registran en transacciones con asignación de cuotas, saldo de capital y crédito no aplicado; los reversos registran compensaciones y preservan el pago original.
- Las visitas solo se pueden cerrar como pagadas con evidencia de un pago positivo, no reversado y del mismo préstamo.
- La observación de visitas vuelve a validar la cuenta y los permisos cuando cambian los registros observados o la cuenta del empleado; una cuenta desactivada o con permiso revocado deja de recibir esos datos.
- Room está en versión 15. La aplicación deshabilita el respaldo automático de Android.
- Workflow #701 terminó correctamente con Lint, pruebas unitarias, compilación APK/AAB y pruebas instrumentadas. Workflow #700 también terminó correctamente. La ejecución #706 valida la revisión posterior de la revocación de permisos.

## Hallazgos y trabajo pendiente

1. **Frontera de autorización:** la autorización sigue siendo local al dispositivo. No protege contra manipulación del APK o de la base de datos y no sustituye autorización en un servidor confiable.
2. **Cobertura de lectura:** aún debe revisarse toda lectura directa de Room para asegurar que las sesiones revocadas no sigan viendo datos de otros módulos.
3. **Auditoría:** hay auditoría local en flujos principales, pero debe completarse y verificarse la matriz de eventos de cambios de permisos, caja, exportaciones, configuración, fallos y acceso a datos.
4. **Mora y cargos:** el calculador de mora y sus pruebas no constituyen cobro contractual implementado. Falta política aprobada, persistencia contractual y asignación/reversión integrada.
5. **Identidad:** no hay identidad central, refresh tokens, recuperación de cuenta ni revocación de sesiones en servidor.
6. **Integridad multi-dispositivo:** falta backend con idempotencia de servidor, aislamiento por empresa/sucursal, control de concurrencia y libro financiero autoritativo.
7. **Privacidad:** GPS y portal del cliente no están implementados. Antes de GPS se requiere consentimiento explícito, control visible, captura limitada y retención definida.
8. **Operación:** faltan backups remotos cifrados, restauración probada, monitoreo, respuesta a incidentes y revisión externa.

## Próximas acciones

- Completar pruebas instrumentadas de revocación y observación protegida.
- Auditar y encapsular cada ruta de lectura/escritura directa a Room.
- Implementar backend con autenticación central, autorización por tenant/sucursal, sesiones revocables, idempotencia y sync.
- Implementar reportes reconciliados y política contractual de mora/cargos.
- Diseñar portal y GPS con protección de privacidad desde el inicio.
- Ejecutar revisión externa de seguridad, privacidad y requisitos regulatorios antes de operar con clientes reales.

## Regla de publicación

Un build exitoso no equivale a certificación ni disponibilidad de APK/AAB descargable. El workflow actual compila APK debug y AAB release sin firma, pero no sube esos archivos como artefactos. La publicación requiere firma protegida, controles legales/regulatorios, política de privacidad y pruebas de aceptación.
