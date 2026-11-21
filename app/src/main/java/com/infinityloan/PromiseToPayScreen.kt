package com.infinityloan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infinityloan.data.*
import com.infinityloan.finance.PromiseToPayStatus
import com.infinityloan.finance.PromiseToPayRecord
import com.infinityloan.finance.PromiseToPaySummaryCalculator
import com.infinityloan.finance.PromiseToPayOperationalReport
import com.infinityloan.finance.PromisePaymentEvidenceCalculator
import com.infinityloan.finance.PromiseEvidenceInput
import com.infinityloan.security.StaffAuthenticationService
import com.infinityloan.security.StaffSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val PromiseGold = Color(0xFFD6AD55)
private val PromisePanel = Color(0xFF142746)
private val PromiseMuted = Color(0xFF9EADC4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromiseToPayScreen(
    db: InfinityDatabase,
    session: StaffSession,
    authentication: StaffAuthenticationService,
    clients: List<ClientEntity>,
    loans: List<LoanEntity>,
    canCreate: Boolean,
    canResolve: Boolean,
    onMessage: (String) -> Unit
) {
    val promiseFlow = remember(session.staffId, session.profile.role) {
        if (session.profile.role == com.infinityloan.security.StaffRole.ADMINISTRATOR ||
            session.profile.role == com.infinityloan.security.StaffRole.SUPERVISOR
        ) db.promisesToPay().observeAll()
        else db.promisesToPay().observeForCollector(session.staffId)
    }
    val promises by promiseFlow.collectAsState(initial = emptyList())
    val linkFlow = remember { db.promisePaymentLinks().observeAll() }
    val paymentLinks by linkFlow.collectAsState(initial = emptyList())
    val names = remember(clients) { clients.associate { it.id to it.fullName } }
    val summary = remember(promises) {
        PromiseToPaySummaryCalculator.calculate(
            promises.map { item ->
                PromiseToPayRecord(
                    promiseId = item.promiseId,
                    loanId = item.loanId,
                    clientId = item.clientId,
                    collectorId = item.collectorId,
                    promisedAmountMinor = item.promisedAmountMinor,
                    currency = item.currency,
                    promisedDate = LocalDate.parse(item.promisedDate),
                    status = PromiseToPayStatus.valueOf(item.status),
                    note = item.note
                )
            },
            LocalDate.now()
        )
    }
    val operationalRows = remember(promises) {
        PromiseToPayOperationalReport.build(
            promises.map { item ->
                PromiseToPayRecord(
                    promiseId = item.promiseId,
                    loanId = item.loanId,
                    clientId = item.clientId,
                    collectorId = item.collectorId,
                    promisedAmountMinor = item.promisedAmountMinor,
                    currency = item.currency,
                    promisedDate = LocalDate.parse(item.promisedDate),
                    status = PromiseToPayStatus.valueOf(item.status),
                    note = item.note
                )
            },
            LocalDate.now()
        )
    }
    val supervisorView = session.profile.role == com.infinityloan.security.StaffRole.ADMINISTRATOR ||
        session.profile.role == com.infinityloan.security.StaffRole.SUPERVISOR
    val scope = rememberCoroutineScope()
    var createOpen by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf<PromiseToPayEntity?>(null) }
    var closingStatus by remember { mutableStateOf(PromiseToPayStatus.BROKEN) }
    var linking by remember { mutableStateOf<PromiseToPayEntity?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PromisePanel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Seguimiento de compromisos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${summary.openCount} abiertas · ${summary.overdueCount} vencidas · ${promises.size} en total", color = PromiseMuted)
                    val verifiedKept = promises.count { item ->
                        item.status == "KEPT" && paymentLinks.filter { it.promiseId == item.promiseId }
                            .fold(0L) { total, link -> Math.addExact(total, link.amountMinor) } >= item.promisedAmountMinor
                    }
                    val unverifiedLegacy = promises.count { item ->
                        item.status == "KEPT" && paymentLinks.filter { it.promiseId == item.promiseId }
                            .fold(0L) { total, link -> Math.addExact(total, link.amountMinor) } < item.promisedAmountMinor
                    }
                    Text("$verifiedKept cumplidas con evidencia · ${summary.brokenCount} incumplidas", color = PromiseMuted, fontSize = 12.sp)
                    if (unverifiedLegacy > 0) Text("$unverifiedLegacy antiguas sin evidencia conciliada", color = Color(0xFFFFC27A), fontSize = 12.sp)
                    listOf("DOP", "USD", "EUR").forEach { currency ->
                        val openMinor = summary.openMinorByCurrency[currency] ?: 0L
                        val overdueMinor = summary.overdueMinorByCurrency[currency] ?: 0L
                        if (openMinor > 0L || overdueMinor > 0L) {
                            Text(
                                "$currency · Abierto: ${BigDecimal.valueOf(openMinor, 2).setScale(2, RoundingMode.HALF_UP)} · Vencido: ${BigDecimal.valueOf(overdueMinor, 2).setScale(2, RoundingMode.HALF_UP)}",
                                color = PromiseGold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (canCreate && loans.any { it.status == "ACTIVE" }) {
                        Button(onClick = { createOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = PromiseGold, contentColor = Color(0xFF0B1730))) { Text("＋ Nueva promesa") }
                    }
                }
            }
        }
        if (supervisorView && operationalRows.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = PromisePanel)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Prioridad operativa por cobrador", color = PromiseGold, fontWeight = FontWeight.Bold)
                        operationalRows.forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(row.collectorId, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Abiertas: ${row.totalOpen} · Vencidas: ${row.overdue} · Hoy: ${row.dueToday} · Futuras: ${row.upcoming}", color = PromiseMuted, fontSize = 12.sp)
                                listOf("DOP", "USD", "EUR").forEach { currency ->
                                    val overdue = row.overdueMinorByCurrency[currency] ?: 0L
                                    val open = row.openMinorByCurrency[currency] ?: 0L
                                    if (open > 0L) Text("$currency · Abierto: ${BigDecimal.valueOf(open, 2).setScale(2, RoundingMode.HALF_UP)} · Vencido: ${BigDecimal.valueOf(overdue, 2).setScale(2, RoundingMode.HALF_UP)}", color = PromiseGold, fontSize = 11.sp)
                                }
                            }
                            Divider(color = PromiseMuted.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }
        if (promises.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = PromisePanel)) {
                Text("Todavía no hay promesas registradas.", color = PromiseMuted, modifier = Modifier.padding(16.dp))
            }
        }
        items(promises, key = { it.promiseId }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = PromisePanel)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(names[p.clientId] ?: "Cliente", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${p.currency} ${BigDecimal.valueOf(p.promisedAmountMinor, 2).setScale(2, RoundingMode.HALF_UP)}", color = PromiseGold, fontWeight = FontWeight.Bold)
                    val linkedMinor = paymentLinks.filter { it.promiseId == p.promiseId }
                        .fold(0L) { total, link -> Math.addExact(total, link.amountMinor) }
                    val evidence = remember(p, linkedMinor) {
                        PromisePaymentEvidenceCalculator.evaluate(
                            PromiseEvidenceInput(p.promiseId, p.promisedAmountMinor, LocalDate.parse(p.promisedDate), p.status, linkedMinor),
                            LocalDate.now()
                        )
                    }
                    Text("Fecha: ${p.promisedDate} · ${evidence.state.name}", color = PromiseMuted, fontSize = 12.sp)
                    Text("Pago conciliado: ${p.currency} ${BigDecimal.valueOf(linkedMinor, 2).setScale(2, RoundingMode.HALF_UP)} · Pendiente: ${p.currency} ${BigDecimal.valueOf(evidence.remainingMinor, 2).setScale(2, RoundingMode.HALF_UP)}", color = PromiseGold, fontSize = 12.sp)
                    if (p.note.isNotBlank()) Text(p.note, color = PromiseMuted, fontSize = 12.sp)
                    if (p.status == "OPEN" && canResolve) {
                        Row {
                            TextButton(onClick = { linking = p }) { Text("Vincular pago", color = PromiseGold) }
                            TextButton(onClick = { resolving = p; closingStatus = PromiseToPayStatus.BROKEN }) { Text("Incumplida", color = Color(0xFFFF8A80)) }
                            TextButton(onClick = { resolving = p; closingStatus = PromiseToPayStatus.CANCELLED }) { Text("Cancelar", color = PromiseMuted) }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        var loanId by remember { mutableStateOf(loans.firstOrNull { it.status == "ACTIVE" }?.id.orEmpty()) }
        var expanded by remember { mutableStateOf(false) }
        var amount by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val loan = loans.firstOrNull { it.id == loanId && it.status == "ACTIVE" }
        AlertDialog(
            onDismissRequest = { if (!busy) createOpen = false },
            title = { Text("Nueva promesa de pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = loan?.let { "${names[it.clientId] ?: "Cliente"} · ${it.currency}" } ?: "Seleccionar préstamo",
                            onValueChange = {}, readOnly = true, label = { Text("Préstamo activo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            loans.filter { it.status == "ACTIVE" }.forEach { item ->
                                DropdownMenuItem(text = { Text("${names[item.clientId] ?: "Cliente"} · ${item.currency}") }, onClick = { loanId = item.id; expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(amount, { amount = it }, label = { Text("Importe (${loan?.currency ?: ""})") }, singleLine = true)
                    OutlinedTextField(date, { date = it }, label = { Text("Fecha AAAA-MM-DD") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Nota opcional") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && loan != null && amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val minor = amount.toBigDecimal().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
                            withContext(Dispatchers.IO) { PromiseToPayService(db, authentication).create(session, loanId, minor, LocalDate.parse(date), note) }
                            createOpen = false
                            onMessage("Promesa registrada correctamente.")
                        } catch (e: Exception) { onMessage(e.message ?: "No se pudo registrar la promesa.") }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Guardando…" else "Registrar", color = PromiseGold) }
            },
            dismissButton = { TextButton(onClick = { if (!busy) createOpen = false }) { Text("Cancelar") } }
        )
    }

    linking?.let { p ->
        var operationId by remember(p.promiseId) { mutableStateOf("") }
        var amount by remember(p.promiseId) { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val linkedMinor = paymentLinks.filter { it.promiseId == p.promiseId }
            .fold(0L) { total, link -> Math.addExact(total, link.amountMinor) }
        val remainingMinor = (p.promisedAmountMinor - linkedMinor).coerceAtLeast(0L)
        val parsed = amount.toBigDecimalOrNull()
        AlertDialog(
            onDismissRequest = { if (!busy) linking = null },
            title = { Text("Conciliar pago real") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pendiente: ${p.currency} ${BigDecimal.valueOf(remainingMinor, 2).setScale(2, RoundingMode.HALF_UP)}")
                    OutlinedTextField(operationId, { operationId = it }, label = { Text("ID de operación del pago") }, singleLine = true)
                    OutlinedTextField(amount, { amount = it }, label = { Text("Importe a conciliar (${p.currency})") }, singleLine = true)
                    Text("El pago debe existir, pertenecer al mismo préstamo y no estar reversado.", color = PromiseMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && operationId.isNotBlank() && parsed != null && parsed > BigDecimal.ZERO &&
                        runCatching { parsed.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() <= remainingMinor }.getOrDefault(false),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val minor = amount.toBigDecimal().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
                                withContext(Dispatchers.IO) {
                                    PromiseToPayService(db, authentication).linkPayment(session, p.promiseId, operationId.trim(), minor)
                                }
                                onMessage("Pago conciliado con evidencia del libro contable.")
                                linking = null
                            } catch (e: Exception) {
                                onMessage(e.message ?: "No se pudo conciliar el pago.")
                            } finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "Conciliando…" else "Conciliar", color = PromiseGold) }
            },
            dismissButton = { TextButton(onClick = { if (!busy) linking = null }) { Text("Cancelar") } }
        )
    }

    resolving?.let { p ->
        var reason by remember(p.promiseId) { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) resolving = null },
            title = { Text("Confirmar: ${closingStatus.name}") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Observación") }, minLines = 2) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val ok = withContext(Dispatchers.IO) { PromiseToPayService(db, authentication).resolve(session, p.promiseId, closingStatus, reason) }
                            onMessage(if (ok) "Promesa actualizada." else "La promesa ya fue modificada.")
                            resolving = null
                        } catch (e: Exception) { onMessage(e.message ?: "No se pudo actualizar.") }
                        finally { busy = false }
                    }
                }) { Text("Confirmar", color = PromiseGold) }
            },
            dismissButton = { TextButton(onClick = { if (!busy) resolving = null }) { Text("Volver") } }
        )
    }
}
