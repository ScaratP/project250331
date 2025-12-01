package com.example.project250311.Game

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object GameManager {
    private const val PREFS_NAME = "game_prefs"
    private const val KEY_USER_ID = "user_uuid"
    private const val COLLECTION_LOGS = "game_logs" // Firestore 裡的集合名稱

    private var _userId: String? = null

    // 取得或產生 User ID
    fun getUserId(context: Context): String {
        if (_userId != null) return _userId!!

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedId = prefs.getString(KEY_USER_ID, null)

        return if (storedId != null) {
            _userId = storedId
            storedId
        } else {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newId).apply()
            _userId = newId
            newId
        }
    }

    /**
     * 記錄事件到 Firebase
     * @param context 用來取得 User ID
     * @param eventType 事件類型 (例如: "view_schedule", "mission_complete")
     * @param details 額外的詳細資訊 (例如: {"course": "資料結構"})
     */
    fun logEvent(context: Context, eventType: String, details: Map<String, Any> = emptyMap()) {
        val db = FirebaseFirestore.getInstance()
        val userId = getUserId(context)

        val logData = hashMapOf(
            "user_id" to userId,
            "event_type" to eventType,
            "timestamp" to FieldValue.serverTimestamp(), // 使用伺服器時間
            "device_model" to android.os.Build.MODEL,
            "details" to details
        )

        db.collection(COLLECTION_LOGS)
            .add(logData)
            .addOnSuccessListener {
                Log.d("GameManager", "Log success: $eventType")
            }
            .addOnFailureListener { e ->
                Log.e("GameManager", "Log failed", e)
            }
    }
}