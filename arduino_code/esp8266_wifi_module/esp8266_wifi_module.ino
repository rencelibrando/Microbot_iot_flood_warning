#include <ESP8266WiFi.h>
#include <FirebaseESP8266.h>
#include <ArduinoJson.h>

// WiFi credentials - Update these for iPhone hotspot or other networks
#define WIFI_SSID "RenceIp"  // Change to "iPhone" or your hotspot name
#define WIFI_PASSWORD "Clarence@1234"  // Update with your hotspot password

// Firebase credentials
#define FIREBASE_HOST "minrobot-387a5-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH "wUY5uXC0OTVzPcVffItm4Ct4KFtcQPgsUakT07qp"

// Firebase objects
FirebaseData firebaseData;
FirebaseConfig config;
FirebaseAuth auth;

// Connection management variables
bool wifiConnected = false;
bool firebaseConnected = false;
unsigned long lastWiFiCheck = 0;
unsigned long lastFirebaseCheck = 0;
unsigned long lastConnectionAttempt = 0;
unsigned long connectionFailures = 0;
const unsigned long WIFI_CHECK_INTERVAL = 10000;        // Check WiFi every 10 seconds
const unsigned long FIREBASE_CHECK_INTERVAL = 15000;    // Check Firebase every 15 seconds
const unsigned long RECONNECT_DELAY = 5000;             // Wait 5 seconds between reconnection attempts
const unsigned long MAX_RECONNECT_DELAY = 30000;        // Maximum delay between attempts (30 seconds)

// Device status variables
String currentDisplayText = "";
String currentBuzzerStatus = "off";
bool emergencyStatus = false;
bool waterEmergencyStatus = false;
int currentWaterLevel = 0;
unsigned long lastCommandTime = 0;
unsigned long lastEmergencyTime = 0;
unsigned long lastHeartbeat = 0;
const unsigned long HEARTBEAT_INTERVAL = 5000;          // 5 seconds
const unsigned long COMMAND_TIMEOUT = 30000;            // 30 seconds
bool arduinoConnected = false;

// Connection status tracking
enum ConnectionState {
  CONN_INITIALIZING,
  CONN_WIFI_CONNECTING,
  CONN_WIFI_CONNECTED,
  CONN_FIREBASE_CONNECTING,
  CONN_FULLY_CONNECTED,
  CONN_WIFI_FAILED,
  CONN_FIREBASE_FAILED,
  CONN_RECONNECTING
};

ConnectionState currentState = CONN_INITIALIZING;

void setup() {
  Serial.begin(9600);
  
  // Configure WiFi for better stability
  WiFi.setAutoReconnect(true);
  WiFi.persistent(true);
  WiFi.mode(WIFI_STA);
  
  Serial.println("ESP8266 IoT Controller Starting...");
  sendToArduino("STATUS", "ESP8266 Starting...", "", "");
  
  // Initialize connection
  initializeConnections();
  
  Serial.println("Setup completed");
}

void loop() {
  // Monitor and maintain connections
  manageConnections();
  
  // Only process commands if fully connected
  if (currentState == CONN_FULLY_CONNECTED) {
    // Check for responses from Arduino
    checkArduinoResponse();
    
    // Check for new Firebase commands
    checkForCommands();
    
    // Check for emergency commands
    checkForEmergencyCommands();
    
    // Send heartbeat
    sendHeartbeat();
  }
  
  delay(100);
}

void initializeConnections() {
  currentState = CONN_WIFI_CONNECTING;
  connectToWiFi();
  
  if (wifiConnected) {
    currentState = CONN_FIREBASE_CONNECTING;
    connectToFirebase();
  }
  
  if (wifiConnected && firebaseConnected) {
    currentState = CONN_FULLY_CONNECTED;
    Serial.println("All connections established successfully");
    sendToArduino("STATUS", "IoT Controller", "Ready", "");
    
    // Update initial device status
    updateDeviceStatus(true, "", "off", false, 0, false);
  }
}

void connectToWiFi() {
  Serial.println("Connecting to WiFi: " + String(WIFI_SSID));
  sendToArduino("STATUS", "Connecting WiFi...", "", "");
  
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  const int maxAttempts = 30; // 15 seconds total
  
  while (WiFi.status() != WL_CONNECTED && attempts < maxAttempts) {
    delay(500);
    Serial.print(".");
    attempts++;
    
    // Send periodic status updates
    if (attempts % 6 == 0) {
      sendToArduino("STATUS", "WiFi connecting...", String(attempts/2) + "s", "");
    }
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    connectionFailures = 0; // Reset failure counter on success
    
    Serial.println();
    Serial.println("WiFi connected successfully!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
    Serial.print("Signal strength: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
    
    sendToArduino("STATUS", "WiFi Connected", WiFi.localIP().toString(), "");
    delay(1000);
    
  } else {
    wifiConnected = false;
    connectionFailures++;
    
    Serial.println();
    Serial.println("WiFi connection failed!");
    Serial.println("WiFi status: " + getWiFiStatusString());
    
    sendToArduino("STATUS", "WiFi Failed", "Check credentials", "");
    currentState = CONN_WIFI_FAILED;
  }
}

void connectToFirebase() {
  Serial.println("Connecting to Firebase...");
  sendToArduino("STATUS", "Connecting Firebase...", "", "");
  
  // Configure Firebase with updated settings
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.timeout.serverResponse = 15000;    // Increased timeout
  config.timeout.socketConnection = 15000;  // Increased timeout
  config.timeout.sslHandshake = 15000;      // Added SSL timeout
  
  // Set database URL explicitly
  config.database_url = "https://" + String(FIREBASE_HOST);
  
  // Initialize Firebase with debug info
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  
  // Set buffer sizes for stability
  firebaseData.setBSSLBufferSize(2048, 2048);  // Increased buffer size
  firebaseData.setResponseSize(2048);
  
  // Add debug logging
  Firebase.setDoubleDigits(5);
  
  // Test Firebase connection with more detailed error handling
  if (testFirebaseConnection()) {
    firebaseConnected = true;
    connectionFailures = 0;
    
    Serial.println("Firebase connected successfully!");
    Serial.println("Database URL: https://" + String(FIREBASE_HOST));
    sendToArduino("STATUS", "Firebase Ready", "", "");
    delay(1000);
    
  } else {
    firebaseConnected = false;
    connectionFailures++;
    
    Serial.println("Firebase connection failed!");
    Serial.println("Error details:");
    Serial.println("  Host: " + String(FIREBASE_HOST));
    Serial.println("  Auth token length: " + String(strlen(FIREBASE_AUTH)));
    Serial.println("  WiFi status: " + getWiFiStatusString());
    Serial.println("  Free heap: " + String(ESP.getFreeHeap()));
    
    sendToArduino("STATUS", "Firebase Failed", "Check config", "");
    currentState = CONN_FIREBASE_FAILED;
  }
}

bool testFirebaseConnection() {
  Serial.println("Testing Firebase connection...");
  
  // Try to write a simple test value first
  if (Firebase.setString(firebaseData, "/test_connection", "esp8266_test")) {
    Serial.println("Firebase write test successful");
    
    // Then try to read it back
    if (Firebase.getString(firebaseData, "/test_connection")) {
      Serial.println("Firebase read test successful");
      Serial.println("Test value: " + firebaseData.stringData());
      return true;
    } else {
      Serial.print("Firebase read test failed: ");
      Serial.println(firebaseData.errorReason());
      Serial.println("HTTP Code: " + String(firebaseData.httpCode()));
      return false;
    }
  } else {
    Serial.print("Firebase write test failed: ");
    Serial.println(firebaseData.errorReason());
    Serial.println("HTTP Code: " + String(firebaseData.httpCode()));
    Serial.println("Response: " + firebaseData.payload());
    return false;
  }
}

void manageConnections() {
  unsigned long currentTime = millis();
  
  // Check WiFi connection status
  if (currentTime - lastWiFiCheck > WIFI_CHECK_INTERVAL) {
    lastWiFiCheck = currentTime;
    
    if (WiFi.status() != WL_CONNECTED) {
      if (wifiConnected) {
        Serial.println("WiFi connection lost! Attempting reconnection...");
        sendToArduino("STATUS", "WiFi Lost", "Reconnecting...", "");
        wifiConnected = false;
        firebaseConnected = false;
        currentState = CONN_RECONNECTING;
      }
      
      // Attempt reconnection with exponential backoff
      if (currentTime - lastConnectionAttempt > getReconnectDelay()) {
        lastConnectionAttempt = currentTime;
        attemptReconnection();
      }
    } else {
      // WiFi is connected, update status if it was previously disconnected
      if (!wifiConnected) {
        wifiConnected = true;
        Serial.println("WiFi reconnected successfully!");
        
        // Also reconnect to Firebase
        currentState = CONN_FIREBASE_CONNECTING;
        connectToFirebase();
        
        if (firebaseConnected) {
          currentState = CONN_FULLY_CONNECTED;
          sendToArduino("STATUS", "Reconnected", "All systems online", "");
        }
      }
    }
  }
  
  // Check Firebase connection status (only if WiFi is connected)
  if (wifiConnected && currentTime - lastFirebaseCheck > FIREBASE_CHECK_INTERVAL) {
    lastFirebaseCheck = currentTime;
    
    if (!testFirebaseConnection()) {
      if (firebaseConnected) {
        Serial.println("Firebase connection lost! Attempting reconnection...");
        sendToArduino("STATUS", "Firebase Lost", "Reconnecting...", "");
        firebaseConnected = false;
        currentState = CONN_FIREBASE_FAILED;
      }
      
      // Try to reconnect Firebase
      if (currentTime - lastConnectionAttempt > getReconnectDelay()) {
        lastConnectionAttempt = currentTime;
        connectToFirebase();
        
        if (firebaseConnected) {
          currentState = CONN_FULLY_CONNECTED;
        }
      }
    } else {
      if (!firebaseConnected) {
        firebaseConnected = true;
        currentState = CONN_FULLY_CONNECTED;
        Serial.println("Firebase reconnected successfully!");
      }
    }
  }
}

void attemptReconnection() {
  Serial.println("Attempting WiFi reconnection...");
  sendToArduino("STATUS", "Reconnecting...", "Attempt " + String(connectionFailures + 1), "");
  
  // Reset WiFi connection
  WiFi.disconnect();
  delay(1000);
  
  connectToWiFi();
  
  if (wifiConnected) {
    connectToFirebase();
    
    if (firebaseConnected) {
      currentState = CONN_FULLY_CONNECTED;
      Serial.println("Full reconnection successful!");
      sendToArduino("STATUS", "Reconnected!", "All systems online", "");
    }
  }
}

unsigned long getReconnectDelay() {
  // Exponential backoff: start with RECONNECT_DELAY, double each failure, max MAX_RECONNECT_DELAY
  unsigned long delay = RECONNECT_DELAY * (1 << min(connectionFailures, 3UL));
  return min(delay, MAX_RECONNECT_DELAY);
}

String getWiFiStatusString() {
  switch (WiFi.status()) {
    case WL_IDLE_STATUS:     return "Idle";
    case WL_NO_SSID_AVAIL:   return "SSID not available";
    case WL_SCAN_COMPLETED:  return "Scan completed";
    case WL_CONNECTED:       return "Connected";
    case WL_CONNECT_FAILED:  return "Connection failed";
    case WL_CONNECTION_LOST: return "Connection lost";
    case WL_WRONG_PASSWORD:  return "Wrong password";
    case WL_DISCONNECTED:    return "Disconnected";
    default:                 return "Unknown (" + String(WiFi.status()) + ")";
  }
}

void sendHeartbeat() {
  if (millis() - lastHeartbeat > HEARTBEAT_INTERVAL) {
    // Only send heartbeat if fully connected
    if (currentState == CONN_FULLY_CONNECTED) {
      updateDeviceStatus(true, currentDisplayText, currentBuzzerStatus, emergencyStatus, currentWaterLevel, waterEmergencyStatus);
      sendToArduino("HEARTBEAT", "", "", "");
    }
    lastHeartbeat = millis();
  }
}

void checkForCommands() {
  if (!firebaseConnected) return;
  
  if (Firebase.getJSON(firebaseData, "/iot_commands")) {
    if (firebaseData.dataType() == "json") {
      FirebaseJson json;
      json.setJsonData(firebaseData.jsonString());
      
      String displayText = "";
      String buzzerAction = "off";
      String buzzerDuration = "1000";
      unsigned long timestamp = 0;
      
      FirebaseJsonData result;
      
      if (json.get(result, "displayText")) {
        displayText = result.stringValue;
      }
      if (json.get(result, "buzzerAction")) {
        buzzerAction = result.stringValue;
      }
      if (json.get(result, "buzzerDuration")) {
        buzzerDuration = result.stringValue;
      }
      if (json.get(result, "timestamp")) {
        timestamp = result.intValue;
      }
      
      if (timestamp > lastCommandTime) {
        lastCommandTime = timestamp;
        
        if (displayText.length() > 0) {
          sendToArduino("LCD", displayText, "", "");
          currentDisplayText = displayText;
        }
        
        if (buzzerAction != "off") {
          sendToArduino("BUZZER", buzzerAction, buzzerDuration, "");
          currentBuzzerStatus = buzzerAction;
        } else {
          sendToArduino("BUZZER", "off", "0", "");
          currentBuzzerStatus = "off";
        }
        
        updateDeviceStatus(true, currentDisplayText, currentBuzzerStatus, emergencyStatus, currentWaterLevel, waterEmergencyStatus);
      }
    }
  } else {
    Serial.print("Failed to get command data: ");
    Serial.println(firebaseData.errorReason());
    
    // Check if this is a connection issue
    if (firebaseData.errorReason().indexOf("connection") >= 0 || 
        firebaseData.errorReason().indexOf("timeout") >= 0) {
      firebaseConnected = false;
    }
  }
}

void checkForEmergencyCommands() {
  if (!firebaseConnected) return;
  
  if (Firebase.getJSON(firebaseData, "/emergency_commands")) {
    if (firebaseData.dataType() == "json") {
      FirebaseJson json;
      json.setJsonData(firebaseData.jsonString());
      
      String emergencyAction = "";
      String adminId = "";
      unsigned long timestamp = 0;
      
      FirebaseJsonData result;
      
      if (json.get(result, "action")) {
        emergencyAction = result.stringValue;
      }
      if (json.get(result, "adminId")) {
        adminId = result.stringValue;
      }
      if (json.get(result, "timestamp")) {
        timestamp = result.intValue;
      }
      
      if (timestamp > lastEmergencyTime && adminId.length() > 0) {
        lastEmergencyTime = timestamp;
        
        sendToArduino("EMERGENCY", emergencyAction, "", "");
        
        if (emergencyAction == "on" || emergencyAction == "activate") {
          emergencyStatus = true;
          currentDisplayText = "EMERGENCY ACTIVE";
          currentBuzzerStatus = "siren";
        } else if (emergencyAction == "off" || emergencyAction == "deactivate") {
          emergencyStatus = false;
          currentDisplayText = "";
          currentBuzzerStatus = "off";
        }
        
        updateDeviceStatus(true, currentDisplayText, currentBuzzerStatus, emergencyStatus, currentWaterLevel, waterEmergencyStatus);
      }
    }
  }
}

void checkArduinoResponse() {
  if (Serial.available()) {
    String response = Serial.readStringUntil('\n');
    response.trim();
    
    if (response.startsWith("STATUS:")) {
      arduinoConnected = true;
    } else if (response.startsWith("LCD_UPDATED:")) {
      String text = response.substring(12);
      if (!emergencyStatus && !waterEmergencyStatus) {
        currentDisplayText = text;
      }
    } else if (response.startsWith("BUZZER_STATUS:")) {
      String status = response.substring(14);
      if (!emergencyStatus && !waterEmergencyStatus) {
        currentBuzzerStatus = status;
      }
    } else if (response.startsWith("EMERGENCY_STATUS:")) {
      String status = response.substring(17);
      if (status == "ACTIVE") {
        emergencyStatus = true;
        currentDisplayText = "EMERGENCY ACTIVE";
        currentBuzzerStatus = "siren";
      } else if (status == "DEACTIVATED") {
        emergencyStatus = false;
        currentDisplayText = "";
        currentBuzzerStatus = "off";
      }
      updateDeviceStatus(true, currentDisplayText, currentBuzzerStatus, emergencyStatus, currentWaterLevel, waterEmergencyStatus);
    } else if (response.startsWith("WATER_LEVEL:")) {
      String levelStr = response.substring(12);
      currentWaterLevel = levelStr.toInt();
    } else if (response.startsWith("WATER_ALERT:")) {
      String alertType = response.substring(12);
      if (alertType == "HIGH_LEVEL") {
        Serial.println("High water level detected!");
        createWaterAlert("Water Level Alert", "High water level detected by sensor", "CRITICAL");
      } else if (alertType == "NORMAL_LEVEL") {
        Serial.println("Water level returned to normal");
        createWaterAlert("Water Level Normal", "Water level has returned to safe levels", "INFO");
      }
    } else if (response.startsWith("WATER_EMERGENCY:")) {
      String status = response.substring(16);
      if (status == "ACTIVE") {
        waterEmergencyStatus = true;
        Serial.println("Water emergency activated!");
        createWaterAlert("Water Emergency", "Automatic water emergency activated", "CRITICAL");
      } else if (status == "DEACTIVATED") {
        waterEmergencyStatus = false;
        Serial.println("Water emergency deactivated");
        createWaterAlert("Water Emergency Resolved", "Water emergency automatically deactivated", "INFO");
      }
      updateDeviceStatus(true, currentDisplayText, currentBuzzerStatus, emergencyStatus, currentWaterLevel, waterEmergencyStatus);
    }
  }
}

void createWaterAlert(String title, String message, String severity) {
  if (!firebaseConnected) return;
  
  FirebaseJson alertJson;
  String alertId = String(millis());
  
  alertJson.set("id", alertId);
  alertJson.set("title", title);
  alertJson.set("message", message);
  alertJson.set("severity", severity);
  alertJson.set("timestamp", (unsigned long)(millis()));
  alertJson.set("isActive", true);
  alertJson.set("source", "water_sensor");
  
  String alertPath = "/warning_alerts/" + alertId;
  
  if (Firebase.setJSON(firebaseData, alertPath, alertJson)) {
    Serial.println("Water alert created: " + title);
  } else {
    Serial.print("Failed to create water alert: ");
    Serial.println(firebaseData.errorReason());
  }
}

void sendToArduino(String command, String param1, String param2, String param3) {
  String message = command + ":" + param1 + ":" + param2 + ":" + param3;
  Serial.println(message);
}

void updateDeviceStatus(bool isOnline, String displayText, String buzzerStatus, bool emergency, int waterLevel, bool waterEmergency) {
  if (!firebaseConnected) return;
  
  FirebaseJson json;
  json.set("isOnline", isOnline);
  json.set("lastSeen", (unsigned long)(millis()));
  json.set("currentDisplayText", displayText);
  json.set("buzzerStatus", buzzerStatus);
  json.set("arduinoConnected", arduinoConnected);
  json.set("emergencyActive", emergency);
  json.set("waterLevel", waterLevel);
  json.set("waterEmergencyActive", waterEmergency);
  
  // Add connection quality information
  if (wifiConnected) {
    json.set("wifiSignalStrength", WiFi.RSSI());
    json.set("wifiSSID", WiFi.SSID());
  }
  
  if (Firebase.setJSON(firebaseData, "/device_status", json)) {
    Serial.println("Device status updated successfully");
  } else {
    Serial.print("Failed to update device status: ");
    Serial.println(firebaseData.errorReason());
    
    // Check if this indicates a connection problem
    if (firebaseData.errorReason().indexOf("connection") >= 0 || 
        firebaseData.errorReason().indexOf("timeout") >= 0) {
      firebaseConnected = false;
    }
  }
} 