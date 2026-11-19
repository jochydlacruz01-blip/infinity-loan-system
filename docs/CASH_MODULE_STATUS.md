# Módulo de caja — diseño y estado

Se incorporó un modelo de dominio Kotlin independiente para sesiones de caja, ingresos, egresos y cierres contados.

- Monedas admitidas: DOP, USD y EUR.
- Apertura con saldo inicial no negativo.
- Movimiento con identificador de operación, dirección, importe positivo y descripción.
- Rechazo de identificadores duplicados y de operaciones sobre sesiones cerradas.
- Cierre con conteo físico y cálculo explícito de diferencia (conteo menos saldo esperado).
- Aritmética checked (`Math.addExact`/`subtractExact`) para evitar desbordamientos silenciosos.

**Alcance:** este paquete contiene el modelo de dominio y pruebas unitarias. Aún no está conectado a Room, pantalla de caja, pagos de préstamos, permisos ni cierres persistentes; esas integraciones requieren migración y transacciones de base de datos y no se declaran completadas.
