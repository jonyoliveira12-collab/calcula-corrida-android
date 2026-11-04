package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,36,28,28); setBackgroundColor(Color.rgb(244,247,250)) }
        root.addView(TextView(this).apply { text="CALCULA CORRIDA"; textSize=25f; setTextColor(Color.rgb(20,42,62)); setTypeface(null,Typeface.BOLD) })
        root.addView(TextView(this).apply { text="Análise automática de ofertas • Uber e 99"; textSize=14f; setTextColor(Color.DKGRAY); setPadding(0,8,0,24) })
        status=TextView(this).apply { textSize=14f; setTextColor(Color.DKGRAY) }; root.addView(status)
        val minKm=EditText(this).apply { hint="Mínimo por km (ex.: 1,80)"; inputType=8194; setText(prefs.getFloat("min_per_km",1.80f).toString()) }
        val minHour=EditText(this).apply { hint="Mínimo por hora (ex.: 35)"; inputType=8194; setText(prefs.getFloat("min_per_hour",35f).toString()) }
        root.addView(minKm); root.addView(minHour)
        root.addView(Button(this).apply { text="SALVAR MEUS LIMITES"; setOnClickListener {
            val km=minKm.text.toString().replace(",",".").toFloatOrNull(); val hour=minHour.text.toString().replace(",",".").toFloatOrNull()
            if(km==null||hour==null||km<=0||hour<=0) Toast.makeText(this@MainActivity,"Informe valores válidos.",Toast.LENGTH_SHORT).show()
            else { prefs.edit().putFloat("min_per_km",km).putFloat("min_per_hour",hour).apply(); Toast.makeText(this@MainActivity,"Limites salvos!",Toast.LENGTH_SHORT).show() }
        } })
        root.addView(Button(this).apply { text="1. AUTORIZAR JANELA FLUTUANTE"; setOnClickListener { if(!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text="2. ATIVAR LEITURA DE OFERTAS"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(TextView(this).apply { text="A leitura depende de autorização do Android e de os aplicativos exporem os textos da oferta. O app apenas calcula: não aceita, recusa nem toca nos botões do Uber ou da 99. Valores brutos, sem descontos."; textSize=12f; setTextColor(Color.DKGRAY); setPadding(0,22,0,0) })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onResume(){ super.onResume(); if(::status.isInitialized) status.text=if(Settings.canDrawOverlays(this)) "Janela flutuante autorizada. Ative também a leitura nas configurações de acessibilidade." else "Falta conceder a permissão de janela flutuante." }
}
