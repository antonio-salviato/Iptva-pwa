package com.example.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.model.ConnectionType
import com.example.iptv.model.PlaylistInfo
import com.example.iptv.model.XtreamAccountInfo
import com.example.iptv.model.XtreamCredentials
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceVariant

@Composable
fun PlaylistSetupDialog(
    playlistInfo: PlaylistInfo?,
    activeConnectionType: ConnectionType,
    xtreamCredentials: XtreamCredentials?,
    xtreamAccountInfo: XtreamAccountInfo?,
    onSaveM3uUrl: (String) -> Unit,
    onConnectXtream: (String, String, String) -> Unit,
    onTestConnection: () -> Unit,
    onRefreshList: () -> Unit,
    onRemoveConnection: () -> Unit,
    onLoadDemo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(activeConnectionType) }
    var isEditingConnection by remember {
        mutableStateOf(playlistInfo == null || playlistInfo.url.contains("demo.iptvcast.app"))
    }

    var inputM3uUrl by remember { mutableStateOf("") }

    var xtreamServer by remember { mutableStateOf(xtreamCredentials?.serverUrl ?: "") }
    var xtreamUsername by remember { mutableStateOf(xtreamCredentials?.username ?: "") }
    var xtreamPassword by remember { mutableStateOf(xtreamCredentials?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gerenciar Conexão IPTV",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Active Connection Card (if connected)
                if (!isEditingConnection && playlistInfo != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conexão Ativa",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Tipo: ${if (activeConnectionType == ConnectionType.XTREAM_CODES) "API Xtream Codes" else "Playlist M3U/M3U8"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Endereço: ${playlistInfo.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )

                            Text(
                                text = "Canais Carregados: ${playlistInfo.channelCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (activeConnectionType == ConnectionType.XTREAM_CODES && xtreamAccountInfo != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Status da Conta: ${xtreamAccountInfo.status}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Validade: ${xtreamAccountInfo.expDateFormatted ?: "Ilimitado"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Conexões Máximas: ${xtreamAccountInfo.maxConnections}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Management Action Buttons Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onTestConnection()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Testar", color = Color.Black, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onRefreshList()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MidnightSurfaceVariant),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Atualizar", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { isEditingConnection = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alterar", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onRemoveConnection()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remover", fontSize = 12.sp)
                        }
                    }
                } else {
                    // Method Selection Tabs
                    TabRow(
                        selectedTabIndex = if (selectedTab == ConnectionType.M3U) 0 else 1,
                        containerColor = MidnightSurfaceVariant,
                        contentColor = ElectricCyan,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[if (selectedTab == ConnectionType.M3U) 0 else 1]),
                                color = ElectricCyan
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == ConnectionType.M3U,
                            onClick = { selectedTab = ConnectionType.M3U },
                            text = { Text("Playlist M3U", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == ConnectionType.XTREAM_CODES,
                            onClick = { selectedTab = ConnectionType.XTREAM_CODES },
                            text = { Text("Xtream Codes API", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTab == ConnectionType.M3U) {
                        // M3U Playlist Form
                        Text(
                            text = "Informe a URL M3U/M3U8 da sua lista de canais IPTV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inputM3uUrl,
                            onValueChange = { inputM3uUrl = it },
                            label = { Text("URL M3U/M3U8") },
                            placeholder = { Text("http://servidor.com/get.php?username=...&password=...") },
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                focusedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playlist_url_input")
                        )

                        if (inputM3uUrl.startsWith("http://", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HttpWarningBadge()
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { if (inputM3uUrl.isNotBlank()) onSaveM3uUrl(inputM3uUrl) },
                            enabled = inputM3uUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_m3u_button")
                        ) {
                            Text("Conectar M3U", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Xtream Codes API Form
                        Text(
                            text = "Informe os dados de servidor, usuário e senha da sua conta Xtream Codes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = xtreamServer,
                            onValueChange = { xtreamServer = it },
                            label = { Text("Servidor (Ex: http://servidor.com:8080)") },
                            placeholder = { Text("http://servidor.exemplo:8080") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = ElectricCyan) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                focusedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("xtream_server_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = xtreamUsername,
                            onValueChange = { xtreamUsername = it },
                            label = { Text("Nome de Usuário") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ElectricCyan) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                focusedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("xtream_username_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = xtreamPassword,
                            onValueChange = { xtreamPassword = it },
                            label = { Text("Senha") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Mostrar senha"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                focusedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("xtream_password_input")
                        )

                        if (xtreamServer.startsWith("http://", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HttpWarningBadge()
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()) {
                                    onConnectXtream(xtreamServer, xtreamUsername, xtreamPassword)
                                }
                            },
                            enabled = xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_xtream_button")
                        ) {
                            Text("Conectar Xtream Codes", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Free Channels & Presets
                Text(
                    text = "Listas Gratuitas de Canais Ao Vivo:",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { onSaveM3uUrl("https://iptv-org.github.io/iptv/countries/br.m3u") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_pluto_tv_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Carregar Pluto TV & Canais BR (Gratuito)")
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { onSaveM3uUrl("https://raw.githubusercontent.com/iptv-org/iptv/master/streams/br.m3u") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_iptv_org_br_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Carregar Lista Alternativa BR (IPTV-Org)")
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onLoadDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_demo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testar com Canais Demo Gratuitos")
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        onCheckUpdate()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_updates_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificar Atualizações do App")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun HttpWarningBadge() {
    Surface(
        color = AmberAccent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AmberAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Aviso: Conexões HTTP (não cifradas) trafegam dados em texto simples.",
                style = MaterialTheme.typography.labelSmall,
                color = AmberAccent
            )
        }
    }
}
