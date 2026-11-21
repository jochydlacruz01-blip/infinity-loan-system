package com.infinityloan.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.infinityloan.security.StaffSession

@Composable
fun FinancialIdentityAuditScreen(db: InfinityDatabase, session: StaffSession) {
    var historyMode by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PersistedIdentityAuditReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(db, refreshKey) {
        loading = true
        error = null
        try {
            report = withContext(Dispatchers.IO) { db.auditPersistedFinancialIdentity() }
        } catch (e: Exception) {
            error = e.message ?: "No se pudo ejecutar la auditoría."
        } finally { loading = false }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { historyMode = false }) { Text("Integridad") }
            OutlinedButton(onClick = { historyMode = true }) { Text("Historial de auditoría") }
        }
        if (historyMode) { Text("Historial disponible desde el módulo Auditoría principal.") } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Integridad financiera", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { refreshKey++ }, enabled = !loading) { Text("Actualizar") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        report?.let { snapshot ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if(snapshot.dashboard.clean) "Auditoría sin incidencias detectadas" else "Requiere revisión", style = MaterialTheme.typography.titleMedium)
                    Text("Incidencias: ${snapshot.dashboard.totalFindings}")
                    Text("Clientes examinados: ${snapshot.clientsChecked}")
                    Text("Préstamos examinados: ${snapshot.loansChecked}")
                    Text("Pagos examinados: ${snapshot.paymentsChecked}")
                    Text("Asignaciones examinadas: ${snapshot.allocationsChecked}")
                    Text("Auditoría de solo lectura; no corrige ni cambia registros.")
                }
            }
            if(snapshot.dashboard.categories.isNotEmpty()) {
                Text("Incidencias por categoría", style = MaterialTheme.typography.titleMedium)
                snapshot.dashboard.categories.forEach { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.type.name.replace('_',' '))
                            Text(item.count.toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Text("Detalles", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshot.findings) { issue ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(issue.type.name.replace('_',' '), style = MaterialTheme.typography.titleSmall)
                                Text("Registro: ${issue.key}")
                                Text(issue.details)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
