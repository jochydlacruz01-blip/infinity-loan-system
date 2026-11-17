package com.infinityloan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0B1730)
private val Panel = Color(0xFF142746)
private val Gold = Color(0xFFD6AD55)
private val Muted = Color(0xFF9EADC4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InfinityDashboard() }
    }
}

@Composable
private fun InfinityDashboard() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            background = Navy,
            surface = Panel,
            onPrimary = Navy,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("INFINITY", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("LOAN SYSTEM", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Panel ejecutivo · Sucursal principal", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Text(" ADMIN ", color = Gold, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }
                Text("Resumen financiero", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("Cartera activa", "RD$ 0.00", Color(0xFF174B83), Modifier.weight(1f))
                    KpiCard("Cobrado hoy", "RD$ 0.00", Color(0xFF17634E), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("En mora", "RD$ 0.00", Color(0xFF775A1D), Modifier.weight(1f))
                    KpiCard("Clientes", "0", Color(0xFF51417D), Modifier.weight(1f))
                }
                SectionPanel("Actividad de cartera") {
                    Text("El gráfico aparecerá cuando se conecte la base de datos.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(90.dp).background(Color(0xFF1B3558), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text("SIN DATOS · MODO INICIAL", color = Muted, fontSize = 11.sp)
                    }
                }
                SectionPanel("Accesos principales") {
                    listOf("Clientes", "Préstamos", "Pagos y caja", "Cobros y rutas", "Contabilidad", "Informes", "Empleados", "Configuración").forEach {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("◆", color = Gold, modifier = Modifier.padding(end = 10.dp))
                            Text(it, color = Color.White, modifier = Modifier.weight(1f))
                            Text("›", color = Muted, fontSize = 20.sp)
                        }
                        HorizontalDivider(color = Color(0xFF2A3B55))
                    }
                }
                Text("Base inicial 0.1.0 · Datos de demostración vacíos", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
            Spacer(Modifier.height(9.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
