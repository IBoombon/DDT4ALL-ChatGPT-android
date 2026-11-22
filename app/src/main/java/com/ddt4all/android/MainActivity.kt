package com.ddt4all.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1001
        private const val REQUEST_RENAULT_PACK = 1002
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connectedDeviceName: String? = null
    private val elmCommandLock = Any()
    private val renaultDataPack by lazy { RenaultDataPack(this) }

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

        val profileButton = Button(this).apply {
            text = "БАЗА ЭБУ LAGUNA II (TCOM 116)"
            setOnClickListener { showLagunaProfile() }
        }

        val importPackButton = Button(this).apply {
            text = "ИМПОРТИРОВАТЬ БАЗУ LAGUNA II (ZIP)"
            setOnClickListener { chooseRenaultPack() }
        }
        val importedDbButton = Button(this).apply {
            text = "ОТКРЫТЬ ДИАГНОСТИЧЕСКИЕ СЕАНСЫ TCOM 116"
            setOnClickListener { showImportedLagunaPack() }
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
            text = "Проверить поддержку OBD-II (0100)"
            setOnClickListener { runObdTest() }
        }
        val protocolButton = Button(this).apply {
            text = "Определить протокол адаптера (ATDP)"
            setOnClickListener { runReadOnlyCommand("Протокол адаптера", "ATDP") }
        }
        val rpmButton = Button(this).apply {
            text = "Обороты двигателя (010C)"
            setOnClickListener { runReadOnlyCommand("Обороты двигателя", "010C") }
        }
        val speedButton = Button(this).apply {
            text = "Скорость автомобиля (010D)"
            setOnClickListener { runReadOnlyCommand("Скорость автомобиля", "010D") }
        }
        val dtcButton = Button(this).apply {
            text = "Чтение кодов ошибок (03)"
            setOnClickListener { runReadOnlyCommand("Коды ошибок (режим 03)", "03") }
        }
        val coolantButton = Button(this).apply {
            text = "Температура охлаждающей жидкости (0105)"
            setOnClickListener { runReadOnlyCommand("Температура ОЖ", "0105") }
        }
        val intakeTempButton = Button(this).apply {
            text = "Температура воздуха на впуске (010F)"
            setOnClickListener { runReadOnlyCommand("Температура воздуха", "010F") }
        }
        val throttleButton = Button(this).apply {
            text = "Положение дросселя (0111)"
            setOnClickListener { runReadOnlyCommand("Положение дросселя", "0111") }
        }
        val mafButton = Button(this).apply {
            text = "Расход воздуха MAF (0110)"
            setOnClickListener { runReadOnlyCommand("Расход воздуха MAF", "0110") }
        }
        val voltageButton = Button(this).apply {
            text = "Напряжение блока управления (0142)"
            setOnClickListener { runReadOnlyCommand("Напряжение блока", "0142") }
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
        content.addView(profileButton, matchWrap())
        content.addView(importPackButton, matchWrap())
        content.addView(importedDbButton, matchWrap())
        content.addView(connectButton, matchWrap())
        content.addView(disconnectButton, matchWrap())
        content.addView(testButton, matchWrap())
        content.addView(protocolButton, matchWrap())
        content.addView(rpmButton, matchWrap())
        content.addView(speedButton, matchWrap())
        content.addView(dtcButton, matchWrap())
        content.addView(coolantButton, matchWrap())
        content.addView(intakeTempButton, matchWrap())
        content.addView(throttleButton, matchWrap())
        content.addView(mafButton, matchWrap())
        content.addView(voltageButton, matchWrap())

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

    private fun chooseRenaultPack() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_RENAULT_PACK)
    }

    @Deprecated("Deprecated API retained for compatibility without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RENAULT_PACK || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        thread(name = "renault-pack-import") {
            try {
                val temp = File(cacheDir, "renault-import.zip")
                contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    ?: throw IOException("Не удалось открыть выбранный файл")
                val saved = renaultDataPack.importPack(temp)
                val profile = renaultDataPack.readProfile(saved)
                runOnUiThread {
                    appendLog("База Renault импортирована: TCOM ${profile.tcom}, ЭБУ: ${profile.ecus.size}.")
                    AlertDialog.Builder(this)
                        .setTitle("База Laguna II импортирована")
                        .setMessage("Профиль ${profile.label}\nTCOM ${profile.tcom} • тип ${profile.vehicleType} • топология ${profile.topology}\nВариантов ЭБУ: ${profile.ecus.size}\n\nЭто справочные варианты из базы, а не результат сканирования автомобиля.")
                        .setPositiveButton("Открыть ЭБУ") { _, _ -> showImportedLagunaPack() }
                        .setNegativeButton("Закрыть", null)
                        .show()
                }
            } catch (e: Exception) { runOnUiThread { appendLog("Ошибка импорта базы Renault: ${e.message}") } }
        }
    }

    private fun showImportedLagunaPack() {
        val pack = File(filesDir, "renault-laguna2-tcom116.zip")
        try {
            val profile = renaultDataPack.readProfile(pack)
            val labels = profile.ecus.map { "${it.ecuId} — ${it.label}" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("TCOM ${profile.tcom}: выберите вариант ЭБУ")
                .setItems(labels) { _, which ->
                    val ecu = profile.ecus[which]
                    thread(name = "renault-session-read") {
                        try {
                            val details = renaultDataPack.describeEcu(pack, ecu)
                            runOnUiThread { AlertDialog.Builder(this).setTitle("ЭБУ ${ecu.ecuId}").setMessage(details).setPositiveButton("Закрыть", null).show() }
                        } catch (e: Exception) { runOnUiThread { appendLog("Ошибка чтения диагностического сеанса: ${e.message}") } }
                    }
                }
                .setNegativeButton("Закрыть", null)
                .show()
        } catch (e: Exception) { appendLog("База ещё не импортирована. Выберите ZIP через кнопку импорта. Причина: ${e.message}") }
    }
    private fun showLagunaProfile() {
        try {
            val jsonText = assets.open("renault/laguna2_tcom116.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val profile = JSONObject(jsonText)
            val vehicle = profile.getJSONObject("vehicle")
            val networks = profile.getJSONArray("networks")
            val groups = profile.getJSONArray("ecuGroups")
            val sessions = profile.getJSONObject("sessions")
            val details = StringBuilder()

            details.append("Профиль: ${vehicle.getString("name")}\n")
            details.append("TCOM ${vehicle.getString("tcom")} • тип ${vehicle.getString("vehicleTypeCode")}\n")
            details.append("Топология: ${vehicle.getInt("topologyIndex")}\n\n")
            details.append("СЕТИ И КОНТАКТЫ OBD:\n")
            for (i in 0 until networks.length()) {
                val network = networks.getJSONObject(i)
                val pins = network.getJSONObject("pins")
                details.append(network.getString("type")).append(" (сеть ${network.getInt("index")}): ")
                val pinNames = pins.keys()
                val pinText = ArrayList<String>()
                while (pinNames.hasNext()) {
                    val key = pinNames.next()
                    pinText.add("\\$" + key + "=" + pins.getInt(key))
                }
                details.append(pinText.joinToString(", ")).append("\n")
            }
            details.append("\nКАНДИДАТЫ ЭБУ ИЗ TCOM (НЕ РЕЗУЛЬТАТ СКАНИРОВАНИЯ):\n")
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                details.append("\n").append(group.getString("system"))
                    .append(" [сеть ${group.getInt("networkIndex")}]:\n")
                val ecus = group.getJSONArray("ecus")
                for (j in 0 until ecus.length()) {
                    val ecu = ecus.getJSONObject(j)
                    details.append("• ${ecu.getString("id")}")
                    val doc = ecu.optString("doc")
                    if (doc.isNotBlank()) details.append(" — ").append(doc)
                    details.append("\n")
                }
            }
            details.append("\nОПИСАНИЯ ДИАГНОСТИЧЕСКИХ СЕАНСОВ В АРХИВЕ:\n")
            val sessionKeys = sessions.keys()
            while (sessionKeys.hasNext()) {
                val id = sessionKeys.next()
                val session = sessions.getJSONObject(id)
                details.append("\nЭБУ $").append(id).append(" — ").append(session.getString("databaseName")).append("\n")
                details.append("Идентификации: ").append(session.getInt("identifications"))
                details.append("; состояния: ").append(session.getInt("states"))
                details.append("; параметры: ").append(session.getInt("parameters"))
                details.append("; команды: ").append(session.getInt("commands")).append("\n")
                details.append("Файл: ").append(session.getString("file")).append("\n")
            }
            details.append("\nВАЖНО: эти варианты взяты из базы TCOM 116. Наличие блока в списке не подтверждает, что он установлен в автомобиле. XML описывает названия и структуру диагностических данных; проверенная процедура связи ещё разрабатывается. Запись конфигураций и исполнительные тесты отключены.")

            AlertDialog.Builder(this)
                .setTitle("База Renault — Laguna II")
                .setMessage(details.toString())
                .setPositiveButton("Закрыть", null)
                .show()
        } catch (e: Exception) {
            appendLog("Не удалось открыть профиль Laguna II: ${e.message}")
        }
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
        synchronized(elmCommandLock) {
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
        }
        runOnUiThread { appendLog("Инициализация ELM327 завершена. Можно выполнить проверку 0100.") }
    }

    private fun runObdTest() {
        runReadOnlyCommand("Поддержка стандартных PID", "0100")
    }

    private fun runReadOnlyCommand(label: String, command: String) {
        if (bluetoothSocket?.isConnected != true) {
            appendLog("Сначала подключите ELM327.")
            return
        }
        thread(name = "elm327-read-only") {
            try {
                val response = synchronized(elmCommandLock) {
                    if (bluetoothSocket?.isConnected != true) {
                        throw IOException("Bluetooth-соединение отсутствует")
                    }
                    sendCommand(command, 6000)
                }
                runOnUiThread {
                    appendLog("> $command ($label)\n${response.ifBlank { "(ответ не получен до таймаута)" }}")
                    appendLog(formatReadOnlyResult(command, response))
                }
            } catch (e: Exception) {
                runOnUiThread { appendLog("Ошибка $label: ${e.message}") }
            }
        }
    }

    private fun decodePid(data: String, prefix: String, label: String, convert: (Int) -> String): String {
        val match = Regex(prefix + "([0-9A-F]{2})").find(data)
        return if (match == null) "$label: ответ не распознан или PID не поддерживается."
        else "$label: ${convert(match.groupValues[1].toInt(16))}."
    }

    private fun decodeObdDtc(hex: String): String {
        val a = hex.substring(0, 2).toInt(16)
        val b = hex.substring(2, 4).toInt(16)
        val family = when ((a shr 6) and 0x03) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
        val digit = (a shr 4) and 0x03
        return family + digit + "%02X".format(java.util.Locale.US, a and 0x0F) + "%02X".format(java.util.Locale.US, b)
    }

    private fun formatReadOnlyResult(command: String, response: String): String {
        // Classify ELM327 transport/status text before removing non-hex characters.
        // Otherwise words such as NO DATA or CAN ERROR become meaningless hex fragments.
        val raw = response.trim()
        val upper = raw.uppercase(java.util.Locale.US)
        if (raw.isBlank()) return "Таймаут: адаптер не прислал ответ. Это не подтверждает отказ ЭБУ."
        if (upper.contains("UNABLE TO CONNECT")) return "Ошибка связи: ELM327 не смог установить связь с блоком (UNABLE TO CONNECT)."
        if (upper.contains("CAN ERROR")) return "Ошибка шины CAN, а не положительный ответ ЭБУ."
        if (upper.contains("BUS INIT") && upper.contains("ERROR")) return "Ошибка инициализации автомобильной шины: $raw"
        if (upper.contains("STOPPED")) return "Запрос остановлен адаптером (STOPPED); результат не получен."
        if (upper.contains("NO DATA")) return "Адаптер не получил данных от ЭБУ (NO DATA). Причина может быть в неподдерживаемом запросе, адресации или связи."
        if (upper.contains("BUFFER FULL")) return "Переполнен буфер ELM327; ответ неполный."
        if (upper.contains("ERROR") || upper.contains("?")) return "Адаптер сообщил ошибку: $raw"

        val compact = upper.replace(Regex("[^0-9A-F]"), "")
        val negative = Regex("7F([0-9A-F]{2})([0-9A-F]{2})").find(compact)
        if (negative != null) {
            val service = negative.groupValues[1]
            val nrc = negative.groupValues[2]
            val reason = when (nrc) {
                "10" -> "общий отказ"
                "11" -> "сервис не поддерживается ЭБУ"
                "12" -> "запрошенная функция или PID не поддерживается"
                "13" -> "неверная длина или формат запроса"
                "21" -> "ЭБУ занят, повторите запрос позже"
                "22" -> "условия выполнения запроса сейчас не соблюдены"
                "31" -> "запрошенный параметр или диапазон недоступен"
                "33" -> "доступ запрещён"
                "35" -> "неверный ключ доступа"
                "36" -> "превышено число попыток доступа"
                "37" -> "необходимо выждать перед новой попыткой доступа"
                "78" -> "ЭБУ ещё выполняет запрос"
                else -> "отрицательный ответ ЭБУ, код причины $nrc"
            }
            return "ОТКАЗ ЭБУ: ответ 7F $service $nrc — $reason. Параметр не прочитан."
        }

        return when (command) {
            "ATDP" -> if (raw.isBlank()) "Адаптер не сообщил название протокола."
                else "Ответ ELM327 о протоколе: $raw. AUTO означает автоматический выбор, а не подтверждённый протокол ЭБУ."
            "0100" -> {
                val match = Regex("4100([0-9A-F]{8})").find(compact)
                if (match != null) {
                    val mask = match.groupValues[1]
                    if (mask == "00000000") "ЭБУ вернул нулевую маску поддерживаемых PID. Это не доказывает отсутствие связи с Renault ЭБУ."
                    else "Положительный ответ OBD-II 41 00. Маска поддерживаемых PID: $mask"
                } else "Положительный ответ 0100 не распознан. Сырой ответ: $raw"
            }
            "010C" -> {
                val match = Regex("410C([0-9A-F]{4})").find(compact)
                if (match != null) "Положительный ответ OBD-II 41 0C. Расчётные обороты: ${"%.0f".format(java.util.Locale.US, match.groupValues[1].toLong(16) / 4.0)} об/мин."
                else "Положительный ответ с данными оборотов не получен. Сырой ответ: $raw"
            }
            "010D" -> {
                val match = Regex("410D([0-9A-F]{2})").find(compact)
                if (match != null) "Положительный ответ OBD-II 41 0D. Скорость: ${match.groupValues[1].toInt(16)} км/ч."
                else "Положительный ответ со скоростью не получен. Сырой ответ: $raw"
            }
            "03" -> {
                val data = Regex("43([0-9A-F]+)").find(compact)?.groupValues?.get(1)
                if (data == null) "Положительный ответ режима 03 не распознан. Нельзя делать вывод об отсутствии ошибок."
                else {
                    val codes = data.chunked(4).filter { it.length == 4 && it != "0000" }.map { decodeObdDtc(it) }
                    if (codes.isEmpty()) "Положительный ответ 43: ненулевые стандартные DTC в полученных данных не обнаружены. Блоки Renault отдельно не опрошены."
                    else "Положительный ответ 43. Стандартные коды OBD-II: " + codes.joinToString(", ")
                }
            }
            "0105" -> decodePid(compact, "4105", "Положительный ответ OBD-II 41 05 — температура ОЖ") { b -> "${b - 40} °C" }
            "010F" -> decodePid(compact, "410F", "Положительный ответ OBD-II 41 0F — температура воздуха") { b -> "${b - 40} °C" }
            "0111" -> decodePid(compact, "4111", "Положительный ответ OBD-II 41 11 — положение дросселя") { b -> "${"%.1f".format(java.util.Locale.US, b * 100.0 / 255.0)} %" }
            "0110" -> {
                val m = Regex("4110([0-9A-F]{4})").find(compact)
                if (m == null) "Положительный ответ OBD-II для MAF не получен. Сырой ответ: $raw"
                else "Положительный ответ OBD-II 41 10 — MAF: ${((m.groupValues[1].substring(0,2).toInt(16)*256 + m.groupValues[1].substring(2,4).toInt(16))/100.0)} г/с"
            }
            "0142" -> {
                val m = Regex("4142([0-9A-F]{4})").find(compact)
                if (m == null) "Положительный ответ OBD-II для напряжения не получен. Сырой ответ: $raw"
                else "Положительный ответ OBD-II 41 42 — напряжение: ${((m.groupValues[1].toInt(16))/1000.0)} В"
            }
            else -> "Ответ адаптера/ЭБУ не классифицирован: $raw"
        }
    }

    @Synchronized
    @Throws(IOException::class)
    private fun sendCommand(command: String, timeoutMs: Int): String {
        val socket = bluetoothSocket ?: throw IOException("Нет Bluetooth-соединения")
        val streamIn = input ?: throw IOException("Входной поток не открыт")
        val streamOut = output ?: throw IOException("Выходной поток не открыт")

        // Keep write + response read atomic: concurrent commands would mix ELM327 replies.
        streamOut.write((command + "\r").toByteArray(Charsets.US_ASCII))
        streamOut.flush()

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
