package br.com.calculacorrida

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Locale

/** Latest saved offer stays in the notification shade after its floating card disappears. */
object OfferNotifications {
    const val CHANNEL = "saved_offers"
    const val ID = 503
    fun show(context:Context,result:TripResult,sourcePackage:String) {
        val app=DriverPackages.name(sourcePackage) ?: return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Ofertas salvas",NotificationManager.IMPORTANCE_LOW).apply {
            description="Última oferta identificada e salva no histórico"
        })
        if(!manager.areNotificationsEnabled())return
        val launch=context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open=PendingIntent.getActivity(context,ID,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text=String.format(Locale("pt","BR"),"R$ %.2f • %.1f km • %.0f min\nR$ %.2f/km • R$ %.2f/h",result.fare,result.totalKm,result.minutes,result.perKm,result.perHour)
        val notification=Notification.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_offer_notification)
            .setContentTitle("$app • oferta salva")
            .setContentText(text.replace('\n',' '))
            .setStyle(Notification.BigTextStyle().bigText(text+"\nToque para abrir o JHONDRIVER e consultar o histórico."))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_STATUS).build()
        try { manager.notify(ID,notification) } catch(_:SecurityException) { /* History remains saved if permission is denied. */ }
    }
}
