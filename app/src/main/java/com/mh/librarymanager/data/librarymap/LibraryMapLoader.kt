package com.mh.librarymanager.data.librarymap

import android.content.Context
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.LibraryMap
import com.mh.librarymanager.domain.LibraryMapSection
import com.mh.librarymanager.domain.MapColorLabels
import com.mh.librarymanager.domain.MapHotspot
import com.mh.librarymanager.domain.ShelfSlot
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object LibraryMapLoader {
    private val assetFiles = mapOf(
        BookPlace.OTZAR to "library_maps/otzar.json",
        BookPlace.BEIS_MIDRASH to "library_maps/beis_midrash.json",
    )

    fun load(context: Context, place: BookPlace): LibraryMap? {
        val assetPath = assetFiles[place] ?: return null
        return try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            parse(context, json)
        } catch (_: IOException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    fun parse(context: Context, json: String): LibraryMap? {
        return try {
            parseOrThrow(context, json)
        } catch (_: JSONException) {
            null
        }
    }

    private fun parseOrThrow(context: Context, json: String): LibraryMap? {
        val root = JSONObject(json)
        val imageName = root.optString("imageDrawable").trim()
        if (imageName.isEmpty()) return null

        val imageResId = context.resources.getIdentifier(imageName, "drawable", context.packageName)
        if (imageResId == 0) return null

        val sections = root.getJSONArray("sections")
        val parsedSections = buildList {
            for (i in 0 until sections.length()) {
                add(parseSection(sections.getJSONObject(i)))
            }
        }
        return LibraryMap(
            mapId = root.getString("mapId"),
            place = BookPlace.fromStored(root.getString("place")) ?: return null,
            frameWidth = root.getInt("frameWidth"),
            frameHeight = root.getInt("frameHeight"),
            imageResId = imageResId,
            sections = parsedSections,
        )
    }

    private fun parseSection(obj: JSONObject): LibraryMapSection {
        val hotspot = obj.getJSONObject("hotspot")
        val from = obj.optJSONObject("from")
        val to = obj.optJSONObject("to")
        val columns = obj.optJSONArray("columns")?.let { arr ->
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } ?: emptyList()
        return LibraryMapSection(
            id = obj.getString("id"),
            label = obj.getString("label"),
            color = MapColorLabels.normalize(obj.getString("color")),
            from = ShelfSlot(from?.optString("letter").orEmpty(), from?.optInt("number") ?: 0),
            to = ShelfSlot(to?.optString("letter").orEmpty(), to?.optInt("number") ?: 0),
            hotspot = MapHotspot(
                x = hotspot.getDouble("x").toFloat(),
                y = hotspot.getDouble("y").toFloat(),
                w = hotspot.getDouble("w").toFloat(),
                h = hotspot.getDouble("h").toFloat(),
            ),
            numberOnly = obj.optBoolean("numberOnly", false),
            column = obj.optString("column").trim(),
            columns = columns,
            shelfFrom = obj.optInt("shelfFrom", 0),
            shelfTo = obj.optInt("shelfTo", 0),
        )
    }
}
