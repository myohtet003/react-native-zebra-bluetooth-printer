package com.myohtet.Barcode_Printer.printer

import android.util.Base64
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.zebra.sdk.btleComm.BluetoothLeConnection
import com.zebra.sdk.btleComm.BluetoothLeDiscoverer
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.discovery.*
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import kotlin.concurrent.thread

class PrinterModule(
        private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    // ✅ CHANGE 2: Use generic 'Connection' or 'BluetoothConnection'
    private var printerConnection: Connection? = null

    // LAN variables
    private var tcpSocket: Socket? = null
    private var lanOutStream: java.io.OutputStream? = null

    override fun getName() = "PrinterModule"

    private fun sendEvent(
            event: String,
            params: Any?,
    ) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(event, params)
        }
    }

    // ------------------------------------------------------------------------------------
    // DISCOVER BLUETOOTH PRINTERS

    @ReactMethod
    fun discoverBluetoothBle(promise: Promise) {
        Thread {
                    try {
                        val resultArray = Arguments.createArray()

                        BluetoothLeDiscoverer.findPrinters(
                                reactContext.applicationContext,
                                object : DiscoveryHandler {
                                    override fun foundPrinter(printer: DiscoveredPrinter) {
                                        val map = Arguments.createMap()

                                        val name =
                                                printer.discoveryDataMap["FRIENDLY_NAME"]
                                                        ?: printer.discoveryDataMap["MODEL"]
                                                                ?: "Zebra BLE Printer"

                                        map.putString("deviceName", name)
                                        map.putString("address", printer.address)
                                        map.putString("type", "BLUETOOTH_LE")

                                        resultArray.pushMap(map)
                                    }

                                    override fun discoveryFinished() {
                                        promise.resolve(resultArray)
                                    }

                                    override fun discoveryError(message: String) {
                                        promise.reject("DISCOVERY_ERROR", message)
                                    }
                                },
                        )
                    } catch (e: Exception) {
                        promise.reject("DISCOVERY_FAILED", e.message)
                    }
                }
                .start()
    }

    @ReactMethod
    fun connectBluetoothBle(address: String, promise: Promise) {
        thread {
            try {
                printerConnection?.close()

                printerConnection = BluetoothLeConnection(address, reactContext.applicationContext)

                printerConnection?.open()

                sendEvent("printer_connected", "BLUETOOTH_LE")
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("BT_BLE_FAILED", e.message)
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // CONNECT LAN
    // ------------------------------------------------------------------------------------
    @ReactMethod
    fun connectLan(
            ip: String,
            port: Int,
            promise: Promise,
    ) {
        thread {
            try {
                closeTcp()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 5000)

                tcpSocket = socket
                lanOutStream = socket.getOutputStream()

                sendEvent("printer_connected", "LAN")
                promise.resolve(true)
            } catch (e: Exception) {
                closeTcp()
                promise.reject("TCP_CONNECT_FAILED", e.message)
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // PRINT BASE64
    // ------------------------------------------------------------------------------------
    @ReactMethod
    fun printBase64(
            base64Data: String,
            promise: Promise,
    ) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            writeBytes(bytes)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PRINT_FAILED", e.message)
        }
    }

    // ------------------------------------------------------------------------------------
    // PRINT TEXT
    // ------------------------------------------------------------------------------------
    @ReactMethod
    fun printText(
            text: String,
            encodingName: String?,
            promise: Promise,
    ) {
        try {
            val charset =
                    try {
                        if (!encodingName.isNullOrEmpty()) Charset.forName(encodingName)
                        else Charsets.UTF_8
                    } catch (_: Exception) {
                        Charsets.UTF_8
                    }

            val bytes = text.toByteArray(charset)
            writeBytes(bytes)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PRINT_FAILED", e.message)
        }
    } 


    
    private fun writeBytes(bytes: ByteArray) {
        try {
            if (printerConnection != null && printerConnection!!.isConnected) {
                val chunkSize = 100 // safe for BLE
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + chunkSize, bytes.size)
                    val chunk = bytes.copyOfRange(offset, end)
                    printerConnection!!.write(chunk)
                    // BLE needs breathing room
                    Thread.sleep(30)
                    offset = end
                }
                return
            }
            if (lanOutStream != null && tcpSocket?.isConnected == true) {
                lanOutStream!!.write(bytes)
                lanOutStream!!.flush()
                return
            }
            throw ConnectionException("No active printer connection")
        } catch (e: Exception) {
            sendEvent("printer_disconnected", null)
            disconnect(null)
            throw e
        }
    }

    // ------------------------------------------------------------------------------------
    // GET STATUS
    // ------------------------------------------------------------------------------------
    @ReactMethod
    fun getPrinterStatus(promise: Promise) {
        thread {
            try {
                val cmd = "! U1 getvar \"zpl.system_status\"\r\n".toByteArray(Charsets.UTF_8)

                //  CHECK BLUETOOTH
                if (printerConnection != null && printerConnection!!.isConnected) {
                    printerConnection!!.write(cmd)

                    // Give printer a moment to reply
                    Thread.sleep(500)

                    val resp =
                            if (printerConnection!!.bytesAvailable() > 0) {
                                val data = printerConnection!!.read()
                                String(data, Charsets.UTF_8).trim()
                            } else {
                                "NO_DATA"
                            }

                    sendEvent("printer_status", resp)
                    promise.resolve(resp)
                    return@thread
                }

                // ✅ CHECK LAN
                if (tcpSocket != null && tcpSocket!!.isConnected) {
                    lanOutStream!!.write(cmd)
                    lanOutStream!!.flush()

                    val input = tcpSocket!!.getInputStream()
                    val buffer = ByteArray(1024)
                    val len = input.read(buffer)

                    val resp =
                            if (len > 0) String(buffer, 0, len, Charsets.UTF_8).trim()
                            else "NO_DATA"

                    sendEvent("printer_status", resp)
                    promise.resolve(resp)
                    return@thread
                }

                promise.reject("NO_CONNECTION", "Printer not connected")
            } catch (e: Exception) {
                promise.reject("STATUS_FAILED", e.message)
            }
        }
    }

    @ReactMethod
    fun isConnected(promise: Promise) {
        try {
            val connected =
                    when {
                        printerConnection != null && printerConnection!!.isConnected -> true
                        tcpSocket != null && tcpSocket!!.isConnected -> true
                        else -> false
                    }
            promise.resolve(connected)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    // ------------------------------------------------------------------------------------
    // DISCONNECT & CLEANUP
    // ------------------------------------------------------------------------------------
    @ReactMethod
    fun disconnect(promise: Promise?) {
        try {
            printerConnection?.close()
            printerConnection = null

            closeTcp()

            sendEvent("printer_disconnected", null)
            promise?.resolve(true)
        } catch (e: Exception) {
            promise?.reject("DISCONNECT_FAILED", e.message)
        }
    }

    private fun closeTcp() {
        try {
            tcpSocket?.close()
        } catch (_: Exception) {}
        tcpSocket = null
        lanOutStream = null
    }
}
