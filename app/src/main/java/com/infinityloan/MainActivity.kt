package com.infinityloan
 
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.withTransaction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinityloan.data.*
import com.infinityloan.finance.LoanScheduleGenerator
import com.infinityloan.finance.MinorUnitTotals
import com.infinityloan.finance.OverdueBalance
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffSession
import com.infinityloan.security.AuthenticationResult
import com.infinityloan.security.AccessPolicy
import com.infinityloan.security.Permission
import com.infinityloan.security.StaffRole
import com.infinityloan.security.AccessProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

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

private enum class Screen(val label: String, val permission: Permission) {
    HOME("Panel principal", Permission.DASHBOARD_VIEW),
    CLIENTS("Clientes", Permission.CLIENT_VIEW),
    LOANS("Préstamos y cuotas", Permission.LOAN_VIEW),
    PAYMENTS("Pagos", Permission.PAYMENT_VIEW),
    CASH("Gestión de caja", Permission.CASH_SESSION_OPEN),
    REPORTS("Informes", Permission.REPORT_VIEW),
    SETTINGS("Configuración", Permission.SYSTEM_SETTINGS_MANAGE),
    STAFF("Personal", Permission.STAFF_VIEW),
    AUDIT("Auditoría", Permission.AUDIT_LOG_VIEW)
}

@Composable
private fun InfinityApp(db: InfinityDatabase) {
    var session by remember { mutableStateOf<StaffSession?>(null) }
    var accountCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(db) {
        accountCount = withContext(Dispatchers.IO) { db.staffAccounts().count() }
    }

    val currentSession = session
    if (currentSession == null) {
        StaffAccessGate(
            accountCount = accountCount,
            service = remember(db) { StaffAuthenticationService(db.staffAccounts()) },
            db = db,
            onAuthenticated = { session = it }
        )
    } else {
        AuthenticatedInfinityApp(
            db = db,
            session = currentSession,
            onLogout = { session = null }
        )
    }
}

@Composable
private fun StaffAccessGate(
    accountCount: Int?,
    service: StaffAuthenticationService,
    db: InfinityDatabase,
    onAuthenticated: (StaffSession) -> Unit
) {
    val scope = rememberCoroutineScope()
    var displayedAccountCount by remember(accountCount) { mutableStateOf(accountCount) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val firstRun = displayedAccountCount == 0

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, background = Navy, surface = Panel,
        onPrimary = Navy, onBackground = Color.White, onSurface = Color.White
    )) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("INFINITY", color = Gold, fontWeight = FontWeight.Bold, fontSize = 27.sp)
                    Text("LOAN SYSTEM", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (displayedAccountCount == null) "Preparando acceso seguro…"
                        else if (firstRun) "Configuración inicial · Administrador principal"
                        else "Acceso del personal",
                        color = Muted
                    )
                    if (displayedAccountCount != null) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        )
                        if (firstRun) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Nombre del administrador") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy
                            )
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (firstRun) "Contraseña (mínimo 12 caracteres)" else "Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                val submittedPassword = password
                                password = ""
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        if (firstRun) {
                                            val created = withContext(Dispatchers.IO) {
                                                db.withTransaction {
                                                    val result = service.createInitialAdministrator(
                                                        username, displayName, submittedPassword.toCharArray()
                                                    )
                                                    if (result) {
                                                        db.auditLogs().insert(
                                                            AuditLogEntity(
                                                                actorStaffId = null,
                                                                actorUsername = username.trim().lowercase(Locale.ROOT),
                                                                action = "INITIAL_ADMIN_CREATED",
                                                                targetType = "STAFF_ACCOUNT",
                                                                targetId = null,
                                                                details = "Configuración inicial del administrador"
                                                            )
                                                        )
                                                    }
                                                    result
                                                }
                                            }
                                            if (created) {
                                                displayedAccountCount = 1
                                                message = "Administrador creado. Inicia sesión con tus credenciales."
                                            } else {
                                                displayedAccountCount = 1
                                                message = "La configuración inicial ya fue completada. Inicia sesión."
                                            }
                                        } else {
                                            val (result, authenticatedSession) = withContext(Dispatchers.IO) {
                                                service.authenticate(username, submittedPassword.toCharArray())
                                            }
                                            when (result) {
                                                AuthenticationResult.AUTHENTICATED ->
                                                    authenticatedSession?.let(onAuthenticated)
                                                AuthenticationResult.INVALID_CREDENTIALS ->
                                                    message = "Usuario o contraseña incorrectos."
                                                AuthenticationResult.ACCOUNT_DISABLED ->
                                                    message = "Esta cuenta está desactivada. Contacta al administrador."
                                                AuthenticationResult.ACCOUNT_LOCKED ->
                                                    message = "La cuenta está temporalmente bloqueada por intentos fallidos. Intenta nuevamente más tarde."
                                            }
                                        }
                                    } catch (error: IllegalArgumentException) {
                                        message = error.message ?: "No se pudieron validar las credenciales."
                                    } catch (_: Exception) {
                                        message = "No se pudo completar la operación de acceso."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && username.isNotBlank() && password.isNotEmpty() &&
                                (!firstRun || displayName.isNotBlank()),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)
                        ) {
                            Text(
                                if (busy) "Procesando…" else if (firstRun) "Crear administrador" else "Iniciar sesión",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        message?.let { Text(it, color = Gold) }
                    }
                    Text(
                        "Las credenciales de esta versión se almacenan localmente en este dispositivo. La identidad compartida entre sucursales y dispositivos requiere el backend central.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedInfinityApp(
    db: InfinityDatabase,
    session: StaffSession,
    onLogout: () -> Unit
) {
    val authenticationService = remember(db) { StaffAuthenticationService(db.staffAccounts()) }
    val clients by db.clients().observeAll().collectAsState(initial = emptyList())
    val loans by db.loans().observeAll().collectAsState(initial = emptyList())
    val payments by db.payments().observeAll().collectAsState(initial = emptyList())
    val installments by db.installments().observeAll().collectAsState(initial = emptyList())
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showClientForm by remember { mutableStateOf(false) }
    var selectedClient by remember { mutableStateOf<ClientEntity?>(null) }
    var showLoanForm by remember { mutableStateOf(false) }
    var showPaymentForm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val can: (Permission) -> Boolean = { AccessPolicy.allows(session.profile, it) }
    val availableScreens = Screen.values().filter { can(it.permission) }
    if (screen !in availableScreens) screen = availableScreens.firstOrNull() ?: Screen.HOME

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
                    availableScreens.forEach { item ->
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
                        title = {
                            Column {
                                Text(screen.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(session.displayName, color = Muted, fontSize = 10.sp)
                            }
                        },
                        actions = {
                            TextButton(onClick = onLogout) {
                                Text("Salir", color = Gold)
                            }
                        },
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
                        Screen.CLIENTS -> {
                            if (can(Permission.CLIENT_CREATE)) {
                                FloatingActionButton(
                                    onClick = {
                                        selectedClient = null
                                        showClientForm = true
                                    },
                                    containerColor = Gold
                                ) {
                                    Text("+", color = Navy, fontSize = 24.sp)
                                }
                            }
                        }
                        Screen.LOANS -> {
                            if (can(Permission.LOAN_CREATE)) {
                                FloatingActionButton(
                                    onClick = { showLoanForm = true },
                                    containerColor = Gold
                                ) { Text("+", color = Navy, fontSize = 24.sp) }
                            }
                        }
                        Screen.PAYMENTS -> {
                            if (can(Permission.PAYMENT_REGISTER)) {
                                FloatingActionButton(
                                    onClick = { showPaymentForm = true },
                                    containerColor = Gold
                                ) { Text("+", color = Navy, fontSize = 24.sp) }
                            }
                        }
                        else -> Unit
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(clients, loans, payments, installments)
                        Screen.CLIENTS -> ClientsScreen(
    clients = clients,
    onEdit = { client ->
        if (can(Permission.CLIENT_UPDATE)) {
            selectedClient = client
            showClientForm = true
        }
    }
)
                        Screen.LOANS -> LoansScreen(loans, clients)
                        Screen.PAYMENTS -> PaymentsScreen(payments, loans, clients)
                        Screen.CASH -> CashManagementScreen(db, session, can, onMessage = { message = it })
                        Screen.REPORTS -> ReportsSummaryScreen(db, clients, loans, payments, installments)
                        Screen.SETTINGS -> InfoScreen("Configuración", "La autenticación y los perfiles locales están activos en este dispositivo. La sincronización central, copias remotas y controles de producción requieren el backend compartido.")
                        Screen.STAFF -> StaffManagementScreen(
                            db = db,
                            session = session,
                            onMessage = { message = it }
                        )
                        Screen.AUDIT -> AuditLogScreen(db)
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
    initialClient = selectedClient,
    onDismiss = {
        showClientForm = false
        selectedClient = null
    },
    onSave = { name, doc, phone, address ->
        val clientToEdit = selectedClient

        scope.launch {
            try {
                require(withContext(Dispatchers.IO) {
                    authenticationService.isSessionValid(session)
                }) { "La sesión ha caducado. Inicia sesión nuevamente." }
                require(
                    if (clientToEdit == null) can(Permission.CLIENT_CREATE)
                    else can(Permission.CLIENT_UPDATE)
                ) { "No tienes permiso para esta operación." }
                val cleanName = name.trim()
                val cleanDoc = doc.trim()
                val cleanPhone = phone.trim()
                val cleanAddress = address.trim()

                require(cleanName.isNotBlank() && cleanDoc.isNotBlank())

                if (clientToEdit == null) {
                    val newClient = ClientEntity(
                        fullName = cleanName,
                        documentNumber = cleanDoc,
                        phone = cleanPhone,
                        address = cleanAddress
                    )
                    db.withTransaction {
                        db.clients().insert(newClient)
                        db.auditLogs().insert(AuditLogEntity(
                            actorStaffId = session.staffId,
                            actorUsername = session.username,
                            action = "CLIENT_CREATED",
                            targetType = "CLIENT",
                            targetId = newClient.id,
                            details = "Registro de cliente"
                        ))
                    }
                    message = "Cliente registrado correctamente."
                } else {
                    db.withTransaction {
                        db.clients().updateClient(
                            id = clientToEdit.id,
                            fullName = cleanName,
                            documentNumber = cleanDoc,
                            phone = cleanPhone,
                            address = cleanAddress
                        )
                        db.auditLogs().insert(AuditLogEntity(
                            actorStaffId = session.staffId,
                            actorUsername = session.username,
                            action = "CLIENT_UPDATED",
                            targetType = "CLIENT",
                            targetId = clientToEdit.id,
                            details = "Actualización de datos del cliente"
                        ))
                    }
                    message = "Cliente actualizado correctamente."
                }
            } catch (_: Exception) {
                message = "No se pudo guardar. Verifica los datos o si el documento ya existe."
            } finally {
                showClientForm = false
                selectedClient = null
            }
        }
    }
)

    if (showLoanForm) LoanForm(
        clients = clients,
        onDismiss = { showLoanForm = false },
        onSave = { clientId, amount, rate, periods, currency, method, frequency, firstDueDateText ->
            scope.launch {
                try {
                    require(withContext(Dispatchers.IO) {
                        authenticationService.isSessionValid(session)
                    }) { "La sesión ha caducado. Inicia sesión nuevamente." }
                    require(can(Permission.LOAN_CREATE)) { "No tienes permiso para crear préstamos." }
                    val principal = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)
                    val minor = principal.movePointRight(2).longValueExact()
                    val rateValue = BigDecimal(rate).divide(BigDecimal("100"))
                    val firstDueDate = LocalDate.parse(firstDueDateText)
                    val schedule = LoanScheduleGenerator.generate(
                        principal = principal,
                        periodicRate = rateValue,
                        periods = periods,
                        method = method,
                        frequency = LoanScheduleGenerator.Frequency.valueOf(frequency),
                        firstDueDate = firstDueDate
                    )
                    val loan = LoanEntity(
                        clientId = clientId, principalMinor = minor, balanceMinor = minor,
                        currency = currency, periodicRate = rateValue.toPlainString(),
                        periods = periods, method = method,
                        frequency = frequency, firstDueDate = firstDueDate.toString()
                    )
                    val rows = schedule.map { item ->
                        LoanInstallmentEntity(
                            loanId = loan.id,
                            number = item.number,
                            dueDate = item.dueDate.toString(),
                            principalMinor = item.principal.movePointRight(2).longValueExact(),
                            interestMinor = item.interest.movePointRight(2).longValueExact(),
                            totalMinor = item.total.movePointRight(2).longValueExact(),
                            remainingPrincipalMinor = item.remainingPrincipal.movePointRight(2).longValueExact()
                        )
                    }
                    require(rows.fold(0L) { total, row -> Math.addExact(total, row.principalMinor) } == minor) {
                        "El calendario no reconcilia el capital del préstamo."
                    }
                    db.withTransaction {
                        db.loans().insert(loan)
                        db.installments().insertAll(rows)
                        db.auditLogs().insert(AuditLogEntity(
                            actorStaffId = session.staffId,
                            actorUsername = session.username,
                            action = "LOAN_CREATED",
                            targetType = "LOAN",
                            targetId = loan.id,
                            details = "currency=$currency;principalMinor=$minor;installments=" + rows.size
                        ))
                    }
                    message = "Préstamo y ${rows.size} cuotas contractuales registrados correctamente."
                } catch (_: Exception) {
                    message = "No se pudo registrar. Revisa el monto, la tasa, el plazo y la fecha (AAAA-MM-DD)."
                }
                showLoanForm = false
            }
        }
    )

    if (showPaymentForm) PaymentForm(
        loans = loans.filter { it.status == "ACTIVE" },
        onDismiss = { showPaymentForm = false },
        onSave = { operationId, loanId, amount, reference ->
            scope.launch {
                val result = try {
                    require(withContext(Dispatchers.IO) {
                        authenticationService.isSessionValid(session)
                    }) { "La sesión ha caducado. Inicia sesión nuevamente." }
                    require(can(Permission.PAYMENT_REGISTER)) { "No tienes permiso para registrar pagos." }
                    val minor = BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                    db.withTransaction {
                        val loan = db.loans().findById(loanId) ?: error("Préstamo no encontrado.")
                        val cashSession = db.cashSessions().findOpenSession(session.staffId, loan.currency)
                            ?: error("Abre primero una caja en ${loan.currency} para registrar este cobro.")
                        val postResult = db.postCashLoanPayment(
                            PaymentEntity(operationId, loanId, minor, reference.trim()),
                            cashSession.sessionId
                        )
                        if (postResult == PaymentPostResult.POSTED) {
                            db.auditLogs().insert(AuditLogEntity(
                                actorStaffId = session.staffId,
                                actorUsername = session.username,
                                action = "PAYMENT_REGISTERED_WITH_CASH",
                                targetType = "PAYMENT",
                                targetId = operationId,
                                details = "loanId=$loanId;amountMinor=$minor;currency=${loan.currency};cashSessionId=${cashSession.sessionId}"
                            ))
                        }
                        postResult
                    }
                } catch (e: Exception) {
                    message = e.message ?: "No se pudo registrar el cobro."
                    PaymentPostResult.INVALID_AMOUNT
                }
                message = when (result) {
                    PaymentPostResult.POSTED -> "Pago registrado y entrada de efectivo vinculada a caja."
                    PaymentPostResult.EXCEEDS_BALANCE -> "El pago supera el saldo pendiente."
                    PaymentPostResult.LOAN_NOT_FOUND -> "No se encontró el préstamo."
                    PaymentPostResult.LOAN_NOT_ACTIVE -> "El préstamo no está activo."
                    PaymentPostResult.DUPLICATE -> "Operación duplicada."
                    PaymentPostResult.SCHEDULE_NOT_FOUND -> "El préstamo no tiene calendario de cuotas."
                    PaymentPostResult.INVALID_SCHEDULE -> "El calendario o la caja no son válidos. No se registró el pago."
                    PaymentPostResult.LEGACY_RECONCILIATION_REQUIRED -> "Este préstamo tiene pagos históricos sin distribución por cuotas. Requiere conciliación."
                    PaymentPostResult.INVALID_AMOUNT -> message ?: "Importe inválido o caja no abierta. No se registró el cobro."
                }
                if (result == PaymentPostResult.POSTED || result == PaymentPostResult.DUPLICATE) {
                    showPaymentForm = false
                }
            }
        }
    )
}


@Composable
private fun CashManagementScreen(db: InfinityDatabase, session: StaffSession, can: (Permission) -> Boolean, onMessage: (String) -> Unit) {
    val authenticationService = remember(db) { StaffAuthenticationService(db.staffAccounts()) }
    val openSessions by db.cashSessions().observeOpenSessions().collectAsState(initial = emptyList())
    val allSessions by db.cashSessions().observeSessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var currency by remember { mutableStateOf("DOP") }
    var opening by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var counted by remember { mutableStateOf("") }
    val own = openSessions.firstOrNull { it.staffId == session.staffId && it.currency == currency }
    val ownMovements by (if (own == null) kotlinx.coroutines.flow.flowOf(emptyList()) else db.cashSessions().observeMovements(own.sessionId)).collectAsState(initial = emptyList())
    fun minor(value: String): Long = BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    fun display(value: Long): String = BigDecimal.valueOf(value, 2).toPlainString()
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("CONTROL DE CAJA", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Sesión individual · " + session.displayName, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("DOP", "USD", "EUR").forEach { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) } }
        }
        item {
            if (own == null) {
                OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Fondo inicial ($currency)") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = can(Permission.CASH_SESSION_OPEN) && opening.isNotBlank(), onClick = {
                    scope.launch { try {
                        require(withContext(Dispatchers.IO) { authenticationService.isSessionValid(session) }) { "La sesión ha caducado." }
                        require(can(Permission.CASH_SESSION_OPEN)) { "No tienes permiso para abrir caja." }
                        val startMinor = minor(opening); require(startMinor >= 0L)
                        db.withTransaction {
                            val newSession = CashSessionEntity(UUID.randomUUID().toString(), session.staffId, currency, startMinor, System.currentTimeMillis())
                            db.openCashSession(newSession)
                            db.auditLogs().insert(AuditLogEntity(actorStaffId = session.staffId, actorUsername = session.username, action = "CASH_OPENED", targetType = "CASH_SESSION", targetId = newSession.sessionId, details = "currency=$currency;openingMinor=$startMinor"))
                        }
                        opening=""; onMessage("Caja abierta ($currency).")
                    } catch(e:Exception) { onMessage(e.message ?: "No se pudo abrir la caja.") } }
                }) { Text("Abrir caja") }
            } else {
                val expected = ownMovements.fold(own.openingMinor) { acc, m -> if(m.type=="CASH_IN") Math.addExact(acc,m.amountMinor) else Math.subtractExact(acc,m.amountMinor) }
                DataCard("Caja abierta · $currency", "Fondo inicial: " + display(own.openingMinor) + " $currency\nMovimientos: " + ownMovements.size + "\nEfectivo esperado: " + display(expected) + " $currency")
                Text("Registrar movimiento", color = Gold, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Motivo / descripción") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = can(Permission.CASH_MOVEMENT_MANAGE) && amount.isNotBlank() && reason.isNotBlank(), onClick = {
                        scope.launch { try {
                            require(withContext(Dispatchers.IO) { authenticationService.isSessionValid(session) }) { "La sesión ha caducado." }
                            require(can(Permission.CASH_MOVEMENT_MANAGE)) { "No tienes permiso para registrar movimientos de caja." }
                            val value = minor(amount); require(value > 0)
                            db.withTransaction {
                                val m = CashMovementEntity(UUID.randomUUID().toString(), own.sessionId, "CASH_IN", value, reason.trim(), System.currentTimeMillis())
                                db.postCashMovement(m)
                                db.auditLogs().insert(AuditLogEntity(actorStaffId = session.staffId, actorUsername = session.username, action = "CASH_IN_RECORDED", targetType = "CASH_MOVEMENT", targetId = m.operationId, details = "amountMinor=$value;currency=$currency"))
                            }
                            amount=""; reason=""; onMessage("Entrada registrada.")
                        } catch(e:Exception) { onMessage(e.message ?: "No se pudo registrar.") } }
                    }) { Text("Entrada") }
                    Button(enabled = can(Permission.CASH_MOVEMENT_MANAGE) && amount.isNotBlank() && reason.isNotBlank(), onClick = {
                        scope.launch { try {
                            require(withContext(Dispatchers.IO) { authenticationService.isSessionValid(session) }) { "La sesión ha caducado." }
                            require(can(Permission.CASH_MOVEMENT_MANAGE)) { "No tienes permiso para registrar movimientos de caja." }
                            val value = minor(amount); require(value > 0)
                            db.withTransaction {
                                val m = CashMovementEntity(UUID.randomUUID().toString(), own.sessionId, "CASH_OUT", value, reason.trim(), System.currentTimeMillis())
                                db.postCashMovement(m)
                                db.auditLogs().insert(AuditLogEntity(actorStaffId = session.staffId, actorUsername = session.username, action = "CASH_OUT_RECORDED", targetType = "CASH_MOVEMENT", targetId = m.operationId, details = "amountMinor=$value;currency=$currency"))
                            }
                            amount=""; reason=""; onMessage("Salida registrada.")
                        } catch(e:Exception) { onMessage(e.message ?: "No se pudo registrar.") } }
                    }) { Text("Salida") }
                }
                Text("Movimientos de la sesión", color = Gold, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ownMovements.forEach { m -> DataCard(if(m.type=="CASH_IN") "Entrada" else "Salida", m.reason + "\n" + display(m.amountMinor) + " $currency") } }
                OutlinedTextField(value = counted, onValueChange = { counted = it }, label = { Text("Conteo físico al cierre ($currency)") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = can(Permission.CASH_SESSION_CLOSE) && counted.isNotBlank(), onClick = {
                    scope.launch { try {
                        require(withContext(Dispatchers.IO) { authenticationService.isSessionValid(session) }) { "La sesión ha caducado." }
                        require(can(Permission.CASH_SESSION_CLOSE)) { "No tienes permiso para cerrar caja." }
                        val count = minor(counted); require(count >= 0L)
                        db.withTransaction {
                            db.closeCashSession(own.sessionId, count)
                            db.auditLogs().insert(AuditLogEntity(actorStaffId = session.staffId, actorUsername = session.username, action = "CASH_CLOSED", targetType = "CASH_SESSION", targetId = own.sessionId, details = "countedMinor=$count;expectedMinor=$expected;varianceMinor=${Math.subtractExact(count,expected)}"))
                        }
                        counted=""; onMessage("Caja cerrada. Diferencia: " + display(Math.subtractExact(count,expected)) + " $currency")
                    } catch(e:Exception) { onMessage(e.message ?: "No se pudo cerrar.") } }
                }) { Text("Cerrar y conciliar caja") }
            }
        }
        item { Text("Historial de sesiones", color = Gold, fontWeight = FontWeight.Bold) }
        items(allSessions.take(30)) { row ->
            val state = if(row.closedAt==null) "ABIERTA" else "CERRADA"
            DataCard("$state · ${row.currency}", "Apertura: " + display(row.openingMinor) + "\nFecha: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(row.openedAt)) + "\n" + (if(row.countedClosingMinor!=null) "Conteo cierre: " + display(row.countedClosingMinor) else "En curso"))
        }
    }
}


@Composable
private fun ReportsSummaryScreen(
    db: InfinityDatabase,
    clients: List<ClientEntity>,
    loans: List<LoanEntity>,
    payments: List<PaymentEntity>,
    installments: List<LoanInstallmentEntity>
) {
    val currencies = listOf("DOP", "USD", "EUR")
    val today = LocalDate.now().toString()
    var cashSummary by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(db) {
        db.cashSessions().observeSessions().collect { rows ->
            cashSummary = withContext(Dispatchers.IO) {
                val summaries = mutableListOf<String>()
                for (currency in currencies) {
                    val matching = rows.filter { it.currency == currency }
                    val closed = matching.filter { it.closedAt != null }
                    val expectedBySession = mutableMapOf<String, Long>()
                    for (row in matching) {
                        val movements = db.cashSessions().findMovements(row.sessionId)
                        var balance = row.openingMinor
                        for (movement in movements) {
                            balance = if (movement.type == "CASH_IN") Math.addExact(balance, movement.amountMinor)
                            else Math.subtractExact(balance, movement.amountMinor)
                        }
                        expectedBySession[row.sessionId] = balance
                    }
                    var expectedOpen = 0L
                    var expectedClosed = 0L
                    for (row in matching) {
                        val balance = expectedBySession[row.sessionId] ?: row.openingMinor
                        if (row.closedAt == null) expectedOpen = Math.addExact(expectedOpen, balance)
                        else expectedClosed = Math.addExact(expectedClosed, balance)
                    }
                    var counted = 0L
                    var variance = 0L
                    for (row in closed) {
                        val physical = row.countedClosingMinor ?: 0L
                        counted = Math.addExact(counted, physical)
                        variance = Math.addExact(variance, Math.subtractExact(physical, expectedBySession[row.sessionId] ?: row.openingMinor))
                    }
                    summaries.add(
                        currency + " · sesiones: " + matching.size + " (abiertas " + matching.count { it.closedAt == null } + ", cerradas " + closed.size + ")" +
                            "\nEsperado en cajas abiertas: " + money(expectedOpen) + "\nEsperado de cajas cerradas: " + money(expectedClosed) + "\nConteo físico (cerradas): " + money(counted) +
                            "\nDiferencia acumulada (cerradas): " + money(variance)
                    )
                }
                summaries
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("INFORMES DE CARTERA", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Resumen local · datos de este dispositivo", color = Muted, fontSize = 12.sp)
        }
        item {
            DataCard("Resumen general", "Clientes registrados: ${clients.size}\nPréstamos: ${loans.size}\nActivos: ${loans.count { it.status == "ACTIVE" }}\nPagos registrados: ${payments.size}")
        }
        item { Text("CONCILIACIÓN CONSOLIDADA DE CAJA", color = Gold, fontWeight = FontWeight.Bold) }
        items(cashSummary) { summary -> DataCard("Caja por moneda", summary) }
        items(currencies) { currency ->
            val currencyLoans = loans.filter { it.currency == currency }
            val currencyPayments = payments.filter { p -> currencyLoans.any { it.id == p.loanId } }
            val overdueRows = installments.filter { row ->
                row.dueDate < today && row.status != "PAID" &&
                    loans.any { it.id == row.loanId && it.currency == currency } &&
                    OverdueBalance.remainingMinor(row.totalMinor, row.paidPrincipalMinor, row.paidInterestMinor) > 0L
            }
            DataCard(
                title = "Informe · $currency",
                detail = "Préstamos: ${currencyLoans.size}\nCapital inicial: ${moneyTotal(currencyLoans.map { it.principalMinor })} $currency\nCapital pendiente: ${moneyTotal(currencyLoans.map { it.balanceMinor })} $currency\nPagos registrados: ${currencyPayments.size}\nTotal de pagos: ${moneyTotal(currencyPayments.map { it.amountMinor })} $currency\nCuotas vencidas: ${overdueRows.size}\nSaldo contractual vencido: ${moneyTotal(overdueRows.map { it.totalMinor - it.paidPrincipalMinor - it.paidInterestMinor })} $currency"
            )
        }
        item {
            DataCard("Alcance y control", "Los informes de cartera y la conciliación de caja se calculan con datos locales del dispositivo. La caja incluye sesiones, movimientos, conteo físico y diferencias de cierre. No constituye conciliación contable externa ni sincronización central; estas requieren backend y controles adicionales.")
        }
    }
}

@Composable
private fun HomeScreen(
    clients: List<ClientEntity>,
    loans: List<LoanEntity>,
    payments: List<PaymentEntity>,
    installments: List<LoanInstallmentEntity>
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
                Kpi("Clientes", clients.size.toString(), Color(0xFF51417D), Modifier.weight(1f))
                Kpi("Préstamos", loans.size.toString(), Color(0xFF174B83), Modifier.weight(1f))
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
                Kpi("Pagos", payments.size.toString(), Color(0xFF76551F), Modifier.weight(1f))
            }
        }

        item {
            val today = LocalDate.now().toString()
            val overdue = installments.filter { row ->
                row.status != "PAID" && row.dueDate < today &&
                    (row.totalMinor - row.paidPrincipalMinor - row.paidInterestMinor) > 0L
            }
            val overdueLoans = loans.filter { loan -> overdue.any { it.loanId == loan.id } }
            DataCard(
                title = "CUOTAS VENCIDAS · ${overdue.size}",
                detail = "Préstamos con cuotas vencidas: ${overdueLoans.size}\n" +
                    currencies.joinToString("\n") { currency ->
                        val amount = overdue.filter { row -> loans.any { it.id == row.loanId && it.currency == currency } }
                            .map { it.totalMinor - it.paidPrincipalMinor - it.paidInterestMinor }
                        "Saldo vencido $currency: ${moneyTotal(amount)} $currency"
                    }
            )
        }
        item {
            Text("RESUMEN EJECUTIVO", color = Gold, fontWeight = FontWeight.Bold)
        }

        items(currencies) { currency ->
            val activeLoans = loans.filter {
                it.currency == currency && it.status == "ACTIVE"
            }

            DataCard(
                title = "Cartera activa · $currency",
                detail = """
                    Préstamos activos: ${activeLoans.size}
                    Capital pendiente: ${moneyTotal(activeLoans.map { it.balanceMinor })} $currency
                """.trimIndent()
            )
        }

        item {
            Text("CARTERA FINANCIERA", color = Gold, fontWeight = FontWeight.Bold)
        }

        items(currencies) { currency ->
            val currencyLoans = loans.filter { it.currency == currency }

            DataCard(
                title = "Cartera total · $currency",
                detail = """
                    Préstamos registrados: ${currencyLoans.size}
                    Capital inicial: ${moneyTotal(currencyLoans.map { it.principalMinor })} $currency
                    Saldo pendiente: ${moneyTotal(currencyLoans.map { it.balanceMinor })} $currency
                """.trimIndent()
            )
        }

        item {
            Text("ACTIVIDAD RECIENTE", color = Gold, fontWeight = FontWeight.Bold)
        }

        val recentPayments = payments.sortedByDescending { it.createdAt }.take(5)

        if (recentPayments.isEmpty()) {
            item {
                EmptyState(
                    "Sin actividad todavía",
                    "Los últimos pagos aparecerán aquí cuando se registren."
                )
            }
        } else {
            items(recentPayments, key = { it.operationId }) { payment ->
                val loan = loans.firstOrNull { it.id == payment.loanId }
                val client = clients.firstOrNull { it.id == loan?.clientId }

                DataCard(
                    title = "Pago · ${payment.reference.ifBlank { "Sin referencia" }}",
                    detail = """
                        Cliente: ${client?.fullName ?: "No disponible"}
                        Importe: ${money(payment.amountMinor)} ${loan?.currency ?: ""}
                        Fecha: ${formatDate(payment.createdAt)}
                        Préstamo: ${payment.loanId.take(8)}
                    """.trimIndent()
                )
            }
        }

        item {
            Text("RECAUDACIÓN POR MONEDA", color = Gold, fontWeight = FontWeight.Bold)
        }

        items(currencies) { currency ->
            val currencyPayments = payments.filter { payment ->
                loans.any { it.id == payment.loanId && it.currency == currency }
            }

            DataCard(
                title = "Pagos recibidos · $currency",
                detail = """
                    Total registrado: ${moneyTotal(currencyPayments.map { it.amountMinor })} $currency
                    Operaciones: ${currencyPayments.size}
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
                    Estado: fase de construcción.

                    Backend compartido, contabilidad completa,
                    sincronización y autenticación multiusuario:
                    pendientes de integración.
                """.trimIndent()
            )
        }
    }
}
@Composable
private fun ClientsScreen(
    clients: List<ClientEntity>,
    onEdit: (ClientEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filteredClients = clients.filter { client ->
        client.fullName.contains(query, ignoreCase = true) ||
        client.documentNumber.contains(query, ignoreCase = true) ||
        client.phone.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar cliente") },
            placeholder = { Text("Nombre, cédula o teléfono") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        when {
            clients.isEmpty() -> {
                EmptyState(
                    "Todavía no hay clientes",
                    "Pulsa + para registrar el primer expediente."
                )
            }

            filteredClients.isEmpty() -> {
                EmptyState(
                    "No se encontraron resultados",
                    "Comprueba el nombre, documento o teléfono."
                )
            }

            else -> {
                Text(
                    "CLIENTES ENCONTRADOS: ${filteredClients.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredClients, key = { it.id }) { client ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEdit(client) },
                            colors = CardDefaults.cardColors(
                                containerColor = Panel
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    client.fullName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Documento: ${client.documentNumber}", color = Muted)
                                Text("Teléfono: ${client.phone}", color = Muted)
                                Text("Dirección: ${client.address}", color = Muted)
                                Text("Registrado: ${formatDate(client.createdAt)}", color = Muted)
                                Text(
                                    "Toca para editar el expediente",
                                    color = Gold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun LoansScreen(loans: List<LoanEntity>, clients: List<ClientEntity>) {
    var query by remember { mutableStateOf("") }

    val filteredLoans = loans.filter { loan ->
        val clientName = clients.firstOrNull {
            it.id == loan.clientId
        }?.fullName ?: ""

        clientName.contains(query, ignoreCase = true) ||
        loan.id.contains(query, ignoreCase = true) ||
        loan.currency.contains(query, ignoreCase = true) ||
        loan.method.contains(query, ignoreCase = true) ||
        loan.status.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar préstamo") },
            placeholder = { Text("Cliente, ID, moneda o estado") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        when {
            loans.isEmpty() -> {
                EmptyState(
                    "Todavía no hay préstamos",
                    "Registra un cliente antes de crear un préstamo."
                )
            }

            filteredLoans.isEmpty() -> {
                EmptyState(
                    "No se encontraron préstamos",
                    "Prueba con otro nombre, moneda o estado."
                )
            }

            else -> {
                Text(
                    "PRÉSTAMOS ENCONTRADOS: ${filteredLoans.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLoans, key = { it.id }) { loan ->
                        val client = clients.firstOrNull {
                            it.id == loan.clientId
                        }?.fullName ?: "Cliente no disponible"

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

                                PERÍODOS: ${loan.periods}
                                AMORTIZACIÓN: ${loan.method}
                                ESTADO: ${loan.status}
                                REGISTRO: ${formatDate(loan.createdAt)}
                                ID: ${loan.id}
                            """.trimIndent()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsScreen(
    payments: List<PaymentEntity>,
    loans: List<LoanEntity>,
    clients: List<ClientEntity>
) {
    var query by remember { mutableStateOf("") }

    val filteredPayments = payments
        .sortedByDescending { it.createdAt }
        .filter { payment ->
            val loan = loans.firstOrNull {
                it.id == payment.loanId
            }

            val client = clients.firstOrNull {
                it.id == loan?.clientId
            }

            payment.reference.contains(query, ignoreCase = true) ||
            payment.loanId.contains(query, ignoreCase = true) ||
            (client?.fullName ?: "").contains(query, ignoreCase = true) ||
            (loan?.currency ?: "").contains(query, ignoreCase = true)
        }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar pago") },
            placeholder = { Text("Cliente, recibo, préstamo o moneda") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        when {
            payments.isEmpty() -> {
                EmptyState(
                    "Sin pagos registrados",
                    "Los pagos aparecerán aquí cuando se registren."
                )
            }

            filteredPayments.isEmpty() -> {
                EmptyState(
                    "No se encontraron pagos",
                    "Intenta con otro nombre o referencia."
                )
            }

            else -> {
                Text(
                    "PAGOS ENCONTRADOS: ${filteredPayments.size}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPayments, key = { it.operationId }) { payment ->
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

                                IMPORTE
                                ${money(payment.amountMinor)} ${loan?.currency ?: ""}

                                FECHA
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
private fun ClientForm(
    initialClient: ClientEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember(initialClient) { mutableStateOf(initialClient?.fullName.orEmpty()) }
var doc by remember(initialClient) { mutableStateOf(initialClient?.documentNumber.orEmpty()) }
var phone by remember(initialClient) { mutableStateOf(initialClient?.phone.orEmpty()) }
var address by remember(initialClient) { mutableStateOf(initialClient?.address.orEmpty()) }
    AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialClient == null) "Nuevo cliente" else "Editar cliente") },
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
    onSave: (String, String, String, Int, String, String, String, String) -> Unit
) {
    var selected by remember(clients) { mutableStateOf(clients.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("12") }
    var currency by remember { mutableStateOf("DOP") }
    var method by remember { mutableStateOf("FRANCES") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var firstDueDate by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    val validDate = remember(firstDueDate) {
        runCatching { LocalDate.parse(firstDueDate) }.isSuccess
    }
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
            OutlinedTextField(periods, { periods = it }, label = { Text("Cantidad de cuotas") }, singleLine = true)
            Text("Frecuencia de pago", color = Gold)
            Row {
                listOf("WEEKLY" to "Semanal", "BIWEEKLY" to "Quincenal", "MONTHLY" to "Mensual").forEach { (value, label) ->
                    TextButton(onClick = { frequency = value }) {
                        Text(if (frequency == value) "[$label]" else label)
                    }
                }
            }
            OutlinedTextField(
                value = firstDueDate,
                onValueChange = { firstDueDate = it },
                label = { Text("Primer vencimiento (AAAA-MM-DD)") },
                singleLine = true,
                isError = !validDate
            )
            Row { listOf("DOP", "USD", "EUR").forEach { TextButton(onClick = { currency = it }) { Text(if (currency == it) "[$it]" else it) } } }
            Row { listOf("FRANCES", "ITALIANO", "AMERICANO").forEach { TextButton(onClick = { method = it }) { Text(if (method == it) "[$it]" else it) } } }
        } },
        confirmButton = {
            TextButton(
                enabled = clients.isNotEmpty() &&
                    amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                    rate.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } == true &&
                    periods.toIntOrNull()?.let { it in 1..600 } == true && validDate,
                onClick = { onSave(selected, amount, rate, periods.toInt(), currency, method, frequency, firstDueDate) }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PaymentForm(
    loans: List<LoanEntity>, onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var selected by remember(loans) { mutableStateOf(loans.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }; var reference by remember { mutableStateOf("") }
    var operationId by remember { mutableStateOf(UUID.randomUUID().toString()) }
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
        confirmButton = { TextButton(enabled = loans.isNotEmpty() && amount.toBigDecimalOrNull() != null, onClick = { onSave(operationId, selected, amount, reference) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun moneyTotal(amounts: Iterable<Long>): String =
    MinorUnitTotals.sum(amounts)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()

private fun money(minor: Long): String =
    BigDecimal.valueOf(minor, 2)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}


@Composable
private fun StaffManagementScreen(
    db: InfinityDatabase,
    session: StaffSession,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val service = remember(db) { StaffAuthenticationService(db.staffAccounts()) }
    var accounts by remember { mutableStateOf(emptyList<StaffAccountSummary>()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<StaffAccountSummary?>(null) }
    var selectedPermissions by remember { mutableStateOf<Set<Permission>>(emptySet()) }
    var savingPermissions by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canManage = AccessPolicy.allows(session.profile, Permission.STAFF_MANAGE)
    val canCustomizePermissions = AccessPolicy.allows(session.profile, Permission.ROLE_PERMISSION_MANAGE)
    val assignableRoles = StaffRole.values().filter { role ->
        (role != StaffRole.ADMINISTRATOR || session.profile.role == StaffRole.ADMINISTRATOR) &&
            AccessPolicy.effectivePermissions(AccessProfile(role)).all {
                AccessPolicy.allows(session.profile, it)
            }
    }
    var selectedRole by remember { mutableStateOf(assignableRoles.firstOrNull() ?: StaffRole.COLLECTOR) }

    LaunchedEffect(db, refreshKey) {
        accounts = withContext(Dispatchers.IO) { db.staffAccounts().getAllSummaries() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Personal y accesos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${accounts.size} cuentas registradas", color = Muted, fontSize = 12.sp)
                }
                if (canManage && assignableRoles.isNotEmpty()) {
                    Button(
                        onClick = { showCreate = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)
                    ) { Text("Nuevo empleado") }
                }
            }
        }
        items(accounts, key = { it.id }) { account ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("@${account.username} · ${account.role}", color = Muted, fontSize = 12.sp)
                        Text(
                            if (account.isActive) "Activo" else "Desactivado",
                            color = if (account.isActive) Gold else Muted,
                            fontSize = 11.sp
                        )
                    }
                    if (canManage && account.id != session.staffId &&
                        (account.role != StaffRole.ADMINISTRATOR.name ||
                            session.profile.role == StaffRole.ADMINISTRATOR)
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            db.withTransaction {
                                                service.setStaffAccountActive(session, account.id, !account.isActive)
                                                db.auditLogs().insert(
                                                AuditLogEntity(
                                                    actorStaffId = session.staffId,
                                                    actorUsername = session.username,
                                                    action = if (account.isActive) "STAFF_ACCOUNT_DISABLED" else "STAFF_ACCOUNT_ENABLED",
                                                    targetType = "STAFF_ACCOUNT",
                                                    targetId = account.id,
                                                    details = "username=${account.username}"
                                                )
                                            )
                                            }
                                        }
                                        refreshKey++
                                        onMessage(if (account.isActive) "Cuenta desactivada." else "Cuenta reactivada.")
                                    } catch (error: Exception) {
                                        onMessage(error.message ?: "No se pudo actualizar la cuenta.")
                                    }
                                }
                            }
                        ) {
                            Text(if (account.isActive) "Desactivar" else "Activar", color = Gold)
                        }
                    }
                }
            }
        }
    }

    editingAccount?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!savingPermissions) editingAccount = null },
            title = { Text("Permisos · ${target.displayName}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Activa solo las capacidades necesarias para este empleado.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Permission.entries.forEach { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = permission in selectedPermissions,
                                onCheckedChange = { checked ->
                                    selectedPermissions = if (checked) {
                                        selectedPermissions + permission
                                    } else {
                                        selectedPermissions - permission
                                    }
                                },
                                enabled = !savingPermissions
                            )
                            Text(permission.name.replace('_', ' '), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !savingPermissions,
                    onClick = {
                        savingPermissions = true
                        scope.launch {
                            try {
                                val targetEntity = withContext(Dispatchers.IO) {
                                    db.staffAccounts().findById(target.id)
                                } ?: throw IllegalArgumentException("La cuenta ya no existe.")
                                val role = StaffRole.valueOf(targetEntity.role)
                                val defaults = AccessPolicy.defaultPermissions(role)
                                val grants = selectedPermissions - defaults
                                val denials = defaults - selectedPermissions
                                withContext(Dispatchers.IO) {
                                    db.withTransaction {
                                        service.updateStaffPermissions(session, target.id, grants, denials)
                                        db.auditLogs().insert(
                                            AuditLogEntity(
                                                actorStaffId = session.staffId,
                                                actorUsername = session.username,
                                                action = "STAFF_PERMISSIONS_UPDATED",
                                                targetType = "STAFF_ACCOUNT",
                                                targetId = target.id,
                                                details = "grants=${grants.sortedBy { it.name }.joinToString(",")};denials=${denials.sortedBy { it.name }.joinToString(",")}"
                                            )
                                        )
                                    }
                                }
                                refreshKey++
                                editingAccount = null
                                onMessage("Permisos actualizados. La cuenta deberá iniciar sesión nuevamente para aplicar los cambios.")
                            } catch (error: Exception) {
                                onMessage(error.message ?: "No se pudieron actualizar los permisos.")
                            } finally {
                                savingPermissions = false
                            }
                        }
                    }
                ) { Text(if (savingPermissions) "Guardando…" else "Guardar", color = Gold) }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingPermissions,
                    onClick = { editingAccount = null }
                ) { Text("Cancelar") }
            }
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Crear cuenta de empleado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Nombre completo") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Nombre de usuario") },
                        singleLine = true
                    )
                    Text("Perfil de acceso", fontWeight = FontWeight.SemiBold)
                    assignableRoles.forEach { role ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            )
                            Text(role.name, color = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña (mínimo 12 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = displayName.isNotBlank() && username.isNotBlank() && password.length >= 12,
                    onClick = {
                        val submittedPassword = password
                        password = ""
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    db.withTransaction {
                                    service.createStaffAccount(
                                        actor = session,
                                        username = username,
                                        displayName = displayName,
                                        role = selectedRole,
                                        password = submittedPassword.toCharArray()
                                    )
                                    db.auditLogs().insert(
                                        AuditLogEntity(
                                            actorStaffId = session.staffId,
                                            actorUsername = session.username,
                                            action = "STAFF_ACCOUNT_CREATED",
                                            targetType = "STAFF_ACCOUNT",
                                            targetId = null,
                                            details = "username=${username.trim().lowercase(Locale.ROOT)};role=${selectedRole.name}"
                                        )
                                    )
                                    }
                                }
                                refreshKey++
                                showCreate = false
                                username = ""
                                displayName = ""
                                onMessage("Cuenta de empleado creada correctamente.")
                            } catch (error: Exception) {
                                onMessage(error.message ?: "No se pudo crear la cuenta.")
                            }
                        }
                    }
                ) { Text("Crear", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancelar") }
            }
        )
    }
}


@Composable
private fun AuditLogScreen(db: InfinityDatabase) {
    val entries by db.auditLogs().observeRecent(200).collectAsState(initial = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Registro de auditoría", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Últimos 200 eventos registrados en este dispositivo", color = Muted, fontSize = 12.sp)
            }
        }
        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Text("Todavía no hay eventos de auditoría.", color = Muted, modifier = Modifier.padding(16.dp))
                }
            }
        }
        items(entries, key = { it.id }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.action, color = Gold, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${entry.actorUsername} · ${entry.targetType}${entry.targetId?.let { " · $it" } ?: ""}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    if (entry.details.isNotBlank()) {
                        Text(entry.details, color = Muted, fontSize = 11.sp)
                    }
                    Text(
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(entry.createdAt)),
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
