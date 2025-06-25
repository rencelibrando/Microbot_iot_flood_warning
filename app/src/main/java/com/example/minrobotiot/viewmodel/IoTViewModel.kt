package com.example.minrobotiot.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.minrobotiot.data.DeviceStatus
import com.example.minrobotiot.data.IoTCommand
import com.example.minrobotiot.data.EmergencyCommand
import com.example.minrobotiot.repository.IoTRepository
import com.example.minrobotiot.repository.NetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class IoTViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IoTRepository(application.applicationContext)
    
    private val _uiState = MutableStateFlow(IoTUiState())
    val uiState: StateFlow<IoTUiState> = _uiState.asStateFlow()
    
    private val _deviceStatus = MutableStateFlow(DeviceStatus(isOnline = false)) // Start with unknown status
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _emergencyState = MutableStateFlow(EmergencyState())
    val emergencyState: StateFlow<EmergencyState> = _emergencyState.asStateFlow()
    
    private var deviceStatusObserverStarted = false
    private var emergencyObserverStarted = false
    
    companion object {
        private const val TAG = "IoTViewModel"
    }
    
    init {
        Log.d(TAG, "ViewModel initialized")
        checkConnectionStatus()
        // Delay device status observation to allow network connection to establish
        viewModelScope.launch {
            delay(1000) // Give 1 second for network to establish
            observeDeviceStatus()
            observeEmergencyStatus()
            observeWarningAlerts()
        }
    }
    
    private fun observeDeviceStatus() {
        if (deviceStatusObserverStarted) {
            Log.d(TAG, "Device status observer already started")
            return
        }
        deviceStatusObserverStarted = true
        
        Log.d(TAG, "Starting device status observation")
        viewModelScope.launch {
            try {
                repository.getDeviceStatus().collect { status ->
                    Log.d(TAG, "Device status received: isOnline=${status.isOnline}, displayText='${status.currentDisplayText}', buzzer='${status.buzzerStatus}', emergency=${status.emergencyActive}")
                    _deviceStatus.value = status
                    _connectionState.value = _connectionState.value.copy(
                        isConnectedToFirebase = true,
                        firebaseError = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device status observation failed", e)
                _connectionState.value = _connectionState.value.copy(
                    isConnectedToFirebase = false,
                    firebaseError = e.message ?: "Firebase connection failed"
                )
                
                // Don't immediately mark device as offline due to Firebase errors
                // Keep the last known status unless it's a network issue
                if (e is NetworkException) {
                    Log.w(TAG, "Network issue detected, marking device offline")
                    _deviceStatus.value = _deviceStatus.value.copy(isOnline = false)
                }
            }
        }
    }
    
    private fun observeEmergencyStatus() {
        if (emergencyObserverStarted) {
            Log.d(TAG, "Emergency status observer already started")
            return
        }
        emergencyObserverStarted = true
        
        Log.d(TAG, "Starting emergency status observation")
        viewModelScope.launch {
            try {
                repository.getEmergencyStatus().collect { emergencyCommand ->
                    Log.d(TAG, "Emergency command received: action=${emergencyCommand.action}, adminId=${emergencyCommand.adminId}")
                    // Update emergency state based on received command
                    val isActive = emergencyCommand.action == "on" || emergencyCommand.action == "activate"
                    _emergencyState.value = _emergencyState.value.copy(
                        isActive = isActive,
                        lastCommand = emergencyCommand
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Emergency status observation failed", e)
            }
        }
    }
    
    private fun checkConnectionStatus() {
        viewModelScope.launch {
            val isOnline = repository.isOnline()
            Log.d(TAG, "Network connection status: $isOnline")
            _connectionState.value = _connectionState.value.copy(isOnline = isOnline)
            
            // If we go offline, don't immediately mark device as offline
            // Device might still be online on local network
            if (!isOnline) {
                // Only mark as offline if we were previously tracking status
                if (deviceStatusObserverStarted) {
                    Log.w(TAG, "Lost network connection, marking device offline")
                    _deviceStatus.value = _deviceStatus.value.copy(isOnline = false)
                }
            }
        }
    }
    
    fun refreshConnectionStatus() {
        Log.d(TAG, "Refreshing connection status")
        checkConnectionStatus()
        // Try to reconnect to Firebase if needed
        if (!deviceStatusObserverStarted || !connectionState.value.isConnectedToFirebase) {
            Log.d(TAG, "Restarting device status observer")
            deviceStatusObserverStarted = false
            observeDeviceStatus()
        }
        
        // Restart emergency observer if needed
        if (!emergencyObserverStarted) {
            Log.d(TAG, "Restarting emergency status observer")
            emergencyObserverStarted = false
            observeEmergencyStatus()
        }
    }
    
    fun updateDisplayText(text: String) {
        _uiState.value = _uiState.value.copy(displayText = text)
    }
    
    fun updateBuzzerDuration(duration: Int) {
        _uiState.value = _uiState.value.copy(buzzerDuration = duration)
    }
    
    fun updateSelectedBuzzerAction(action: String) {
        _uiState.value = _uiState.value.copy(selectedBuzzerAction = action)
    }
    
    fun updateEmergencyReason(reason: String) {
        _emergencyState.value = _emergencyState.value.copy(reason = reason)
    }
    
    fun addWarningAlert(alert: com.example.minrobotiot.data.WarningAlert) {
        val currentAlerts = _emergencyState.value.warningAlerts.toMutableList()
        currentAlerts.add(alert)
        _emergencyState.value = _emergencyState.value.copy(warningAlerts = currentAlerts)
    }
    
    fun dismissWarningAlert(alertId: String) {
        viewModelScope.launch {
            repository.dismissWarningAlert(alertId)
                .onSuccess {
                    Log.d(TAG, "Warning alert dismissed successfully")
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to dismiss warning alert", exception)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to dismiss alert: ${exception.message}"
                    )
                }
        }
    }
    
    private fun observeWarningAlerts() {
        Log.d(TAG, "Starting warning alerts observation")
        viewModelScope.launch {
            try {
                repository.getWarningAlerts().collect { alerts ->
                    Log.d(TAG, "Warning alerts received: ${alerts.size} alerts")
                    _emergencyState.value = _emergencyState.value.copy(warningAlerts = alerts)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Warning alerts observation failed", e)
            }
        }
    }
    
    fun activateManualEmergency() {
        val currentState = _emergencyState.value
        
        if (!validateNetworkConnection()) return
        
        val emergencyCommand = EmergencyCommand(
            action = "activate",
            adminId = "app_user", // Simplified admin ID
            reason = currentState.reason.ifEmpty { "Manual emergency activated via app" }
        )
        
        _emergencyState.value = _emergencyState.value.copy(manualEmergencyActive = true)
        sendEmergencyCommand(emergencyCommand)
    }
    
    fun deactivateManualEmergency() {
        val currentState = _emergencyState.value
        
        if (!validateNetworkConnection()) return
        
        val emergencyCommand = EmergencyCommand(
            action = "deactivate",
            adminId = "app_user", // Simplified admin ID
            reason = currentState.reason.ifEmpty { "Manual emergency deactivated via app" }
        )
        
        _emergencyState.value = _emergencyState.value.copy(manualEmergencyActive = false)
        sendEmergencyCommand(emergencyCommand)
    }
    
    // Legacy method for backward compatibility
    fun activateEmergency() {
        activateManualEmergency()
    }
    
    fun deactivateEmergency() {
        deactivateManualEmergency()
    }

    
    private fun sendEmergencyCommand(command: EmergencyCommand) {
        viewModelScope.launch {
            Log.d(TAG, "Sending emergency command: action='${command.action}', adminId='${command.adminId}'")
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                errorMessage = null,
                successMessage = null
            )
            
            repository.sendEmergencyCommand(command)
                .onSuccess {
                    Log.d(TAG, "Emergency command sent successfully")
                    val message = if (command.action == "activate") {
                        "🚨 EMERGENCY ACTIVATED!\n\nEmergency system has been activated. LED and siren are now active on the device."
                    } else {
                        "✅ Emergency Deactivated\n\nEmergency system has been safely deactivated."
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = message
                    )
                    
                    _emergencyState.value = _emergencyState.value.copy(
                        isActive = command.action == "activate",
                        lastCommand = command
                    )
                }
                .onFailure { exception ->
                    Log.e(TAG, "Emergency command failed", exception)
                    val errorMessage = "🚨 Emergency Command Failed\n\n${exception.message}\n\n⚠️ Emergency systems may not respond. Check your connection and try again."
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMessage
                    )
                }
        }
    }
    
    fun sendDisplayCommand() {
        if (!validateNetworkConnection()) return
        
        val currentState = _uiState.value
        val command = IoTCommand(
            displayText = currentState.displayText,
            buzzerAction = "off",
            buzzerDuration = 0
        )
        sendCommand(command)
    }
    
    fun sendBuzzerCommand(action: String) {
        if (!validateNetworkConnection()) return
        
        val currentState = _uiState.value
        val command = IoTCommand(
            displayText = "",
            buzzerAction = action,
            buzzerDuration = currentState.buzzerDuration
        )
        sendCommand(command)
    }
    
    fun sendCombinedCommand() {
        if (!validateNetworkConnection()) return
        
        val currentState = _uiState.value
        val command = IoTCommand(
            displayText = currentState.displayText,
            buzzerAction = currentState.selectedBuzzerAction,
            buzzerDuration = currentState.buzzerDuration
        )
        sendCommand(command)
    }
    
    private fun validateNetworkConnection(): Boolean {
        if (!repository.isOnline()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "❌ No Internet Connection\n\nPlease check your network settings:\n• WiFi or mobile data is enabled\n• Internet connection is stable\n• Try toggling airplane mode\n\nThen tap 'Retry' to try again.",
                isLoading = false
            )
            _connectionState.value = _connectionState.value.copy(isOnline = false)
            return false
        }
        return true
    }
    
    private fun sendCommand(command: IoTCommand) {
        viewModelScope.launch {
            Log.d(TAG, "Sending command: displayText='${command.displayText}', buzzerAction='${command.buzzerAction}'")
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                errorMessage = null,
                successMessage = null
            )
            
            // Double-check network before sending
            if (!repository.isOnline()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "❌ Connection Lost\n\nYour internet connection was lost while sending the command. Please check your network and try again."
                )
                return@launch
            }
            
            repository.sendCommand(command)
                .onSuccess {
                    Log.d(TAG, "Command sent successfully")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        lastSentCommand = command,
                        successMessage = "✅ Command sent successfully!\n\nYour IoT device should respond shortly."
                    )
                    _connectionState.value = _connectionState.value.copy(
                        isConnectedToFirebase = true,
                        firebaseError = null
                    )
                }
                .onFailure { exception ->
                    Log.e(TAG, "Command failed", exception)
                    val errorMessage = when (exception) {
                        is NetworkException -> "🌐 ${exception.message}\n\n💡 Troubleshooting:\n• Check WiFi/mobile data\n• Restart your router\n• Try again in a few moments"
                        else -> {
                            val baseMessage = exception.message ?: "Unknown error occurred"
                            when {
                                baseMessage.contains("PERMISSION_DENIED") -> 
                                    "🔒 Access Denied\n\nFirebase database access was denied. Please contact the app administrator."
                                baseMessage.contains("timeout") -> 
                                    "⏱️ Connection Timeout\n\nThe request took too long. Please check your internet speed and try again."
                                baseMessage.contains("UNAVAILABLE") -> 
                                    "🚫 Service Unavailable\n\nFirebase service is temporarily down. Please try again later."
                                else -> "❌ $baseMessage\n\n💡 Try:\n• Check your internet connection\n• Restart the app\n• Try again in a moment"
                            }
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMessage
                    )
                    
                    // Update connection state based on error type
                    if (exception is NetworkException) {
                        _connectionState.value = _connectionState.value.copy(isOnline = false)
                    } else {
                        _connectionState.value = _connectionState.value.copy(
                            isConnectedToFirebase = false,
                            firebaseError = exception.message
                        )
                    }
                }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
    
    fun retryLastAction() {
        clearMessages()
        checkConnectionStatus()
        
        // Refresh device status observation
        if (!connectionState.value.isConnectedToFirebase) {
            deviceStatusObserverStarted = false
            observeDeviceStatus()
        }
        
        val lastCommand = _uiState.value.lastSentCommand
        if (lastCommand != null) {
            sendCommand(lastCommand)
        }
    }
    
    // Add a manual refresh function for debugging
    fun forceRefreshDeviceStatus() {
        Log.d(TAG, "Force refreshing device status")
        deviceStatusObserverStarted = false
        observeDeviceStatus()
    }
}

data class IoTUiState(
    val displayText: String = "",
    val selectedBuzzerAction: String = "off",
    val buzzerDuration: Int = 1000,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val lastSentCommand: IoTCommand? = null
)

data class ConnectionState(
    val isOnline: Boolean = true,
    val isConnectedToFirebase: Boolean = true,
    val firebaseError: String? = null
)

data class EmergencyState(
    val isActive: Boolean = false,
    val manualEmergencyActive: Boolean = false, // Manual emergency separate from water emergency
    val reason: String = "",
    val lastCommand: EmergencyCommand? = null,
    val warningAlerts: List<com.example.minrobotiot.data.WarningAlert> = emptyList()
) 