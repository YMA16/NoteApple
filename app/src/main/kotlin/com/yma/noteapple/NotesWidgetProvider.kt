package com.yma.noteapple

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NotesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val noteCount = runCatching { NotesDatabase(context).notes().size }.getOrDefault(0)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.note_widget)
            views.setTextViewText(R.id.widget_count, "$noteCount פתקים")
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("new_note", true)
            }
            val pending = PendingIntent.getActivity(
                context, 5200 + id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_new, pending)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
