package com.example.iptv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.cast.CastManager
import com.example.iptv.cast.DiscoveredCastDevice
import com.example.iptv.model.CastState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceVariant

@Composable
fun NativeCastButton(
    castState: CastState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ElectricCyan
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .testTag("cast_button")
    ) {
        if (castState.isConnected) {
            Icon(
                imageVector = Icons.Default.CastConnected,
                contentDescription = "Transmitindo para ${castState.deviceName}",
                tint = AmberAccent
            )
        } else if (castState.isConnecting) {
            CircularProgressIndicator(
                color = AmberAccent,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Cast,
                contentDescription = "Transmitir para TV / Chromecast",
                tint = tint
            )
        }
    }
}

@Composable
fun CastDeviceDialog(
    castManager: CastManager,
    currentChannel: com.example.iptv.model.Channel? = null,
    onDismiss: () -> Unit
) {
    val castState by castManager.castState.collectAsState()
    val devices by castManager.discoveredDevices.collectAsState()
    val isScanning by castManager.isScanning.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        castManager.startActiveScan()
        onDispose {
            castManager.stopActiveScan()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = null,
                        tint = if (castState.isConnected) AmberAccent else ElectricCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Transmitir na TV",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Scanning indicator bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isScanning) "Buscando na mesma rede Wi-Fi..." else "Rede Wi-Fi verificada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { castManager.startActiveScan() },
                        enabled = !isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (!isScanning) ElectricCyan else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Atualizar",
                            fontSize = 12.sp,
                            color = if (!isScanning) ElectricCyan else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Currently connected device card (if any)
                if (castState.isConnected) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AmberAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = MidnightSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = AmberAccent.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CastConnected,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.padding(8.dp).size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = castState.deviceName ?: "Dispositivo Conectado",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Conectado agora",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    castManager.disconnect()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Desconectar", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        val rokuIp = castManager.getCurrentRokuIp()
                        if (rokuIp != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MidnightSurface)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Roku TV ($rokuIp) - Receptor de Vídeo:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ao selecionar um canal, a transmissão inicia diretamente na TV.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { castManager.sendRokuKeyToTv("WakeUp") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Acordar TV", fontSize = 10.sp, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { castManager.sendRokuKeyToTv("Play") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Play / Pause", fontSize = 10.sp, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { castManager.sendRokuKeyToTv("Info") },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Info / Som", fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💡 Dica: Verifique se em Configurações > Sistema > Controle por apps móveis na sua Roku TV está como 'Permitir'.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = AmberAccent)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Aparelhos Encontrados (${devices.size}):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (devices.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidnightSurfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isScanning) "Procurando TV ou Chromecast..." else "Nenhum aparelho encontrado",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Verifique se o aparelho e o celular estão conectados no mesmo Wi-Fi.\n2. Verifique se o Chromecast/TV está ligado.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!isScanning) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { castManager.startActiveScan() },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Escanear Novamente", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((devices.size * 68).coerceAtMost(220).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.id }) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        castManager.selectDevice(device, currentChannel)
                                        onDismiss()
                                    }
                                    .testTag("device_${device.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (device.isConnected) AmberAccent.copy(alpha = 0.15f) else MidnightSurfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = if (device.isConnected) AmberAccent.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (device.isConnected) AmberAccent else ElectricCyan,
                                                modifier = Modifier.padding(6.dp).size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = device.description ?: "Google Cast / Smart TV",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (device.isConnected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Conectado",
                                            tint = AmberAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Cast,
                                            contentDescription = "Conectar",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var showManualIpDialog by remember { mutableStateOf(false) }
                var manualIpInput by remember { mutableStateOf("") }

                TextButton(
                    onClick = { showManualIpDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("+ Adicionar IP do Roku Manualmente", fontSize = 12.sp, color = ElectricCyan)
                }

                if (showManualIpDialog) {
                    AlertDialog(
                        onDismissRequest = { showManualIpDialog = false },
                        title = { Text("Conectar por IP do Roku", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Digite o endereço IP do seu Roku ou Philco Roku TV (exemplo: 172.16.0.203):", fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.OutlinedTextField(
                                    value = manualIpInput,
                                    onValueChange = { manualIpInput = it },
                                    label = { Text("Endereço IP") },
                                    placeholder = { Text("172.16.0.203") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (manualIpInput.isNotBlank()) {
                                        castManager.addManualRokuIp(manualIpInput.trim())
                                        showManualIpDialog = false
                                        manualIpInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Text("Adicionar", color = Color.Black)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showManualIpDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun CastStatusBar(
    castState: CastState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = castState.isConnected || castState.isConnecting,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Status Chromecast",
                        tint = AmberAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Row(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = if (castState.isConnecting) "Conectando TV..." else "Transmitindo: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = castState.deviceName ?: "Chromecast / Smart TV",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (castState.isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        castState.currentChannel?.let { channel ->
                            IconButton(onClick = onPlayPause) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause TV"
                                )
                            }
                        }
                        IconButton(onClick = onStop) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Parar transmissão",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
