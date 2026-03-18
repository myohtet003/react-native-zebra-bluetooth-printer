# React Native Zebra Bluetooth Printer (Android - Expo)

This project provides a **Zebra Bluetooth BLE barcode printing solution** for **React Native (Expo)** using Android native module.

Keywords: Zebra printer, Bluetooth BLE, barcode printing, ZPL, React Native, Expo, Android printing

It includes:

- Kotlin native code (`PrinterModule.kt`)
- Zebra SDK JAR files
- BLE (Bluetooth Low Energy) & LAN connection support
- Methods for printing base64, text, checking printer status, and disconnecting

---

## 🚀 Features

- ✅ Zebra Bluetooth BLE printing
- ✅ LAN printing support
- ✅ Print ZPL (barcode labels)
- ✅ Base64 printing support
- ✅ Printer status checking
- ✅ Event listeners (connected / disconnected / status)

---

## 📂 Folder Structure

```
ZebraPrinterModule/
├── android/
│   ├── app/
│   │   ├── libs/                          <- Zebra SDK .jar files go here
│   │   └── src/
│   │       └── main/
│   │           └── java/com/myohtet/Barcode_Printer/
│   │               ├── printer/
│   │               │   ├── PrinterModule.kt
│   │               │   └── PrinterPackage.kt
│   │               ├── MainActivity.kt
│   │               └── MainApplication.kt
├── README.md
└── .gitignore
```

---

## ⚙️ Installation

1. Copy the `android` folder into your React Native project.

2. Make sure your project `android/app/libs/` includes the Zebra SDK JAR files.

3. Add this dependency in `android/app/build.gradle` if not already present:

```gradle
implementation fileTree(dir: "libs", include: ["*.jar"])
```

> ⚠️ This module works only on Android because the Zebra SDK is Android-native.

---

## 🔌 Usage

Import the module in your React Native JS/TS code:

```js
import { NativeModules, NativeEventEmitter } from "react-native";
const { PrinterModule } = NativeModules;

const printerEmitter = new NativeEventEmitter(PrinterModule);

// ----------------------------
// Discover Bluetooth LE printers
// ----------------------------
PrinterModule.discoverBluetoothBle()
  .then(printers => console.log("Discovered printers:", printers))
  .catch(err => console.log("Discovery error:", err));

// ----------------------------
// Connect to a BLE printer
// ----------------------------
const bleAddress = "PRINTER_ADDRESS"; // replace with your printer MAC address
PrinterModule.connectBluetoothBle(bleAddress)
  .then(() => console.log("Connected via BLE"))
  .catch(err => console.log("BLE Connect error:", err));

// ----------------------------
// Connect via LAN
// ----------------------------
PrinterModule.connectLan("192.168.1.100", 9100)
  .then(() => console.log("Connected via LAN"))
  .catch(err => console.log("LAN Connect error:", err));

// ----------------------------
// Print text
// ----------------------------
PrinterModule.printText("Hello Zebra!", "UTF-8")
  .then(() => console.log("Text printed successfully"))
  .catch(err => console.log("Print error:", err));

// ----------------------------
// Print base64 data
// ----------------------------
PrinterModule.printBase64("BASE64_STRING_HERE")
  .then(() => console.log("Base64 printed successfully"))
  .catch(err => console.log("Base64 print error:", err));

// ----------------------------
// Get printer status
// ----------------------------
PrinterModule.getPrinterStatus()
  .then(status => console.log("Printer status:", status))
  .catch(err => console.log("Status error:", err));

// ----------------------------
// Check connection
// ----------------------------
PrinterModule.isConnected()
  .then(connected => console.log("Is printer connected:", connected))
  .catch(err => console.log("Connection check error:", err));

// ----------------------------
// Disconnect printer
// ----------------------------
PrinterModule.disconnect()
  .then(() => console.log("Printer disconnected"))
  .catch(err => console.log("Disconnect error:", err));

// ----------------------------
// Listen for printer events
// ----------------------------
printerEmitter.addListener("printer_status", status => console.log("Printer status event:", status));
printerEmitter.addListener("printer_connected", type => console.log("Printer connected event via:", type));
printerEmitter.addListener("printer_disconnected", () => console.log("Printer disconnected event"));
```

---

## 📝 Notes

- Make sure the printer is paired via Bluetooth or accessible via LAN before printing.
- BLE connections use chunked data with small delays for stable printing.
- The module emits events for printer status, connection, and disconnection.

---

## 🔗 References

- [Zebra Android SDK](https://www.zebra.com/us/en/support-downloads/software/scanner-software/scanner-sdk-for-android.html)
- [React Native Native Modules](https://reactnative.dev/docs/native-modules-android)
