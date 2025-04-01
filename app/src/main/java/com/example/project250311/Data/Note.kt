package com.example.project250311.Data

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String?,
    val formattedContent: String?,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    // Helper to convert AnnotatedString to a serialized string
    fun toFormattedContent(annotatedString: AnnotatedString): String {
        val spans = annotatedString.spanStyles.map { span ->
            mapOf(
                "start" to span.start,
                "end" to span.end,
                "style" to mapOf(
                    "fontSize" to span.item.fontSize.value,
                    "fontWeight" to (span.item.fontWeight?.weight ?: 400),
                    "fontStyle" to (span.item.fontStyle?.toString() ?: "Normal"),
                    "color" to span.item.color.toArgb().toUInt().toString(16).padStart(8, '0'), // Store as ARGB hex
                    "background" to span.item.background.toArgb().toUInt().toString(16).padStart(8, '0') // Store as ARGB hex
                )
            )
        }
        val data = mapOf(
            "text" to annotatedString.text,
            "spans" to spans
        )
        return Gson().toJson(data)
    }

    // Helper to convert serialized string back to AnnotatedString
    fun toAnnotatedString(): AnnotatedString {
        if (formattedContent.isNullOrEmpty()) return AnnotatedString(content ?: "")

        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(formattedContent, type)
            val text = data["text"] as? String ?: (content ?: "")
            val spans = data["spans"] as? List<Map<String, Any>> ?: emptyList()

            val builder = AnnotatedString.Builder()
            builder.append(text)

            spans.forEach { span ->
                val start = (span["start"] as? Double)?.toInt() ?: 0
                val end = (span["end"] as? Double)?.toInt() ?: 0
                val styleData = span["style"] as? Map<String, Any> ?: emptyMap()

                val fontSize = (styleData["fontSize"] as? Double)?.toFloat() ?: 20f
                val fontWeight = (styleData["fontWeight"] as? Double)?.toInt() ?: 400
                val fontStyle = styleData["fontStyle"] as? String

                // Handle both old (numeric) and new (hex string) formats for color and background
                val color = when (val colorValue = styleData["color"]) {
                    is String -> try {
                        Log.d("Note", "Parsing color hex: $colorValue")
                        Color(colorValue.toLong(16))
                    } catch (e: Exception) {
                        Log.e("Note", "Failed to parse color hex: $colorValue, error: ${e.message}")
                        Color.Black
                    }
                    is Double -> try {
                        Log.d("Note", "Parsing color double: $colorValue")
                        Color(colorValue.toLong())
                    } catch (e: Exception) {
                        Log.e("Note", "Failed to parse color double: $colorValue, error: ${e.message}")
                        Color.Black
                    }
                    else -> {
                        Log.w("Note", "Invalid color value: $colorValue")
                        Color.Black
                    }
                }
                val background = when (val backgroundValue = styleData["background"]) {
                    is String -> try {
                        Log.d("Note", "Parsing background hex: $backgroundValue")
                        Color(backgroundValue.toLong(16))
                    } catch (e: Exception) {
                        Log.e("Note", "Failed to parse background hex: $backgroundValue, error: ${e.message}")
                        Color.Transparent
                    }
                    is Double -> try {
                        Log.d("Note", "Parsing background double: $backgroundValue")
                        Color(backgroundValue.toLong())
                    } catch (e: Exception) {
                        Log.e("Note", "Failed to parse background double: $backgroundValue, error: ${e.message}")
                        Color.Transparent
                    }
                    else -> {
                        Log.w("Note", "Invalid background value: $backgroundValue")
                        Color.Transparent
                    }
                }

                builder.addStyle(
                    SpanStyle(
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight(fontWeight),
                        fontStyle = if (fontStyle == "Italic") FontStyle.Italic else FontStyle.Normal,
                        color = color,
                        background = background
                    ),
                    start,
                    end
                )
            }
            return builder.toAnnotatedString()
        } catch (e: Exception) {
            Log.e("Note", "Failed to deserialize formattedContent: ${e.message}")
            return AnnotatedString(content ?: "")
        }
    }
}