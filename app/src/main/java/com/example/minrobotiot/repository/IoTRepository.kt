package com.example.minrobotiot.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.minrobotiot.data.DeviceStatus
import com.example.minrobotiot.data.IoTCommand
import com.example.minrobotiot.data.EmergencyCommand
import com.example.minrobotiot.data.WarningAlert
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class IoTRepository(private val context: Context) {
    // Configure Firebase database with the correct regional URL
    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://minrobot-387a5-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }
    
    private val commandsRef: DatabaseReference by lazy { database.getReference("iot_commands") }
    private val statusRef: DatabaseReference by lazy { database.getReference("device_status") }
    private val emergencyRef: DatabaseReference by lazy { database.getReference("emergency_commands") }
    private val warningAlertsRef: DatabaseReference by lazy { database.getReference("warning_alerts") }
    
    suspend fun sendCommand(command: IoTCommand): Result<Unit> {
        return try {
            // Check network connectivity first
            if (!isNetworkAvailable()) {
                return Result.failure(NetworkException("No internet connection. Please check your network settings and try again."))
            }
            
            commandsRef.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is UnknownHostException -> "Unable to connect to Firebase. Please check your internet connection."
                is SocketTimeoutException -> "Connection timeout. Please check your internet connection and try again."
                is IOException -> "Network error occurred. Please verify your internet connection."
                is SecurityException -> "Permission denied. Please check Firebase security rules."
                else -> when {
                    e.message?.contains("PERMISSION_DENIED") == true -> 
                        "Access denied. Please check Firebase database rules and authentication."
                    e.message?.contains("NETWORK_ERROR") == true -> 
                        "Network error. Please check your internet connection."
                    e.message?.contains("UNAVAILABLE") == true -> 
                        "Firebase service temporarily unavailable. Please try again later."
                    e.message?.contains("TIMEOUT") == true -> 
                        "Request timed out. Please check your connection and try again."
                    e.message?.contains("different region") == true ->
                        "Database region mismatch. Using Asia Southeast region URL."
                    else -> "Failed to send command: ${e.message ?: "Unknown error occurred"}"
                }
            }
            Result.failure(IoTException(errorMessage, e))
        }
    }
    
    suspend fun sendEmergencyCommand(command: EmergencyCommand): Result<Unit> {
        return try {
            // Check network connectivity first
            if (!isNetworkAvailable()) {
                return Result.failure(NetworkException("No internet connection. Emergency command cannot be sent."))
            }
            
            emergencyRef.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is UnknownHostException -> "Unable to connect to Firebase. Emergency command failed to send."
                is SocketTimeoutException -> "Connection timeout. Emergency command failed to send."
                is IOException -> "Network error occurred. Emergency command failed to send."
                is SecurityException -> "Permission denied. Check Firebase security rules for emergency commands."
                else -> when {
                    e.message?.contains("PERMISSION_DENIED") == true -> 
                        "Access denied for emergency commands. Check admin permissions."
                    e.message?.contains("NETWORK_ERROR") == true -> 
                        "Network error. Emergency command failed to send."
                    e.message?.contains("UNAVAILABLE") == true -> 
                        "Firebase service temporarily unavailable. Emergency command failed."
                    else -> "Failed to send emergency command: ${e.message ?: "Unknown error occurred"}"
                }
            }
            Result.failure(IoTException(errorMessage, e))
        }
    }
    
    fun getDeviceStatus(): Flow<DeviceStatus> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(DeviceStatus::class.java) ?: DeviceStatus()
                trySend(status)
            }
            
            override fun onCancelled(error: DatabaseError) {
                val errorMessage = when (error.code) {
                    DatabaseError.PERMISSION_DENIED -> "Access denied. Please check Firebase permissions."
                    DatabaseError.NETWORK_ERROR -> "Network error. Please check your internet connection."
                    DatabaseError.UNAVAILABLE -> "Firebase service temporarily unavailable."
                    DatabaseError.USER_CODE_EXCEPTION -> "An unexpected error occurred."
                    else -> "Database error: ${error.message}"
                }
                close(IoTException(errorMessage, error.toException()))
            }
        }
        
        statusRef.addValueEventListener(listener)
        awaitClose { statusRef.removeEventListener(listener) }
    }
    
    fun getEmergencyStatus(): Flow<EmergencyCommand> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val command = snapshot.getValue(EmergencyCommand::class.java) ?: EmergencyCommand()
                trySend(command)
            }
            
            override fun onCancelled(error: DatabaseError) {
                val errorMessage = when (error.code) {
                    DatabaseError.PERMISSION_DENIED -> "Access denied for emergency status."
                    DatabaseError.NETWORK_ERROR -> "Network error while monitoring emergency status."
                    DatabaseError.UNAVAILABLE -> "Firebase service unavailable."
                    else -> "Error monitoring emergency status: ${error.message}"
                }
                close(IoTException(errorMessage, error.toException()))
            }
        }
        
        emergencyRef.addValueEventListener(listener)
        awaitClose { emergencyRef.removeEventListener(listener) }
    }
    
    suspend fun updateDeviceStatus(status: DeviceStatus): Result<Unit> {
        return try {
            if (!isNetworkAvailable()) {
                return Result.failure(NetworkException("No internet connection available."))
            }
            
            statusRef.setValue(status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is UnknownHostException -> "Cannot reach Firebase servers. Check your internet connection."
                is SocketTimeoutException -> "Connection timeout while updating device status."
                is IOException -> "Network error while updating device status."
                else -> "Failed to update device status: ${e.message ?: "Unknown error"}"
            }
            Result.failure(IoTException(errorMessage, e))
        }
    }
    
    fun getCommands(): Flow<IoTCommand> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val command = snapshot.getValue(IoTCommand::class.java) ?: IoTCommand()
                trySend(command)
            }
            
            override fun onCancelled(error: DatabaseError) {
                val errorMessage = when (error.code) {
                    DatabaseError.PERMISSION_DENIED -> "Access denied while listening for commands."
                    DatabaseError.NETWORK_ERROR -> "Network error while listening for commands."
                    DatabaseError.UNAVAILABLE -> "Firebase service unavailable."
                    else -> "Error listening for commands: ${error.message}"
                }
                close(IoTException(errorMessage, error.toException()))
            }
        }
        
        commandsRef.addValueEventListener(listener)
        awaitClose { commandsRef.removeEventListener(listener) }
    }
    
    fun getWarningAlerts(): Flow<List<WarningAlert>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<WarningAlert>()
                for (childSnapshot in snapshot.children) {
                    val alert = childSnapshot.getValue(WarningAlert::class.java)
                    if (alert != null && alert.isActive) {
                        alerts.add(alert)
                    }
                }
                trySend(alerts)
            }
            
            override fun onCancelled(error: DatabaseError) {
                val errorMessage = when (error.code) {
                    DatabaseError.PERMISSION_DENIED -> "Access denied while listening for warning alerts."
                    DatabaseError.NETWORK_ERROR -> "Network error while listening for warning alerts."
                    DatabaseError.UNAVAILABLE -> "Firebase service unavailable."
                    else -> "Error listening for warning alerts: ${error.message}"
                }
                close(IoTException(errorMessage, error.toException()))
            }
        }
        
        warningAlertsRef.addValueEventListener(listener)
        awaitClose { warningAlertsRef.removeEventListener(listener) }
    }
    
    suspend fun dismissWarningAlert(alertId: String): Result<Unit> {
        return try {
            if (!isNetworkAvailable()) {
                return Result.failure(NetworkException("No internet connection available."))
            }
            
            warningAlertsRef.child(alertId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is UnknownHostException -> "Cannot reach Firebase servers. Check your internet connection."
                is SocketTimeoutException -> "Connection timeout while dismissing alert."
                is IOException -> "Network error while dismissing alert."
                else -> "Failed to dismiss alert: ${e.message ?: "Unknown error"}"
            }
            Result.failure(IoTException(errorMessage, e))
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    fun isOnline(): Boolean = isNetworkAvailable()
    
    // Simple admin validation - in production, use proper authentication
    fun validateAdminAccess(adminId: String): Boolean {
        // For now, simple validation - in production use Firebase Auth
        return adminId.isNotEmpty() && adminId.length >= 4
    }
    
    companion object {
        // Predefined admin IDs for demo - in production use Firebase Auth
        val ADMIN_IDS = setOf("admin", "emergency", "root", "supervisor")
        
        fun isValidAdmin(adminId: String): Boolean {
            return adminId.lowercase() in ADMIN_IDS
        }
    }
}

// Custom exception classes for better error handling
class IoTException(message: String, cause: Throwable? = null) : Exception(message, cause)
class NetworkException(message: String) : Exception(message) 