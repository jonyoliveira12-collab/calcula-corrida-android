package br.com.calculacorrida

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Explicit bank-mode reminder. Never enables accessibility or inspects a banking app. */
object BankModeReminder {
    const val ID=804
    private const val CHANNEL="reader_resume"
    private const val PENDING="bank_resume_pending"
    fun isPending(context:Context)=context.getSharedPreferences("settings",0).getBoolean(PENDING,false)
    fun isReaderEnabled(context:Context):Boolean {
        if(Settings.Secure.getInt(context.contentResolver,Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false
        val expected=ComponentName(context,OfferAccessibilityService::class.java)
        return Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any{ComponentName.unflattenFromString(it)==expected}
    }
    fun begin(context:Context):Boolean {
        context.getSharedPreferences("settings",0).edit().putBoolean(PENDING,true).apply()
        return show(context)
    }
    fun show(context:Context):Boolean {
        if(!isPending(context))return false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Lembrete para retomar leitura",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="Atalho para reativar a acessibilidade após usar o modo banco."
        })
        if(!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val intent=Intent(context,BankResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open=PendingIntent.getActivity(context,ID,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("JHONDRIVER • Retomar leitura")
            .setContentText("Terminou no banco? Toque para abrir a acessibilidade.")
            .setStyle(Notification.BigTextStyle().bigText("Para voltar a analisar corridas, toque em Retomar leitura e confirme a ativação do JHONDRIVER nos ajustes do Android. Suas opções foram mantidas."))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null,"Retomar leitura",open).build()).build()
        return runCatching{manager.notify(ID,notification);true}.getOrDefault(false)
    }
    fun clear(context:Context) {
        context.getSharedPreferences("settings",0).edit().remove(PENDING).apply()
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
    fun openReaderSettings(activity:Activity) {
        val details=Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME,ComponentName(activity,OfferAccessibilityService::class.java).flattenToString())
        Toast.makeText(activity,"Ative o JHONDRIVER nos ajustes do Android para retomar a leitura.",Toast.LENGTH_LONG).show()
        runCatching{activity.startActivity(details)}
            .recoverCatching{activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
            .onFailure{Toast.makeText(activity,"Abra Ajustes → Acessibilidade → JHONDRIVER para reativar.",Toast.LENGTH_LONG).show()}
    }
}
