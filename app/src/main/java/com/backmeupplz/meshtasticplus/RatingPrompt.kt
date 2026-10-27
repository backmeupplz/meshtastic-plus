package com.backmeupplz.meshtasticplus

import android.app.Activity
import androidx.core.content.edit
import java.time.LocalDate

private const val DAY = 86_400_000L

/**
 * The store's own rating prompt, never a question of ours first: after 20 sent messages, once the app was opened
 * on 3 days and first used 3+ days ago. At most twice per install, the second time 120+ days later on a newer
 * version, since neither store says whether the user rated. Kept on this phone, nothing is sent anywhere.
 */
fun ratingDue(now: Long, first: Long, days: Int, sent: Int, asks: Int, lastAsk: Long, lastVersion: Long, version: Long) =
    days >= 3 && now - first >= 3 * DAY && sent >= 20 &&
        (asks == 0 || asks == 1 && now - lastAsk >= 120 * DAY && version > lastVersion)

object Rating {
    private val prefs get() = Mesh.appContext.getSharedPreferences("mesh", 0)
    private val started = System.currentTimeMillis()

    /** The app is on screen: counts the days it's used on. */
    fun used() {
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong("rateDay", -1) == today) return
        prefs.edit {
            if (!prefs.contains("rateFirst")) putLong("rateFirst", System.currentTimeMillis())
            putLong("rateDay", today)
            putInt("rateDays", prefs.getInt("rateDays", 0) + 1)
        }
    }

    fun sent() = prefs.edit { putInt("rateSent", prefs.getInt("rateSent", 0) + 1) }

    /** Back from a chat to the list. Not after a message of ours failed since the app started. */
    fun maybeAsk(activity: Activity) {
        val p = prefs
        val now = System.currentTimeMillis()
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
        if (Mesh.messages.any { it.mine && it.status == "✗" && it.time >= started }) return
        val asks = p.getInt("rateAsks", 0)
        if (!ratingDue(now, p.getLong("rateFirst", now), p.getInt("rateDays", 0), p.getInt("rateSent", 0), asks, p.getLong("rateLastAsk", 0), p.getLong("rateVersion", 0), version)) return
        p.edit { putInt("rateAsks", asks + 1); putLong("rateLastAsk", now); putLong("rateVersion", version) }
        requestReview(activity)
    }
}
