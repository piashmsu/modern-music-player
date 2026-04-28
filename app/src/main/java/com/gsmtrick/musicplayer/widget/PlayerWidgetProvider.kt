package com.gsmtrick.musicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gsmtrick.musicplayer.MainActivity
import com.gsmtrick.musicplayer.R

/**
 * Simple 4x1 home screen widget that launches the app.
 * Tapping any control opens the now-playing screen where the user can
 * control playback. Full widget controls require a bound MediaController,
 * which a launcher widget cannot easily host.
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_play_pause, openApp)
        views.setOnClickPendingIntent(R.id.widget_next, openApp)
        views.setOnClickPendingIntent(R.id.widget_prev, openApp)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }
}
