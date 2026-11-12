package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.*

class CardStyleActivity : Activity() {
    private val prefs by lazy {getSharedPreferences("settings",MODE_PRIVATE)}
    private lateinit var page:LinearLayout
    private lateinit var preview:OfferTagView
    private var state=0
    private data class DemoOffer(val app:String,val fare:Double,val pickupKm:Double,val tripKm:Double,val minutes:Double,val rating:Double,val pickupMinutes:Double)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.rgb(14,15,18);window.navigationBarColor=Color.rgb(14,15,18);window.decorView.systemUiVisibility=0
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(24));setBackgroundColor(Color.rgb(14,15,18))}
        setContentView(ScrollView(this).apply{addView(page)})
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18)+bars.left,dp(16)+bars.top,dp(18)+bars.right,dp(24)+bars.bottom)
            insets
        }

        page.addView(Button(this).apply{text="‹  Voltar";isAllCaps=false;setOnClickListener{finish()}})
        title("Sua etiqueta, do seu jeito",24)
        title("Arraste a etiqueta pela tela durante a leitura. A posição é salva separadamente para cada aplicativo e orientação.",13)
        title("Prévia da oferta",15)
        choices(listOf("Recusar","Analisar","Boa"),0){state=it;refresh()}
        preview=OfferTagView(this);page.addView(preview,LinearLayout.LayoutParams(dp(310),-2).apply{gravity=android.view.Gravity.CENTER_HORIZONTAL;topMargin=dp(10);bottomMargin=dp(20)})
        title("Tamanho do cartão",16)
        val size=TextView(this).apply{setTextColor(Color.rgb(245,216,130))};page.addView(size)
        page.addView(SeekBar(this).apply{max=100;progress=prefs.getInt("overlay_scale",100).coerceIn(60,160)-60;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){size.text="${n+60}%";if(user){prefs.edit().putInt("overlay_scale",n+60).apply();refresh()}};override fun onStartTrackingTouch(v:SeekBar?){};override fun onStopTrackingTouch(v:SeekBar?) {}});size.text="${progress+60}%"})
        title("POSIÇÃO LIVRE",13)
        title("Toque e arraste a etiqueta para cima, para baixo ou para os lados. Ela não fica mais presa à janela da oferta.",13)
        page.addView(Button(this).apply {
            text="Restaurar posições automáticas";isAllCaps=false
            setOnClickListener {
                val editor=prefs.edit()
                prefs.all.keys.filter{it.startsWith("offer_tag_x_") || it.startsWith("offer_tag_y_")}.forEach{editor.remove(it)}
                editor.putLong("card_reset_positions",System.currentTimeMillis()).apply()
                Toast.makeText(this@CardStyleActivity,"Posições restauradas. Arraste novamente para personalizar.",Toast.LENGTH_LONG).show()
            }
        })
        title("CAMPOS EM DESTAQUE",13)
        toggle("Ganho por quilômetro","card_km",true)
        toggle("Ganho por hora","card_hour",true)
        toggle("Nota do passageiro","card_rating",true)
        toggle("Detalhes do embarque","card_details",false)
        toggle("Comentário da IA","card_ai",false)
        title("APARÊNCIA",13)
        toggle("Chamas realistas","card_neon_flames",true)
        toggle("Animar chamas","card_neon_animated",true)
        toggle("Aviso em destaque","card_banner",true)
        toggle("Exibir o motivo","card_compact_reason",false)
        title("Posição do aviso",16)
        choices(listOf("Acima do cartão","Abaixo do cartão"),if(prefs.getString("card_banner_position","above")=="above")0 else 1){prefs.edit().putString("card_banner_position",if(it==0)"above" else "below").apply();refresh()}
        title("Tema",16)
        val themes=listOf("light","dark","colored")
        choices(listOf("Claro","Escuro","Colorido"),themes.indexOf(prefs.getString("card_theme","light"))){prefs.edit().putString("card_theme",themes[it]).apply();refresh()}
        title("A etiqueta acompanha a oferta enquanto ela estiver visível.",13)
        title("Posição inicial por aplicativo",16)
        listOf("UBER","99","INDRIVE","IFOOD").forEach {app->title(app,13);choices(listOf("Esquerda","Centro","Direita"),prefs.getInt("card_position_$app",0)){val editor=prefs.edit().putInt("card_position_$app",it)
            val packages=when(app){"UBER"->listOf("com.ubercab.driver");"99"->listOf("com.app99.driver","com.didiglobal.driver");"INDRIVE"->listOf("sinet.startup.inDriver");else->listOf("br.com.ifood.driver.app")}
            packages.forEach{pkg->listOf(1,2).forEach{orientation->val suffix=pkg.hashCode().toString()+"_"+orientation;editor.remove("offer_tag_x_$suffix").remove("offer_tag_y_$suffix")}}
            editor.putLong("card_reset_positions",System.currentTimeMillis()).apply()}}
        title("Arraste o cartão para ajustar a posição durante a leitura. Os cálculos aparecem antes da resposta da IA.",13)
        refresh()
    }
    private fun title(text:String,size:Int){page.addView(TextView(this).apply{this.text=text;textSize=size.toFloat();setTextColor(Color.rgb(245,216,130));typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(14),0,dp(10))})}
    private fun toggle(text:String,key:String,default:Boolean){page.addView(Switch(this).apply{this.text=text;textSize=16f;setTextColor(Color.rgb(245,244,238));isChecked=prefs.getBoolean(key,default);setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.rgb(30,33,40));cornerRadius=dp(12).toFloat()};setOnCheckedChangeListener{_,value->prefs.edit().putBoolean(key,value).apply();refresh()}})}
    private fun choices(labels:List<String>,selected:Int,change:(Int)->Unit){val row=LinearLayout(this);val buttons=labels.mapIndexed {i,label->Button(this).apply{text=label;textSize=12f;isAllCaps=false;setTextColor(if(i==selected)Color.rgb(0,137,108) else Color.DKGRAY);row.addView(this,LinearLayout.LayoutParams(0,dp(52),1f))}};buttons.forEachIndexed{i,b->b.setOnClickListener{buttons.forEachIndexed{j,v->v.setTextColor(if(i==j)Color.rgb(0,137,108)else Color.DKGRAY)};change(i)}};page.addView(row)}
    private fun refresh(){
        if(!::preview.isInitialized)return
        preview.layoutParams=preview.layoutParams.apply{width=(dp(310)*prefs.getInt("overlay_scale",100)/100).coerceAtMost(resources.displayMetrics.widthPixels-dp(36))}
        // The preview uses real calculations, with distinct examples for each verdict.
        val example=listOf(
            DemoOffer("UBER",10.0,2.0,8.0,20.0,4.70,4.0),
            DemoOffer("UBER",25.0,3.0,12.0,30.0,4.85,5.0),
            DemoOffer("99",36.0,4.0,14.0,30.0,4.96,6.0)
        )[state]
        val result=TripCalculator.calculate(example.fare,example.pickupKm,example.tripKm,example.minutes,passengerRating=example.rating) ?: return
        preview.setOffer(listOf("RECUSAR","ATENÇÃO","ACEITAR")[state],result.minutes,result.totalKm,result.perKm,result.perHour,
            String.format(java.util.Locale.US,"%.2f",example.rating),listOf(Color.rgb(209,48,57),Color.rgb(204,137,22),Color.rgb(0,146,99))[state],
            example.app,example.fare,example.pickupMinutes,example.pickupKm,result.reason)
        preview.setAiInsight("Confira os indicadores desta oferta de exemplo.")
    }
}
