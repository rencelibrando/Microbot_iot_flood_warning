package com.example.minrobotiot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minrobotiot.data.DeviceStatus
import com.example.minrobotiot.data.WarningAlert
import com.example.minrobotiot.data.AlertSeverity
import com.example.minrobotiot.viewmodel.IoTUiState
import com.example.minrobotiot.viewmodel.IoTViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoTControlScreen() {
    val context = LocalContext.current
    val viewModel: IoTViewModel = viewModel { IoTViewModel(context.applicationContext as android.app.Application) }
    val uiState by viewModel.uiState.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val emergencyState by viewModel.emergencyState.collectAsState()
    
    var statusExpanded by remember { mutableStateOf(true) }
    var emergencyExpanded by remember { mutableStateOf(false) }
    var warningExpanded by remember { mutableStateOf(true) }
    var controlsExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        if (uiState.successMessage != null || uiState.errorMessage != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearMessages()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Modern App Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Flood Warning Controller",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // System Status Section
        ModernExpandableCard(
            title = "System Status",
            expanded = statusExpanded,
            onExpandChange = { statusExpanded = it },
            containerColor = when {
                !connectionState.isOnline -> MaterialTheme.colorScheme.errorContainer
                !deviceStatus.isOnline -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            ModernStatusContent(
                connectionState = connectionState,
                deviceStatus = deviceStatus,
                onRefresh = { viewModel.refreshConnectionStatus() }
            )
        }
        
        // Warning Alerts Section
        if (emergencyState.warningAlerts.isNotEmpty()) {
            ModernExpandableCard(
                title = "Alerts (${emergencyState.warningAlerts.size})",
                expanded = warningExpanded,
                onExpandChange = { warningExpanded = it },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                ModernWarningAlertsContent(
                    alerts = emergencyState.warningAlerts,
                    onDismissAlert = { viewModel.dismissWarningAlert(it) }
                )
            }
        }
        
        // Emergency Control Section
        ModernExpandableCard(
            title = "Emergency Control",
            expanded = emergencyExpanded,
            onExpandChange = { emergencyExpanded = it },
            containerColor = if (emergencyState.isActive) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainer
        ) {
            ModernEmergencyControlContent(
                emergencyState = emergencyState,
                deviceStatus = deviceStatus,
                onReasonChange = { viewModel.updateEmergencyReason(it) },
                onActivateEmergency = { viewModel.activateManualEmergency() },
                onDeactivateEmergency = { viewModel.deactivateManualEmergency() },
                enabled = connectionState.isOnline && !uiState.isLoading
            )
        }
        
        // Device Controls Section
        ModernExpandableCard(
            title = "Device Controls",
            expanded = controlsExpanded,
            onExpandChange = { controlsExpanded = it },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            ModernDeviceControlsContent(
                uiState = uiState,
                onTextChange = { viewModel.updateDisplayText(it) },
                onSendLCD = { viewModel.sendDisplayCommand() },
                onActionChange = { viewModel.updateSelectedBuzzerAction(it) },
                onDurationChange = { viewModel.updateBuzzerDuration(it) },
                onSendBuzzer = { action -> viewModel.sendBuzzerCommand(action) },
                enabled = connectionState.isOnline && !uiState.isLoading && !emergencyState.isActive
            )
        }
        
        // Messages
        if (uiState.errorMessage != null) {
            ModernErrorMessageCard(
                errorMessage = uiState.errorMessage!!,
                onDismiss = { viewModel.clearMessages() },
                onRetry = { viewModel.retryLastAction() },
                showRetry = !connectionState.isOnline || !connectionState.isConnectedToFirebase
            )
        }
        
        if (uiState.successMessage != null) {
            ModernSuccessMessageCard(
                message = uiState.successMessage!!,
                onDismiss = { viewModel.clearMessages() }
            )
        }
        
        // Offline Help
        if (!connectionState.isOnline) {
            ModernOfflineHelpCard()
        }
    }
}

@Composable
fun ModernExpandableCard(
    title: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Modern Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Content with smooth animation
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ModernStatusContent(
    connectionState: com.example.minrobotiot.viewmodel.ConnectionState,
    deviceStatus: DeviceStatus,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModernStatusIndicator(
                label = "Internet",
                isOnline = connectionState.isOnline,
                modifier = Modifier.weight(1f)
            )
            ModernStatusIndicator(
                label = "Firebase",
                isOnline = connectionState.isConnectedToFirebase,
                modifier = Modifier.weight(1f)
            )
            ModernStatusIndicator(
                label = "Device",
                isOnline = deviceStatus.isOnline || deviceStatus.arduinoConnected,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Water sensor info with modern design
        if (deviceStatus.waterLevel > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (deviceStatus.waterEmergencyActive) 
                        MaterialTheme.colorScheme.errorContainer 
                    else 
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Water Level",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${deviceStatus.waterLevel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (deviceStatus.waterEmergencyActive) {
                        Text(
                            text = "HIGH ALERT",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        
        // Last seen info
        Text(
            text = "Last seen: ${if (deviceStatus.lastSeen > 0) "Recently" else "Never"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Modern refresh button
        FilledTonalButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Refresh, 
                contentDescription = null, 
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh Status")
        }
    }
}

@Composable
fun ModernStatusIndicator(
    label: String, 
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(5.dp)
                    )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModernWarningAlertsContent(
    alerts: List<WarningAlert>,
    onDismissAlert: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        alerts.forEach { alert ->
            ModernWarningAlertItem(
                alert = alert,
                onDismiss = { onDismissAlert(alert.id) }
            )
        }
    }
}

@Composable
fun ModernWarningAlertItem(
    alert: WarningAlert,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (alert.severity) {
                AlertSeverity.INFO -> MaterialTheme.colorScheme.surfaceContainer
                AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Source: ${alert.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
fun ModernEmergencyControlContent(
    emergencyState: com.example.minrobotiot.viewmodel.EmergencyState,
    deviceStatus: DeviceStatus,
    onReasonChange: (String) -> Unit,
    onActivateEmergency: () -> Unit,
    onDeactivateEmergency: () -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Emergency status display
        ModernEmergencyStatusDisplay(
            manualActive = emergencyState.manualEmergencyActive,
            waterActive = deviceStatus.waterEmergencyActive,
            overallActive = emergencyState.isActive
        )
        
        // Emergency control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onActivateEmergency,
                modifier = Modifier.weight(1f),
                enabled = enabled && !emergencyState.manualEmergencyActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ACTIVATE", fontWeight = FontWeight.Bold)
            }
            
            OutlinedButton(
                onClick = onDeactivateEmergency,
                modifier = Modifier.weight(1f),
                enabled = enabled && emergencyState.manualEmergencyActive,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("DEACTIVATE")
            }
        }
        
        // Information text
        Text(
            text = "Emergency alerts activate automatically when high water is detected. Manual controls are independent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ModernEmergencyStatusDisplay(
    manualActive: Boolean,
    waterActive: Boolean,
    overallActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overallActive) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Emergency Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernStatusIndicator("Manual", manualActive, Modifier.weight(1f))
                ModernStatusIndicator("Water", waterActive, Modifier.weight(1f))
                ModernStatusIndicator("Overall", overallActive, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ModernErrorMessageCard(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    showRetry: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showRetry) {
                    FilledTonalButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry")
                    }
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun ModernSuccessMessageCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}

@Composable
fun ModernOfflineHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Offline Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "You're currently offline. To use IoT controls:",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "• Connect to WiFi or enable mobile data\n• Check your internet connection\n• Make sure Firebase is accessible\n• Try refreshing the connection status",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ModernDeviceControlsContent(
    uiState: com.example.minrobotiot.viewmodel.IoTUiState,
    onTextChange: (String) -> Unit,
    onSendLCD: () -> Unit,
    onActionChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSendBuzzer: (String) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ModernLCDControlCard(
            uiState = uiState,
            onTextChange = onTextChange,
            onSendLCD = onSendLCD,
            enabled = enabled
        )
        
        ModernBuzzerControlCard(
            uiState = uiState,
            onActionChange = onActionChange,
            onDurationChange = onDurationChange,
            onSendBuzzer = onSendBuzzer,
            enabled = enabled
        )
    }
}

@Composable
fun ModernLCDControlCard(
    uiState: com.example.minrobotiot.viewmodel.IoTUiState,
    onTextChange: (String) -> Unit,
    onSendLCD: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LCD Display Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = uiState.displayText,
                onValueChange = onTextChange,
                label = { Text("Display Message") },
                placeholder = { Text("Enter message...") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                supportingText = { 
                    Text("${uiState.displayText.length}/32 characters")
                },
                isError = uiState.displayText.length > 32,
                shape = RoundedCornerShape(8.dp)
            )
            
            Button(
                onClick = onSendLCD,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && uiState.displayText.isNotBlank() && uiState.displayText.length <= 32,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Send to LCD", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ModernBuzzerControlCard(
    uiState: com.example.minrobotiot.viewmodel.IoTUiState,
    onActionChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSendBuzzer: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Buzzer Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Action selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Action:", style = MaterialTheme.typography.bodyMedium)
                
                val actions = listOf("beep", "alarm", "siren")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.forEach { action ->
                        FilterChip(
                            onClick = { onActionChange(action) },
                            label = { Text(action.uppercase()) },
                            selected = uiState.selectedBuzzerAction == action,
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        )
                    }
                }
            }
            
            // Duration input
            if (uiState.selectedBuzzerAction != "beep") {
                OutlinedTextField(
                    value = uiState.buzzerDuration.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onDurationChange(it) }
                    },
                    label = { Text("Duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSendBuzzer("beep") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Quick Beep")
                }
                
                Button(
                    onClick = { onSendBuzzer(uiState.selectedBuzzerAction) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Send ${uiState.selectedBuzzerAction.uppercase()}")
                }
            }
        }
    }
}

 