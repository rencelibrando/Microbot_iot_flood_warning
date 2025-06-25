#include <LiquidCrystal_I2C.h>

// I2C LCD - only 4 pins needed: GND, VCC, SDA, SCL
// SDA -> A4, SCL -> A5 on Arduino Uno
LiquidCrystal_I2C lcd(0x27, 16, 2); // I2C address 0x27, 16 columns, 2 rows

// Pin definitions
#define BUZZER_PIN 8
#define EMERGENCY_LED_PIN 7
#define WATER_SENSOR_PIN A0  // Water sensor analog input

// LCD dimensions
const int LCD_COLS = 16;
const int LCD_ROWS = 2;

// Water sensor thresholds
const int WATER_THRESHOLD_HIGH = 700;  // Analog value for high water level
const int WATER_THRESHOLD_LOW = 300;   // Analog value for normal water level

// Variables for device status
String currentDisplayText = "";
String currentBuzzerStatus = "off";
bool emergencyActive = false;
bool waterEmergencyActive = false;  // Separate flag for water-triggered emergency
unsigned long emergencyStartTime = 0;
unsigned long lastLedToggle = 0;
bool ledState = false;
unsigned long lastHeartbeat = 0;
bool espConnected = false;

// Water sensor variables
int lastWaterLevel = 0;
unsigned long lastWaterCheck = 0;
const unsigned long WATER_CHECK_INTERVAL = 1000;  // Check water level every second

// LCD scrolling variables
String scrollText = "";
int scrollPosition = 0;
unsigned long lastScrollUpdate = 0;
const unsigned long SCROLL_DELAY = 300;  // Milliseconds between scroll steps
bool isScrolling = false;

// Emergency settings
const unsigned long EMERGENCY_TIMEOUT = 30000; // 30 seconds auto-timeout
const unsigned long LED_FLASH_INTERVAL = 250; // 250ms flash interval

void setup() {
  Serial.begin(9600); // Communication with ESP8266
  
  // Initialize pins
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(EMERGENCY_LED_PIN, OUTPUT);
  pinMode(WATER_SENSOR_PIN, INPUT);
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(EMERGENCY_LED_PIN, LOW);
  
  // Initialize I2C LCD
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.print("Arduino Ready");
  lcd.setCursor(0, 1);
  lcd.print("Waiting ESP...");
  
  // Send ready status to ESP8266
  Serial.println("STATUS:Arduino Ready");
  
  delay(2000);
}

void loop() {
  // Check for commands from ESP8266
  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    command.trim();
    processCommand(command);
    lastHeartbeat = millis();
    espConnected = true;
  }
  
  // Check water sensor
  checkWaterLevel();
  
  // Handle emergency system
  handleEmergency();
  
  // Handle LCD scrolling
  handleLCDScrolling();
  
  // Check if ESP8266 is still connected (heartbeat timeout)
  if (millis() - lastHeartbeat > 10000 && espConnected) { // 10 second timeout
    espConnected = false;
    if (!emergencyActive && !waterEmergencyActive) { // Don't override emergency display
      displayStaticText("ESP Disconnected", "Check Connection");
    }
  }
  
  delay(50); // Reduced delay for better emergency responsiveness
}

void checkWaterLevel() {
  if (millis() - lastWaterCheck < WATER_CHECK_INTERVAL) return;
  
  int waterLevel = analogRead(WATER_SENSOR_PIN);
  lastWaterLevel = waterLevel;
  lastWaterCheck = millis();
  
  // Check for high water level (automatic emergency)
  if (waterLevel > WATER_THRESHOLD_HIGH && !waterEmergencyActive) {
    Serial.println("WATER_ALERT:HIGH_LEVEL");
    activateWaterEmergency();
  }
  // Check if water level has returned to normal
  else if (waterLevel < WATER_THRESHOLD_LOW && waterEmergencyActive) {
    Serial.println("WATER_ALERT:NORMAL_LEVEL");
    deactivateWaterEmergency();
  }
  
  // Send water level status periodically (every 5 seconds)
  static unsigned long lastWaterReport = 0;
  if (millis() - lastWaterReport > 5000) {
    Serial.println("WATER_LEVEL:" + String(waterLevel));
    lastWaterReport = millis();
  }
}

void activateWaterEmergency() {
  waterEmergencyActive = true;
  emergencyActive = true;  // Use the same emergency system
  emergencyStartTime = millis();
  ledState = false;
  lastLedToggle = 0;
  
  // Display water emergency message
  stopScrolling();
  lcd.clear();
  lcd.print("** WATER ALERT **");
  lcd.setCursor(0, 1);
  lcd.print("Water level high");
  
  // Start siren pattern
  sirenPattern();
  
  Serial.println("WATER_EMERGENCY:ACTIVE");
}

void deactivateWaterEmergency() {
  bool wasWaterEmergency = waterEmergencyActive;
  waterEmergencyActive = false;
  
  // Only deactivate main emergency if it was caused by water sensor
  if (emergencyActive && wasWaterEmergency) {
    emergencyActive = false;
    
    // Turn off LED and buzzer
    digitalWrite(EMERGENCY_LED_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);
    noTone(BUZZER_PIN);
    
    // Clear emergency display
    stopScrolling();
    displayStaticText("Water Alert", "Level Normal");
    
    currentBuzzerStatus = "off";
    Serial.println("WATER_EMERGENCY:DEACTIVATED");
    
    // Return to normal display after 3 seconds
    delay(3000);
    displayStaticText("IoT Controller", "Ready");
  }
}

void processCommand(String command) {
  // Parse command format: COMMAND:param1:param2:param3
  int firstColon = command.indexOf(':');
  if (firstColon == -1) return;
  
  String cmd = command.substring(0, firstColon);
  String params = command.substring(firstColon + 1);
  
  // Split parameters
  String param1 = "", param2 = "", param3 = "";
  int secondColon = params.indexOf(':');
  if (secondColon != -1) {
    param1 = params.substring(0, secondColon);
    String remaining = params.substring(secondColon + 1);
    
    int thirdColon = remaining.indexOf(':');
    if (thirdColon != -1) {
      param2 = remaining.substring(0, thirdColon);
      param3 = remaining.substring(thirdColon + 1);
    } else {
      param2 = remaining;
    }
  } else {
    param1 = params;
  }
  
  // Process commands
  if (cmd == "LCD") {
    if (!emergencyActive) { // Don't update LCD during emergency
      updateLCDDisplay(param1);
      Serial.println("LCD_UPDATED:" + param1);
    }
  }
  else if (cmd == "BUZZER") {
    if (!emergencyActive) { // Don't interfere with emergency buzzer
      int duration = param2.toInt();
      controlBuzzer(param1, duration);
      Serial.println("BUZZER_STATUS:" + currentBuzzerStatus);
    }
  }
  else if (cmd == "EMERGENCY") {
    handleEmergencyCommand(param1);
  }
  else if (cmd == "STATUS") {
    if (param1.length() > 0 && !emergencyActive) {
      updateLCDDisplay(param1, param2);
    }
    Serial.println("STATUS:OK");
  }
  else if (cmd == "HEARTBEAT") {
    Serial.println("STATUS:Heartbeat OK");
  }
}

void handleEmergencyCommand(String action) {
  if (action == "on" || action == "activate") {
    activateEmergency();
  } else if (action == "off" || action == "deactivate") {
    deactivateEmergency();
  }
}

void activateEmergency() {
  emergencyActive = true;
  emergencyStartTime = millis();
  ledState = false;
  lastLedToggle = 0;
  
  // Display emergency message
  lcd.clear();
  lcd.print("*** EMERGENCY ***");
  lcd.setCursor(0, 1);
  lcd.print("SYSTEM ACTIVATED");
  
  // Start siren pattern
  sirenPattern();
  
  Serial.println("EMERGENCY_STATUS:ACTIVE");
}

void deactivateEmergency() {
  emergencyActive = false;
  
  // Turn off LED and buzzer
  digitalWrite(EMERGENCY_LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);
  noTone(BUZZER_PIN); // Stop any ongoing tone generation
  
  // Clear emergency display
  lcd.clear();
  lcd.print("Emergency");
  lcd.setCursor(0, 1);
  lcd.print("Deactivated");
  
  currentBuzzerStatus = "off";
  
  Serial.println("EMERGENCY_STATUS:DEACTIVATED");
  
  // Return to normal display after 2 seconds
  delay(2000);
  lcd.clear();
  lcd.print("IoT Controller");
  lcd.setCursor(0, 1);
  lcd.print("Ready");
}

void handleEmergency() {
  if (!emergencyActive) return;
  
  // Auto-timeout after 30 seconds
  if (millis() - emergencyStartTime > EMERGENCY_TIMEOUT) {
    deactivateEmergency();
    return;
  }
  
  // Flash LED rapidly
  if (millis() - lastLedToggle > LED_FLASH_INTERVAL) {
    ledState = !ledState;
    digitalWrite(EMERGENCY_LED_PIN, ledState);
    lastLedToggle = millis();
  }
  
  // Continuous siren pattern
  if (millis() % 2000 < 1000) { // High tone for 1 second
    tone(BUZZER_PIN, 1000);
  } else { // Low tone for 1 second
    tone(BUZZER_PIN, 500);
  }
}

void sirenPattern() {
  // Initial siren burst
  for (int i = 0; i < 5; i++) {
    tone(BUZZER_PIN, 1000);
    delay(100);
    tone(BUZZER_PIN, 500);
    delay(100);
  }
  noTone(BUZZER_PIN);
}

void updateLCDDisplay(String text) {
  updateLCDDisplay(text, "");
}

void updateLCDDisplay(String line1, String line2) {
  if (emergencyActive) return; // Don't update during emergency
  
  currentDisplayText = line1;
  if (line2.length() > 0) {
    currentDisplayText += " " + line2;
  }
  
  // Handle single line or combined text
  String fullText = line1;
  if (line2.length() > 0) {
    fullText = line1 + " " + line2;
  }
  
  if (line1.length() == 0 && line2.length() == 0) {
    displayStaticText("IoT Controller", "Ready");
    return;
  }
  
  // If text is longer than LCD width, start scrolling
  if (fullText.length() > LCD_COLS) {
    startScrolling(fullText);
  } else {
    // Short text - display normally
    stopScrolling();
    displayStaticText(line1, line2);
  }
}

void controlBuzzer(String action, int duration) {
  if (emergencyActive) return; // Don't interfere with emergency
  
  currentBuzzerStatus = action;
  
  if (action == "off") {
    digitalWrite(BUZZER_PIN, LOW);
    noTone(BUZZER_PIN);
  } 
  else if (action == "on") {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(duration);
    digitalWrite(BUZZER_PIN, LOW);
    currentBuzzerStatus = "off";
  }
  else if (action == "beep") {
    // Short beep
    digitalWrite(BUZZER_PIN, HIGH);
    delay(200);
    digitalWrite(BUZZER_PIN, LOW);
    currentBuzzerStatus = "off";
  }
  else if (action == "pattern") {
    // Pattern: beep 3 times
    for (int i = 0; i < 3; i++) {
      digitalWrite(BUZZER_PIN, HIGH);
      delay(200);
      digitalWrite(BUZZER_PIN, LOW);
      delay(200);
    }
    currentBuzzerStatus = "off";
  }
}

void displayStaticText(String line1, String line2) {
  lcd.clear();
  lcd.print(line1);
  lcd.setCursor(0, 1);
  lcd.print(line2);
}

void startScrolling(String text) {
  scrollText = text + "    "; // Add padding for smooth scrolling
  scrollPosition = 0;
  isScrolling = true;
  lastScrollUpdate = millis();
}

void stopScrolling() {
  isScrolling = false;
  scrollText = "";
  scrollPosition = 0;
}

void handleLCDScrolling() {
  if (!isScrolling || emergencyActive || waterEmergencyActive) return;
  
  if (millis() - lastScrollUpdate > SCROLL_DELAY) {
    lcd.clear();
    
    // Display scrolling text on first line
    String displayText = "";
    for (int i = 0; i < LCD_COLS; i++) {
      int charIndex = (scrollPosition + i) % scrollText.length();
      displayText += scrollText.charAt(charIndex);
    }
    lcd.print(displayText);
    
    // Status line on second row
    lcd.setCursor(0, 1);
    lcd.print("Water:" + String(lastWaterLevel) + " Online");
    
    scrollPosition++;
    if (scrollPosition >= scrollText.length()) {
      scrollPosition = 0;
    }
    
    lastScrollUpdate = millis();
  }
}