# IoT Device Controller

A complete IoT solution that allows you to control an LCD display and buzzer remotely using an Android app, ESP8266, and Firebase Real-time Database.

## 🚀 Features

- **Remote LCD Control**: Send custom text messages to display on a 16x2 LCD
- **Buzzer Control**: Control buzzer with multiple modes (On/Off/Beep/Pattern)
- **Real-time Communication**: Instant command delivery via Firebase
- **Device Status Monitoring**: View online/offline status and current device state
- **Modern Android UI**: Beautiful Material Design 3 interface
- **WiFi Connectivity**: ESP8266 connects to your WiFi network

## 📱 Android App Features

- Text input for LCD messages
- Buzzer control with duration settings
- Device status indicator (online/offline)
- Multiple buzzer modes:
  - **Off**: Turn buzzer off
  - **On**: Turn buzzer on for specified duration
  - **Beep**: Quick beep sound
  - **Pattern**: Multiple beep pattern
- Combined commands (send both LCD text and buzzer command together)

## 🔧 Hardware Components

- ESP8266 NodeMCU or Wemos D1 Mini
- 16x2 LCD Display (with or without I2C backpack)
- Active or Passive Buzzer
- Breadboard and jumper wires
- 10kΩ potentiometer (for LCD contrast)
- Arduino Uno R3 (optional, can connect directly to ESP8266)

## 📋 Setup Instructions

### 1. Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use existing one
3. Enable **Realtime Database**
4. Set Database rules for testing:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
5. Add Android app to Firebase project:
   - Package name: `com.example.minrobotiot`
   - Download `google-services.json`
   - Replace the template file in `app/google-services.json`

### 2. Android App Setup

1. Open the project in Android Studio
2. Replace `app/google-services.json` with your Firebase configuration
3. Build and install the app on your device
4. Grant necessary permissions

### 3. Arduino/ESP8266 Setup

1. Install required libraries (see `arduino_code/libraries_required.txt`)
2. Open `arduino_code/esp8266_iot_controller/esp8266_iot_controller.ino`
3. Update configuration:
   ```cpp
   #define WIFI_SSID "YOUR_WIFI_SSID"
   #define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
   #define FIREBASE_HOST "YOUR_PROJECT_ID.firebaseio.com"
   #define FIREBASE_AUTH "YOUR_DATABASE_SECRET"
   ```
4. Wire components according to `arduino_code/wiring_diagram.txt`
5. Upload code to ESP8266

### 4. Getting Firebase Database Secret

1. Go to Firebase Console → Project Settings
2. Click on "Service accounts" tab
3. Click "Database secrets"
4. Copy the secret key and use it as `FIREBASE_AUTH`

## 🔌 Hardware Wiring

### Standard LCD Connection:
```
ESP8266  →  LCD
D2 (GPIO4)  →  RS
D3 (GPIO0)  →  Enable  
D4 (GPIO2)  →  D4
D5 (GPIO14) →  D5
D6 (GPIO12) →  D6
D7 (GPIO13) →  D7
3.3V        →  VDD
GND         →  VSS
```

### Buzzer Connection:
```
ESP8266  →  Buzzer
D1 (GPIO5)  →  Positive
GND         →  Negative
```

### I2C LCD (Recommended):
```
ESP8266  →  I2C LCD
D2 (GPIO4)  →  SDA
D1 (GPIO5)  →  SCL
3.3V        →  VCC
GND         →  GND
```

## 📊 Firebase Database Structure

```json
{
  "iot_commands": {
    "displayText": "Hello World!",
    "buzzerAction": "beep",
    "buzzerDuration": 1000,
    "timestamp": 1234567890
  },
  "device_status": {
    "isOnline": true,
    "lastSeen": 1234567890,
    "currentDisplayText": "Hello World!",
    "buzzerStatus": "off"
  }
}
```

## 🎮 How to Use

1. **Power on your ESP8266 device**
2. **Wait for WiFi and Firebase connection** (LCD will show "Ready")
3. **Open the Android app**
4. **Check device status** - should show "Online"
5. **Send commands:**
   - Type text and tap "Send to LCD"
   - Select buzzer action and tap buzzer controls
   - Use "Send Combined Command" for both LCD and buzzer

## 🔧 Troubleshooting

### ESP8266 Issues:
- **Won't connect to WiFi**: Check SSID and password
- **Firebase connection fails**: Verify Firebase host and auth key
- **LCD doesn't display**: Check wiring and contrast potentiometer
- **Buzzer doesn't work**: Check polarity and pin connections

### Android App Issues:
- **Commands not sending**: Check internet connection
- **Firebase errors**: Verify `google-services.json` configuration
- **App crashes**: Check logs and ensure all dependencies are installed

### Firebase Issues:
- **Database rules**: Ensure read/write permissions are set
- **Authentication**: Verify database secret key
- **Network**: Check internet connectivity

## 🛠️ Customization

### Adding More Commands:
1. Update `IoTCommand` data class in Android app
2. Modify Firebase repository methods
3. Update Arduino code to handle new commands

### Different Hardware:
- Change pin definitions in Arduino code
- Update LCD library for different display types
- Modify buzzer control for different buzzer types

## 📝 Dependencies

### Android:
- Firebase Realtime Database
- Firebase Authentication
- Jetpack Compose
- ViewModel and LiveData

### Arduino:
- ESP8266WiFi
- FirebaseESP8266
- LiquidCrystal
- ArduinoJson

## 🔒 Security Notes

- Change Firebase database rules for production use
- Use Firebase Authentication for secure access
- Consider using HTTPS endpoints
- Validate input data on both client and device

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📞 Support

For issues and questions:
1. Check the troubleshooting section
2. Review Firebase and Arduino documentation
3. Create an issue in the repository

---

**Enjoy controlling your IoT devices! 🎉** 