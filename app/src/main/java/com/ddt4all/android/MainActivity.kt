package com.ddt4all.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1001
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connectedDeviceName: String? = null

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        updateConnectionUi(false)
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "DDT4ALL Android"
            textSize = 27f
            setTextColor(Color.rgb(25, 55, 90))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Диагностика Renault • тест Bluetooth ELM327"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }

        connectButton = Button(this).apply {
            text = "Подключить ELM327"
            setOnClickListener { requestBluetoothPermissionAndChooseDevice() }
        }
        disconnectButton = Button(this).apply {
            text = "Отключить адаптер"
            setOnClickListener { disconnectAdapter() }
        }

        val testButton = Button(this).apply {
            text = "Проверить связь (команда 0100)"
            setOnClickListener { runObdTest() }
        }

        logView = TextView(this).apply {
            text = "Журнал обмена с адаптером появится здесь."
            textSize = 13f
            setTextColor(Color.rgb(35, 45, 55))
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.WHITE)
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(status)
        content.addView(connectButton, matchWrap())
        content.addView(disconnectButton, matchWrap())
        content.addView(testButton, matchWrap())

        val futureFeatures = listOf(
            "Идентификация ЭБУ",
            "Чтение ошибок",
            "Параметры в реальном времени",
            "Конфигурация блоков",
            "Исполнительные тесты"
        )
        futureFeatures.forEach { name ->
            content.addView(Button(this).apply {
                text = name
                isEnabled = false
            }, matchWrap())
        }

        val logTitle = TextView(this).apply {
            text = "Журнал"
            textSize = 17f
            setTextColor(Color.rgb(25, 55, 90))
            setPadding(0, 18, 0, 8)
        }
        content.addView(logTitle)
        content.addView(logView, matchWrap())

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissionAndChooseDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasBluetoothConnectPermission()
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_CONNECT
            )
            return
        }
        choosePairedDevice()
    }

    @Deprecated("Deprecated in Android API; retained for compatibility without AndroidX")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                choosePairedDevice()
            } else {
                appendLog("Нет разрешения Bluetooth Connect. Разрешите доступ в настройках приложения.")
            }
        }
    }

    @Suppress("MissingPermission")
    private fun choosePairedDevice() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            appendLog("На телефоне не найден Bluetooth-адаптер.")
            return
        }
        if (!adapter.isEnabled) {
            appendLog("Сначала включите Bluetooth в настройках телефона.")
            return
        }

        val devices = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            appendLog("Нет разрешения на просмотр сопряжённых Bluetooth-устройств.")
            return
        }

        if (devices.isEmpty()) {
            appendLog("Нет сопряжённых устройств. Сначала выполните сопряжение ELM327 в настройках Android.")
            return
        }

        val labels = devices.map { device ->
            val name = try { device.name ?: "Без имени" } catch (_: SecurityException) { "Bluetooth-устройство" }
            "$name\n${device.address}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите сопряжённый ELM327")
            .setItems(labels) { _, which -> connectToDevice(devices[which]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @Suppress("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        disconnectSocketOnly()
        setStatus("Подключение к ${safeDeviceName(device)}…", false)
        appendLog("Подключаемся к ${safeDeviceName(device)}")

        thread(name = "elm327-connect") {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket = socket
                socket.connect()
                input = socket.inputStream
                output = socket.outputStream
                connectedDeviceName = safeDeviceName(device)

                runOnUiThread {
                    setStatus("Подключено: $connectedDeviceName", true)
                    updateConnectionUi(true)
                    appendLog("Bluetooth SPP соединение установлено.")
                }
                initializeElm327()
            } catch (e: Exception) {
                disconnectSocketOnly()
                runOnUiThread {
                    setStatus("Не удалось подключиться", false)
                    updateConnectionUi(false)
                    appendLog("Ошибка подключения: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return try { device.name ?: device.address } catch (_: SecurityException) { "ELM327" }
    }

    private fun initializeElm327() {
        val commands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        for (command in commands) {
            if (bluetoothSocket?.isConnected != true) return
            try {
                val response = sendCommand(command, 4500)
                runOnUiThread { appendLog("> $command\n${response.ifBlank { "(нет ответа до таймаута)" }}") }
            } catch (e: Exception) {
                runOnUiThread { appendLog("Ошибка команды $command: ${e.message}") }
                return
            }
        }
        runOnUiThread { appendLog("Инициализация ELM327 завершена. Можно выполнить проверку 0100.") }
    }

    private fun runObdTest() {
        if (bluetoothSocket?.isConnected != true) {
            appendLog("Сначала подключите ELM327.")
            return
        }
        thread(name = "elm327-obd-test") {
            try {
                val response = sendCommand("0100", 5000)
                runOnUiThread {
                    appendLog("> 0100\n${response.ifBlank { "(ответ не получен)" }}")
                    if (response.contains("41 00", ignoreCase = true) ||
                        response.contains("4100", ignoreCase = true)
                    ) {
                        appendLog("Получен ответ на запрос поддерживаемых параметров OBD-II.")
                    } else {
                        appendLog("Ответ не подтверждает успешный OBD-II запрос. Для Renault могут потребоваться другие протоколы/инициализация.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { appendLog("Ошибка теста 0100: ${e.message}") }
            }
        }
    }

    @Throws(IOException::class)
    private fun sendCommand(command: String, timeoutMs: Int): String {
        val socket = bluetoothSocket ?: throw IOException("Нет Bluetooth-соединения")
        val streamIn = input ?: throw IOException("Входной поток не открыт")
        val streamOut = output ?: throw IOException("Выходной поток не открыт")

        synchronized(this) {
            streamOut.write((command + "\r").toByteArray(Charsets.US_ASCII))
            streamOut.flush()
        }

        val result = StringBuilder()
        val buffer = ByteArray(256)
        val startedAt = System.currentTimeMillis()

        // Bluetooth RFCOMM InputStream.read() can block indefinitely on some adapters.
        // Poll available() so the command has a real upper time limit.
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (!socket.isConnected) throw IOException("Bluetooth-соединение разорвано")

            val availableBytes = try {
                streamIn.available()
            } catch (e: IOException) {
                throw IOException("Ошибка чтения Bluetooth: ${e.message}", e)
            }

            if (availableBytes > 0) {
                val count = streamIn.read(buffer, 0, minOf(buffer.size, availableBytes))
                if (count < 0) throw IOException("Адаптер закрыл соединение")
                result.append(String(buffer, 0, count, Charsets.US_ASCII))
                if (result.contains(">")) break
            } else {
                try {
                    Thread.sleep(40)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Чтение прервано", e)
                }
            }
        }
        return result.toString().replace(">", "").trim()
    }

    private fun disconnectAdapter() {
        disconnectSocketOnly()
        setStatus("Адаптер ELM327: не подключён", false)
        updateConnectionUi(false)
        appendLog("Соединение закрыто.")
    }

    private fun disconnectSocketOnly() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { bluetoothSocket?.close() } catch (_: Exception) {}
        input = null
        output = null
        bluetoothSocket = null
        connectedDeviceName = null
    }

    private fun updateConnectionUi(connected: Boolean) {
        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
    }

    private fun setStatus(message: String, connected: Boolean) {
        status.text = message
        status.setTextColor(if (connected) Color.rgb(35, 125, 65) else Color.rgb(180, 70, 50))
    }

    private fun appendLog(message: String) {
        if (!::logView.isInitialized) return
        logView.append("\n\n$message")
    }

    override fun onDestroy() {
        disconnectSocketOnly()
        super.onDestroy()
    }
}
