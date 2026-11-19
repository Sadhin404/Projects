package com.shadhinkhan.breastai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.shadhinkhan.breastai.engine.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val predictedIndex: Int,
    val probabilities: DoubleArray,
    val bbox: FloatArray,
    val thumbFile: File,
)

/** Past analyses stay on this phone: a JSON index + per-entry PNGs in
 *  app-private storage. Mirrors the Flutter app's history behaviour. */
object HistoryStore {

    private fun root(context: Context) = File(context.filesDir, "history").apply { mkdirs() }
    private fun indexFile(context: Context) = File(root(context), "index.json")
    private fun entryDir(context: Context, id: String) = File(root(context), id)

    private fun readIndex(context: Context): JSONArray {
        val f = indexFile(context)
        if (!f.exists()) return JSONArray()
        return try {
            JSONArray(f.readText())
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun writeIndex(context: Context, arr: JSONArray) {
        indexFile(context).writeText(arr.toString())
    }

    private fun entryFrom(o: JSONObject, context: Context): HistoryEntry {
        val probs = o.getJSONArray("probabilities")
        val bb = o.getJSONArray("bbox")
        return HistoryEntry(
            id = o.getString("id"),
            createdAt = o.getLong("createdAt"),
            predictedIndex = o.getInt("predictedIndex"),
            probabilities = DoubleArray(3) { probs.getDouble(it) },
            bbox = FloatArray(4) { bb.getDouble(it).toFloat() },
            thumbFile = File(entryDir(context, o.getString("id")), "original.png"),
        )
    }

    suspend fun loadEntries(context: Context): List<HistoryEntry> =
        withContext(Dispatchers.IO) {
            val arr = readIndex(context)
            (0 until arr.length())
                .map { entryFrom(arr.getJSONObject(it), context) }
                .sortedByDescending { it.createdAt }
        }

    suspend fun add(context: Context, result: AnalysisResult) {
        withContext(Dispatchers.IO) {
            val dir = entryDir(context, result.id).apply { mkdirs() }
            fun save(bmp: Bitmap, name: String) {
                FileOutputStream(File(dir, name)).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            save(result.original, "original.png")
            save(result.heatmap, "heatmap.png")
            save(result.overlay, "overlay.png")
            val arr = readIndex(context)
            val o = JSONObject().apply {
                put("id", result.id)
                put("createdAt", result.createdAt)
                put("predictedIndex", result.predictedIndex)
                put("probabilities", JSONArray(result.probabilities.toList()))
                put("bbox", JSONArray(result.bbox.map { it.toDouble() }))
            }
            val next = JSONArray().put(o)
            for (i in 0 until arr.length()) next.put(arr.getJSONObject(i))
            writeIndex(context, next)
        }
    }

    suspend fun loadResult(context: Context, entry: HistoryEntry): AnalysisResult? =
        withContext(Dispatchers.IO) {
            val dir = entryDir(context, entry.id)
            fun load(name: String): Bitmap? {
                val f = File(dir, name)
                return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            }
            val original = load("original.png") ?: return@withContext null
            val heatmap = load("heatmap.png") ?: return@withContext null
            val overlay = load("overlay.png") ?: return@withContext null
            AnalysisResult(
                id = entry.id,
                createdAt = entry.createdAt,
                predictedIndex = entry.predictedIndex,
                probabilities = entry.probabilities,
                bbox = entry.bbox,
                original = original,
                heatmap = heatmap,
                overlay = overlay,
            )
        }

    suspend fun delete(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            entryDir(context, id).deleteRecursively()
            val arr = readIndex(context)
            val next = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("id") != id) next.put(o)
            }
            writeIndex(context, next)
        }
    }
}
