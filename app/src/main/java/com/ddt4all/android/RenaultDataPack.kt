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
                        val modelPath: String?, val optimizerPath: String?)
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
                e.optString("optimizerPath").takeIf { it.isNotBlank() && it != "null" })
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
                val counts = countXmlElements(zip.getInputStream(entry))
                out.append("XML-сеанс: $model\n")
                out.append("Элементы: ").append(counts.entries.joinToString { "${it.key}: ${it.value}" }).append("\n\n")
            }
        } else out.append("XML-сеанс не указан в базе.\n\n")
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

    private fun countXmlElements(input: java.io.InputStream): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        input.use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = parser.name.substringAfter(':')
                    if (name in setOf("Screen", "Data", "Identification", "State", "Parameter", "Command", "Service", "Mnemonic"))
                        counts[name] = (counts[name] ?: 0) + 1
                }
                event = parser.next()
            }
        }
        return counts
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
