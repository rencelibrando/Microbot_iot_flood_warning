# ESP8266 + Arduino Separated Setup Guide

This guide walks you through setting up the IoT controller with ESP8266 as WiFi module and Arduino for peripheral control.

## 📋 What You'll Need

### Hardware:
- **ESP8266 Module** (NodeMCU, Wemos D1 Mini, or similar)
- **Arduino Uno R3**
- **16x2 LCD Display**
- **Active or Passive Buzzer**
- **10kΩ Potentiometer** (for LCD contrast)
- **Breadboard and Jumper Wires**
- **External Power Supply** (7-12V DC, 1A minimum)
- **USB Cables** (for programming both devices)

### Software:
- **Arduino IDE** (latest version)
- **ESP8266 Board Package** for Arduino IDE
- **Required Libraries** (see libraries_required.txt)

## 🔧 Step 1: Arduino IDE Setup

### Install ESP8266 Board Package:
1. Open Arduino IDE
2. Go to **File → Preferences**
3. Add this URL to "Additional Boards Manager URLs":
   ```
   http://arduino.esp8266.com/stable/package_esp8266com_index.json
   ```
4. Go to **Tools → Board → Boards Manager**
5. Search for "ESP8266" and install the package

### Install Required Libraries:
1. Go to **Sketch → Include Library → Manage Libraries**
2. Install these libraries:
   - **FirebaseESP8266** by Mobizt
   - **ArduinoJson** by Benoit Blanchon
   - **LiquidCrystal** (usually pre-installed)

## 🔌 Step 2: Hardware Wiring

### Power Connections:
```
External Power Supply (7-12V):
├── Positive (+) → Arduino VIN
├── Positive (+) → ESP8266 VIN (or 3.3V)
├── Negative (-) → Arduino GND
└── Negative (-) → ESP8266 GND
```

### Serial Communication:
```
ESP8266 TX → Arduino Pin 0 (RX)
ESP8266 RX → Arduino Pin 1 (TX)
ESP8266 GND → Arduino GND (shared)
```

### LCD to Arduino:
```
Arduino Pin 12 → LCD RS
Arduino Pin 11 → LCD Enable
Arduino Pin 5  → LCD D4
Arduino Pin 4  → LCD D5
Arduino Pin 3  → LCD D6
Arduino Pin 2  → LCD D7
Arduino 5V     → LCD VDD
Arduino GND    → LCD VSS
Potentiometer  → LCD V0 (contrast)
```

### Buzzer to Arduino:
```
Arduino Pin 8 → Buzzer (+)
Arduino GND   → Buzzer (-)
```

## 💻 Step 3: Upload Arduino Code

1. **Connect Arduino via USB**
2. **Open** `arduino_controller/arduino_controller.ino`
3. **Select Board**: Tools → Board → Arduino Uno
4. **Select Port**: Tools → Port → (your Arduino port)
5. **Upload the code**

The Arduino will show:
```
Arduino Ready
Waiting ESP...
```

## 📡 Step 4: Configure ESP8266 Code

1. **Open** `esp8266_wifi_module/esp8266_wifi_module.ino`
2. **Update WiFi credentials**:
   ```cpp
   #define WIFI_SSID "YOUR_WIFI_SSID"
   #define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
   ```
3. **Update Firebase credentials**:
   ```cpp
   #define FIREBASE_HOST "YOUR_PROJECT_ID.firebaseio.com"
   #define FIREBASE_AUTH "YOUR_DATABASE_SECRET"
   ```

## 📡 Step 5: Upload ESP8266 Code

1. **Disconnect Arduino USB** (to avoid serial conflicts)
2. **Connect ESP8266 via USB**
3. **Select Board**: Tools → Board → ESP8266 Boards → (your ESP8266 model)
4. **Select Port**: Tools → Port → (your ESP8266 port)
5. **Upload the code**

## ⚡ Step 6: Power Up and Test

1. **Disconnect both USB cables**
2. **Connect external power supply**
3. **Observe LCD display sequence**:
   ```
   Arduino Ready
   Waiting ESP...
   ↓
   ESP8266 Starting...
   ↓
   WiFi Connected
   ↓
   Firebase Ready
   ↓
   IoT Controller
   Ready
   ```

## 📱 Step 7: Test with Android App

1. **Open the Android app**
2. **Check device status** - should show "Online"
3. **Send test commands**:
   - Type "Hello World" and tap "Send to LCD"
   - Select "Beep" and tap buzzer control
   - Try combined commands

## 🔍 Troubleshooting

### No Communication Between Devices:
- ✅ Check TX/RX connections (TX→RX, RX→TX)
- ✅ Verify common ground connection
- ✅ Ensure both use 9600 baud rate
- ✅ Check power supply connections

### ESP8266 Won't Connect to WiFi:
- ✅ Verify SSID and password
- ✅ Check WiFi signal strength
- ✅ Try different WiFi network
- ✅ Reset ESP8266 and try again

### LCD Display Issues:
- ✅ Adjust contrast potentiometer
- ✅ Check all LCD wiring connections
- ✅ Verify 5V power to LCD
- ✅ Test with simple LCD example

### Firebase Connection Issues:
- ✅ Verify Firebase host URL
- ✅ Check database secret key
- ✅ Ensure database rules allow read/write
- ✅ Test internet connectivity

### Power Supply Problems:
- ✅ Use 7-12V DC power supply
- ✅ Ensure minimum 500mA current capacity
- ✅ Check all ground connections
- ✅ Verify VIN pin connections

## 📊 Serial Monitor Debugging

### Arduino Serial Output:
```
STATUS:Arduino Ready
STATUS:OK
LCD_UPDATED:Hello World
BUZZER_STATUS:beep
STATUS:Heartbeat OK
```

### ESP8266 Serial Output:
```
Connected to WiFi. IP: 192.168.1.100
Device status updated successfully
New command received:
Display Text: Hello World
Buzzer Action: beep
```

## 🔄 Communication Protocol

The devices communicate using this format:
```
ESP8266 → Arduino: COMMAND:param1:param2:param3
Arduino → ESP8266: STATUS:response_data
```

### Command Examples:
- `LCD:Hello World:::` - Display text on LCD
- `BUZZER:beep:1000::` - Activate buzzer
- `STATUS:WiFi Connected:Ready:` - Status update
- `HEARTBEAT:::` - Keep-alive signal

## ⚙️ Advanced Configuration

### Change Communication Pins:
If you need pins 0/1 for other purposes, use SoftwareSerial:

```cpp
// In Arduino code:
#include <SoftwareSerial.h>
SoftwareSerial espSerial(2, 3); // RX, TX

// Use espSerial instead of Serial for ESP communication
```

### Custom Baud Rate:
Both devices must use the same baud rate:
```cpp
Serial.begin(19200); // Change in both sketches
```

### Additional Sensors:
Add sensors to Arduino and modify the communication protocol to send sensor data to ESP8266.

## 🎯 Next Steps

1. **Test all functionality** with the Android app
2. **Create permanent connections** using PCB or perfboard
3. **Add enclosure** for protection
4. **Expand functionality** with additional sensors
5. **Implement secure authentication** for production use

Your separated ESP8266 + Arduino IoT controller is now ready! 🎉 