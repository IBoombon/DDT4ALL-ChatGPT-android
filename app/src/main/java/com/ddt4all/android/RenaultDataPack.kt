package com.ddt4all.android

import android.content.Context
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/** Reads the compact offline pack produced by tools/build_laguna_pack.py. */
class RenaultDataPack(private val context: Context) {
    data class EcuEntry(val familyId: String, val ecuId: String, val label: String,
                        val modelPath: String?, val optimizerPath: String?,
                        val protocol: String?, val baudRate: String?, val fastInitAddress: String?,
                        val slowInitAddress: String?, val kw1: String?, val kw2: String?,
                        val apcRequest: String?, val apcResponse: String?,
                        val identificationRequest: String?, val identificationResponse: String?,
                        val identificationByteRank: String?, val identificationByteMask: String?,
                        val identificationByteValue: String?)
    data class Profile(val tcom: Int, val label: String, val vehicleType: String,
                       val topology: String, val ecus: List<EcuEntry>)

    fun importPack(source: File): File {
        require(source.isFile && source.length() > 0) { "Файл базы пуст или не найден" }
        ZipFile(source).use { zip ->
            val manifest = readJson(zip, "manifest.json")
            require(manifest.optString("format") == "renault-laguna2-pack-v1") { "Неподдерживаемый формат базы" }
            require(manifest.optInt("tcom") == 116) { "Сейчас поддерживается пакет TCOM 116" }
            val report = readJson(zip, "conversion-report.json")
            require(report.optJSONArray("rejectedPickleFiles")?.length() == 0) { "В пакете есть непреобразованные .p; импорт остановлен" }
        }
        val target = File(context.filesDir, "renault-laguna2-tcom116.zip")
        source.copyTo(target, overwrite = true)
        return target
    }

    fun readProfile(pack: File): Profile = withZip(pack) { zip ->
        val m = readJson(zip, "manifest.json")
        val array = m.getJSONArray("ecuReferences")
        val ecus = (0 until array.length()).map { i ->
            val e = array.getJSONObject(i)
            EcuEntry(e.optString("familyId"), e.optString("ecuId"), e.optString("doc"),
                e.optString("modelPath").takeIf { it.isNotBlank() && it != "null" },
                e.optString("optimizerPath").takeIf { it.isNotBlank() && it != "null" },
                e.optString("protocol").takeIf { it.isNotBlank() && it != "null" },
                e.optString("baudRate").takeIf { it.isNotBlank() && it != "null" },
                e.optString("fastInitAddress").takeIf { it.isNotBlank() && it != "null" },
                e.optString("slowInitAddress").takeIf { it.isNotBlank() && it != "null" },
                e.optString("kw1").takeIf { it.isNotBlank() && it != "null" },
                e.optString("kw2").takeIf { it.isNotBlank() && it != "null" },
                e.optString("apcRequest").takeIf { it.isNotBlank() && it != "null" },
                e.optString("apcResponse").takeIf { it.isNotBlank() && it != "null" },
                e.optString("identificationRequest").takeIf { it.isNotBlank() && it != "null" },
                e.optString("identificationResponse").takeIf { it.isNotBlank() && it != "null" },
                e.optString("identificationByteRank").takeIf { it.isNotBlank() && it != "null" },
                e.optString("identificationByteMask").takeIf { it.isNotBlank() && it != "null" },
                e.optString("identificationByteValue").takeIf { it.isNotBlank() && it != "null" })
        }
        Profile(m.getInt("tcom"), m.optString("label"), m.optString("vehicleTypeCode"),
            m.optString("topologyIndex"), ecus)
    }

    fun describeEcu(pack: File, ecu: EcuEntry): String = withZip(pack) { zip ->
        val out = StringBuilder()
        out.append("ЭБУ ${ecu.ecuId} — ${ecu.label}\nСемейство: ${ecu.familyId}\n\n")
        val model = ecu.modelPath
        if (model != null) {
            val entry = zip.getEntry(model)
            if (entry == null) out.append("XML модели отсутствует в пакете: $model\n")
            else {
                val catalog = readXmlCatalog(zip.getInputStream(entry))
                out.append("XML-сеанс: $model\n")
                out.append(catalog).append("\n")
            }
        } else out.append("XML-сеанс не указан в базе.\n\n")
        out.append("ПАРАМЕТРЫ СВЯЗИ ИЗ Uces.xml (СПРАВОЧНЫЕ, НЕ ОТПРАВЛЯЮТСЯ):\n")
        out.append("Протокол: ${ecu.protocol ?: "не указан"}; скорость: ${ecu.baudRate ?: "не указана"} бод\n")
        out.append("Fast init адрес: ${ecu.fastInitAddress ?: "—"}; slow init: ${ecu.slowInitAddress ?: "—"}\n")
        out.append("KW1/KW2: ${ecu.kw1 ?: "—"}/${ecu.kw2 ?: "—"}\n")
        out.append("APC: ${ecu.apcRequest ?: "—"} → ${ecu.apcResponse ?: "—"}\n")
        out.append("Идентификация: ${ecu.identificationRequest ?: "—"} → ${ecu.identificationResponse ?: "—"}")
        if (ecu.identificationByteRank != null) out.append("; байт ${ecu.identificationByteRank}, маска ${ecu.identificationByteMask}, значение ${ecu.identificationByteValue}")
        out.append("\n\n")
        val opt = ecu.optimizerPath
        if (opt != null) {
            val jsonPath = opt.substringBeforeLast('.') + ".json"
            val entry = zip.getEntry(jsonPath)
            if (entry == null) out.append("Преобразованный ресурс .p не найден: $jsonPath\n")
            else {
                val obj = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                out.append("Ресурс оптимизатора: $jsonPath\nЗаписей: ${obj.length()}\n")
                val keys = obj.keys(); var shown = 0
                while (keys.hasNext() && shown < 8) {
                    val key = keys.next(); val value = obj.optString(key)
                    out.append("• $key — ${value.take(180)}${if (value.length > 180) "…" else ""}\n")
                    shown++
                }
            }
        } else out.append("Ресурс оптимизатора не указан.\n")
        out.toString()
    }

    private fun readXmlCatalog(input: java.io.InputStream): String {
        val tags = listOf("Identification", "State", "Parameter", "Command", "FaultInterpretation")
        val titles = mapOf("Identification" to "Идентификаторы", "State" to "Состояния", "Parameter" to "Параметры", "Command" to "Команды (справочник)", "FaultInterpretation" to "Интерпретации ошибок")
        val entries = tags.associateWith { mutableListOf<String>() }
        val counts = tags.associateWith { 0 }.toMutableMap()
        input.use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, null)
            var current: String? = null
            var currentName = ""
            var currentLabel = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name in tags) {
                            current = parser.name
                            currentName = parser.getAttributeValue(null, "name") ?: ""
                            currentLabel = ""
                            counts[current!!] = (counts[current!!] ?: 0) + 1
                        } else if (parser.name == "Label" && current != null) {
                            currentLabel = parser.getAttributeValue(null, "defaultText") ?: ""
                        } else if (parser.name == "Correspondance" && current == "State") {
                            val v = parser.getAttributeValue(null, "value") ?: "?"
                            val label = parser.getAttributeValue(null, "defaultText") ?: ""
                            if (label.isNotBlank() && (entries["State"]?.size ?: 0) < 100) entries["State"]?.add("$currentName = $v: $label")
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name in tags && current == parser.name) {
                        val line = "$currentName — $currentLabel".trimEnd(' ', '—')
                        val list = entries[current]
                        if (list != null && list.size < 18) list.add(line)
                        current = null
                    }
                }
                event = parser.next()
            }
        }
        val out = StringBuilder()
        for (tag in tags) {
            out.append(titles[tag]).append(" (").append(counts[tag] ?: 0).append("):\n")
            val list = entries[tag].orEmpty()
            if (list.isEmpty()) out.append("  Нет отображаемых записей.\n") else list.take(18).forEach { out.append("  • ").append(it).append("\n") }
            if ((counts[tag] ?: 0) > list.size) out.append("  … ещё ").append((counts[tag] ?: 0) - list.size).append("\n")
        }
        out.append("\nКоманды из XML не исполняются: среди них могут быть исполнительные тесты и записи.")
        return out.toString()
    }

    private fun readJson(zip: ZipFile, path: String): JSONObject {
        val entry = zip.getEntry(path) ?: error("Файл $path не найден в базе")
        return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    private inline fun <T> withZip(file: File, block: (ZipFile) -> T): T {
        require(file.isFile) { "Сначала импортируйте базу Renault" }
        return ZipFile(file).use(block)
    }
}
