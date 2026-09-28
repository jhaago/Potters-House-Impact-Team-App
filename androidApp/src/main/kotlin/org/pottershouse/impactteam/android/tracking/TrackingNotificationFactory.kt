package org.pottershouse.impactteam.android.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.pottershouse.impactteam.domain.ActiveTripSession

class TrackingNotificationFactory(private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active trip tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when an Impact Team trip is sharing this phone's location"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun create(session: ActiveTripSession): Notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Impact Team tracking active")
        .setContentText(contentText(session))
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()

    companion object {
        const val CHANNEL_ID = "active_trip_tracking"
        const val NOTIFICATION_ID = 4101

        fun contentText(session: ActiveTripSession): String =
            "${session.tripName} is sharing your location."
    }
}
