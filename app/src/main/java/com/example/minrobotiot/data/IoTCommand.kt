package com.example.minrobotiot.data

data class IoTCommand(
    val displayText: String = "",
    val buzzerAction: String = "off", // "off", "on", "beep", "pattern"
    val buzzerDuration: Int = 1000, // Duration in milliseconds
    val timestamp: Long = System.currentTimeMillis()
)

data class EmergencyCommand(
    val action: String = "off", // "on", "off", "activate", "deactivate"
    val adminId: String = "", // Admin identifier for security
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "" // Optional reason for emergency
)

data class DeviceStatus(
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val currentDisplayText: String = "",
    val buzzerStatus: String = "off",
    val arduinoConnected: Boolean = false,
    val emergencyActive: Boolean = false,
    val waterLevel: Int = 0, // Water sensor reading (0-1023)
    val waterEmergencyActive: Boolean = false // Water-triggered emergency
) {
    constructor() : this(false, 0L, "", "off", false, false, 0, false)
}

data class WarningAlert(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: AlertSeverity = AlertSeverity.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val source: String = "system" // "water_sensor", "manual", "system"
)

enum class AlertSeverity {
    INFO,    // Blue - informational
    WARNING, // Orange - warning  
    CRITICAL // Red - critical/emergency
} 