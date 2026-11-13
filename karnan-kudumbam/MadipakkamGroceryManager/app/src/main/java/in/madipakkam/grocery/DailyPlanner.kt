package in.madipakkam.grocery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class DailyPlannerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("daily_grocery", "Daily grocery plan", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, "daily_grocery")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("கர்ணன் குடும்பம்: Your grocery plan is ready")
            .setContentText("Check today's vegetables, quantities and household budget.")
            .setAutoCancel(true).build()
        manager.notify(7001, notification)
        return Result.success()
    }
}

object DailyPlanner {
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(7, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val request = PeriodicWorkRequestBuilder<DailyPlannerWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("madipakkam_daily_7am", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
