package com.infinityloan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinityloan.data.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF0B1730)
private val Panel = Color(0xFF142746)
private val Gold = Color(0xFFD6AD55)
private val Muted = Color(0xFF9EADC4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = InfinityDatabase.get(this)
        setContent { InfinityApp(db) }
    }
}

private enum class Screen(val label: String) {
    HOME("Panel principal"), CLIENTS("Clientes"), LOANS("Préstamos y cuotas"),
    PAYMENTS("Pagos y caja"), REPORTS("Informes"), SETTINGS("Configuración")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfinityApp(db: InfinityDatabase) {
    val clients by db.clients().observeAll().collectAsState(initial = emptyList())
    val loans by db.loans().observeAll().collectAsState(initial = emptyList())
    val payments by db.payments().observeAll().collectAsState(initial = emptyList())
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showClientForm by remember { mutableStateOf(false) }
    var showLoanForm by remember { mutableStateOf(false) }
    var showPaymentForm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, background = Navy, surface = Panel,
        onPrimary = Navy, onBackground = Color.White, onSurface = Color.White
    )) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Panel) {
                    Text("INFINITY", color = Gold, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, modifier = Modifier.padding(20.dp))
                    Text("LOAN SYSTEM", color = Color.White,
                        modifier = Modifier.padding(start = 20.dp, bottom = 18.dp))
                    Screen.values().forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = screen == item,
                            onClick = {
                                screen = item
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF263D5D),
                                selectedTextColor = Gold,
                                unselectedTextColor = Color.White
                            )
                        )
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = Navy,
                topBar = {
                    TopAppBar(
                        title = { Text(screen.label, color = Color.White, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰", color = Gold, fontSize = 25.sp)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
                    )
                },
                floatingActionButton = {
                    when (screen) {
                        Screen.CLIENTS -> FloatingActionButton(onClick = { showClientForm = true }, containerColor = Gold) { Text("+", color = Navy, fontSize = 24.sp) }
                        Screen.LOANS -> FloatingActionButton(onClick = { showLoanForm = true }, containerColor = Gold) { Text("+", color = Navy, fontSize = 24.sp) }
                        Screen.PAYMENTS -> FloatingActionButton(onClick = { showPaymentForm = true }, containerColor = Gold) { Text("+", color = Navy, fontSize = 24.sp) }
                        else -> Unit
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(clients, loans, payments)
                        Screen.CLIENTS -> ClientsScreen(clients)
                        Screen.LOANS -> LoansScreen(loans, clients)
                        Screen.PAYMENTS -> PaymentsScreen(payments, loans, clients)
                        Screen.REPORTS -> InfoScreen("Informes", "Los informes se habilitarán cuando existan operaciones registradas. Los datos actuales se guardan localmente en este dispositivo.")
                        Screen.SETTINGS -> InfoScreen("Configuración", "Base local activa. La conexión al servidor, usuarios, permisos y copias de seguridad se integrarán en fases posteriores.")
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Gold)
                    }
                }
            }
        }
    }

    if (showClientForm) ClientForm(
        onDismiss = { showClientForm = false },
        onSave = { name, doc, phone, address ->
            scope.launch {
                try {
                    db.clients().insert(ClientEntity(fullName = name.trim(), documentNumber = doc.trim(), phone = phone.trim(), address = address.trim()))
                    message = "Cliente guardado en el dispositivo."
                } catch (_: Exception) { message = "No se pudo guardar: documento repetido o datos inválidos." }
                showClientForm = false
            }
        }
    )

    if (showLoanForm) LoanForm(
        clients = clients,
        onDismiss = { showLoanForm = false },
        onSave = { clientId, amount, rate, periods, currency, method ->
            scope.launch {
                try {
                    val principal = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)
                    val minor = principal.movePointRight(2).longValueExact()
                    val rateValue = BigDecimal(rate).divide(BigDecimal("100"))
                    require(minor > 0 && periods > 0 && rateValue >= BigDecimal.ZERO)
                    db.loans().insert(LoanEntity(
                        clientId = clientId, principalMinor = minor, balanceMinor = minor,
                        currency = currency, periodicRate = rateValue.toPlainString(),
                        periods = periods, method = method
                    ))
                    message = "Préstamo registrado. El saldo inicial quedó pendiente de calendario contractual."
                } catch (_: Exception) { message = "No se pudo registrar el préstamo. Revisa los valores." }
                showLoanForm = false
            }
        }
    )

    if (showPaymentForm) PaymentForm(
        loans = loans.filter { it.status == "ACTIVE" },
        onDismiss = { showPaymentForm = false },
        onSave = { loanId, amount, reference ->
            scope.launch {
                val result = try {
                    val minor = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                    db.payments().postPayment(
                        PaymentEntity(operationId = UUID.randomUUID().toString(), loanId = loanId, amountMinor = minor, reference = reference.trim())
                    )
                } catch (_: Exception) { PaymentPostResult.INVALID_AMOUNT }
                message = when (result) {
                    PaymentPostResult.POSTED -> "Pago registrado y saldo actualizado."
                    PaymentPostResult.EXCEEDS_BALANCE -> "El pago supera el saldo pendiente."
                    PaymentPostResult.LOAN_NOT_FOUND -> "No se encontró el préstamo."
                    PaymentPostResult.LOAN_NOT_ACTIVE -> "El préstamo no está activo."
                    PaymentPostResult.DUPLICATE -> "Operación duplicada."
                    else -> "Importe inválido. No se registró el pago."
                }
                showPaymentForm = false
            }
        }
    )
}

@Composable
private fun HomeScreen(
    clients: List<ClientEntity>,
    loans: List<LoanEntity>,
    payments: List<PaymentEntity>
) {
    val currencies = listOf("DOP", "USD", "EUR")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    "INFINITY LOAN SYSTEM",
                    color = Gold,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Panel ejecutivo · Sucursal principal",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi(
                    "Clientes",
                    clients.size.toString(),
                    Color(0xFF51417D),
                    Modifier.weight(1f)
                )
                Kpi(
                    "Préstamos",
                    loans.size.toString(),
                    Color(0xFF174B83),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi(
                    "Activos",
                    loans.count { it.status == "ACTIVE" }.toString(),
                    Color(0xFF17634E),
                    Modifier.weight(1f)
                )
                Kpi(
                    "Pagos",
                    payments.size.toString(),
                    Color(0xFF76551F),
                    Modifier.weight(1f)
                )
            }
        }
item {
    Text(
        "RESUMEN EJECUTIVO",
        color = Gold,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

items(currencies) { currency ->

    val activeLoans = loans.filter {
        it.currency == currency && it.status == "ACTIVE"
    }

    val activeBalance = activeLoans.sumOf {
        it.balanceMinor
    }

    DataCard(
        title = "Cartera activa · $currency",
        detail = """
            Préstamos activos: ${activeLoans.size}
            Capital pendiente: ${money(activeBalance)} $currency
        """.trimIndent()
    )
}
        item {
            Text(
                "CARTERA FINANCIERA",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(currencies) { currency ->
            val currencyLoans = loans.filter { it.currency == currency }

            val principal = currencyLoans.sumOf {
                it.principalMinor
            }

            val balance = currencyLoans.sumOf {
                it.balanceMinor
            }

            DataCard(
    title = client,
    detail = """
        Capital inicial: ${money(loan.principalMinor)} ${loan.currency}
        Saldo pendiente: ${money(loan.balanceMinor)} ${loan.currency}
        Tasa por período: ${
            BigDecimal(loan.periodicRate)
                .multiply(BigDecimal("100"))
                .stripTrailingZeros()
                .toPlainString()
        }%
        Cantidad de períodos: ${loan.periods}
        Sistema de amortización: ${loan.method}
        Estado: ${loan.status}
        Fecha de registro: ${formatDate(loan.createdAt)}
    """.trimIndent()
)
        
        }

              item {
            Text(
                "ACTIVIDAD RECIENTE",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        val recentPayments = payments
            .sortedByDescending { it.createdAt }
            .take(5)

        if (recentPayments.isEmpty()) {
            item {
                EmptyState(
                    "Sin actividad todavía",
                    "Los últimos pagos aparecerán aquí cuando se registren."
                )
            }
        } else {
            items(recentPayments, key = { it.operationId }) { payment ->
                val relatedLoan = loans.firstOrNull {
                    it.id == payment.loanId
                }

                val relatedClient = clients.firstOrNull {
                    it.id == relatedLoan?.clientId
                }

                DataCard(
                    title = "Pago · ${payment.reference.ifBlank { "Sin referencia" }}",
                    detail = """
                      Cliente: ${relatedClient?.fullName ?: "No disponible"}
Importe: ${money(payment.amountMinor)} ${relatedLoan?.currency ?: ""}
Fecha: ${formatDate(payment.createdAt)}
Préstamo: ${payment.loanId.take(8)}
                    """.trimIndent()
                )
            }
        }

               item {
            Text(
                "RECAUDACIÓN POR MONEDA",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(currencies) { currency ->
            val collected = payments
                .filter { payment ->
                    loans.any { loan ->
                        loan.id == payment.loanId &&
                        loan.currency == currency
                    }
                }
                .sumOf { it.amountMinor }

            DataCard(
                title = "Pagos recibidos · $currency",
                detail = """
                    Total registrado: ${money(collected)} $currency
                    Operaciones: ${
                        payments.count { payment ->
                            loans.any { loan ->
                                loan.id == payment.loanId &&
                                loan.currency == currency
                            }
                        }
                    }
                """.trimIndent()
            )
        }

        item {
            DataCard(
                "Centro de información",
                """
                    Base de datos local: activa.
                    Almacenamiento: Room.
                    Monedas disponibles: DOP, USD y EUR.
                    Estado del sistema: fase de construcción.
                    
                    Backend compartido, contabilidad completa,
                    sincronización y autenticación multiusuario:
                    pendientes de integración.
                """.trimIndent()
            )
        }
    }
}
   
@Composable
private fun ClientsScreen(clients: List<ClientEntity>) {
    if (clients.isEmpty()) EmptyState("Todavía no hay clientes", "Pulsa + para registrar el primer expediente.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(clients, key = { it.id }) { c ->
            DataCard(c.fullName, "Documento: ${c.documentNumber}\nTeléfono: ${c.phone}\n${c.address}")
        }
    }
}

@Composable
private fun LoansScreen(loans: List<LoanEntity>, clients: List<ClientEntity>) {
    if (loans.isEmpty()) EmptyState("Todavía no hay préstamos", "Registra un cliente antes de crear un préstamo.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(loans, key = { it.id }) { loan ->
            val client = clients.firstOrNull { it.id == loan.clientId }?.fullName ?: "Cliente no disponible"
            val ratePercent = loan.periodicRate
    .toBigDecimalOrNull()
    ?.multiply(BigDecimal("100"))
    ?.stripTrailingZeros()
    ?.toPlainString() ?: "No disponible"

DataCard(
    title = client,
    detail = """
        CAPITAL INICIAL
        ${money(loan.principalMinor)} ${loan.currency}

        SALDO PENDIENTE
        ${money(loan.balanceMinor)} ${loan.currency}

        TASA POR PERÍODO
        $ratePercent%

        CANTIDAD DE PERÍODOS
        ${loan.periods}

        SISTEMA DE AMORTIZACIÓN
        ${loan.method}

        ESTADO
        ${loan.status}

        FECHA DE REGISTRO
        ${formatDate(loan.createdAt)}
    """.trimIndent()
)
        }
    }
}

@Composable
@Composable
private fun PaymentsScreen(
    payments: List<PaymentEntity>,
    loans: List<LoanEntity>,
    clients: List<ClientEntity>
) {
    if (payments.isEmpty()) {
        EmptyState(
            "Sin pagos registrados",
            "Pulsa + para registrar un pago de un préstamo activo."
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            payments.sortedByDescending { it.createdAt },
            key = { it.operationId }
        ) { payment ->

            val loan = loans.firstOrNull {
                it.id == payment.loanId
            }

            val client = clients.firstOrNull {
                it.id == loan?.clientId
            }

            DataCard(
                title = "RECIBO · ${payment.reference.ifBlank { "Sin referencia" }}",
                detail = """
                    CLIENTE
                    ${client?.fullName ?: "No disponible"}

                    IMPORTE PAGADO
                    ${money(payment.amountMinor)} ${loan?.currency ?: ""}

                    FECHA Y HORA
                    ${formatDate(payment.createdAt)}

                    PRÉSTAMO
                    ${payment.loanId.take(8)}

                    ESTADO
                    REGISTRADO LOCALMENTE
                """.trimIndent()
            )
        }
    }
}

@Composable
private fun Kpi(title: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, color = color, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DataCard(title: String, detail: String) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoScreen(title: String, detail: String) {
    DataCard(title, detail)
}

@Composable
private fun ClientForm(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var doc by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo cliente") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, singleLine = true)
            OutlinedTextField(doc, { doc = it }, label = { Text("Cédula / documento") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, singleLine = true)
            OutlinedTextField(address, { address = it }, label = { Text("Dirección") })
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && doc.isNotBlank(), onClick = { onSave(name, doc, phone, address) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun LoanForm(
    clients: List<ClientEntity>, onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String) -> Unit
) {
    var selected by remember(clients) { mutableStateOf(clients.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }; var rate by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("12") }; var currency by remember { mutableStateOf("DOP") }
    var method by remember { mutableStateOf("FRANCES") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo préstamo") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (clients.isEmpty()) Text("Primero debes registrar un cliente.")
            else {
                Text("Cliente", color = Gold)
                clients.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == c.id, onClick = { selected = c.id })
                        Text(c.fullName, color = Color.White)
                    }
                }
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Capital (ej. 50000.00)") }, singleLine = true)
            OutlinedTextField(rate, { rate = it }, label = { Text("Tasa por período (%)") }, singleLine = true)
            OutlinedTextField(periods, { periods = it }, label = { Text("Cantidad de períodos") }, singleLine = true)
            Row { listOf("DOP", "USD", "EUR").forEach { TextButton(onClick = { currency = it }) { Text(if (currency == it) "[$it]" else it) } } }
            Row { listOf("FRANCES", "ITALIANO", "AMERICANO").forEach { TextButton(onClick = { method = it }) { Text(if (method == it) "[$it]" else it) } } }
        } },
        confirmButton = { TextButton(enabled = clients.isNotEmpty() && amount.toBigDecimalOrNull() != null && rate.toBigDecimalOrNull() != null && periods.toIntOrNull() != null, onClick = { onSave(selected, amount, rate, periods.toInt(), currency, method) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PaymentForm(
    loans: List<LoanEntity>, onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var selected by remember(loans) { mutableStateOf(loans.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }; var reference by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registrar pago") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (loans.isEmpty()) Text("No hay préstamos activos.")
            loans.forEach { loan ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == loan.id, onClick = { selected = loan.id })
                    Text("${loan.currency} ${money(loan.balanceMinor)} · ${loan.id.take(8)}", color = Color.White)
                }
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Importe") }, singleLine = true)
            OutlinedTextField(reference, { reference = it }, label = { Text("Referencia / recibo") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = loans.isNotEmpty() && amount.toBigDecimalOrNull() != null, onClick = { onSave(selected, amount, reference) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun money(minor: Long): String =
    BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.HALF_UP).toPlainString()
    private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}
