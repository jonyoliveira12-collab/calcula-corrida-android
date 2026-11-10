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
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=Color.WHITE;window.navigationBarColor=Color.WHITE;window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(24));setBackgroundColor(Color.rgb(246,245,243))}
        setContentView(ScrollView(this).apply{addView(page)})
        page.addView(Button(this).apply{text="‹  Voltar";isAllCaps=false;setOnClickListener{finish()}})
        title("Estilo do cartão",24)
        title("Prévia da oferta",15)
        choices(listOf("Recusar","Atenção","Aceitar"),0){state=it;refresh()}
        preview=OfferTagView(this);page.addView(preview,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10);bottomMargin=dp(20)})
        title("CAMPOS EM DESTAQUE",13)
        toggle("Ganho por quilômetro","card_km",true)
        toggle("Ganho por hora","card_hour",true)
        toggle("Nota do passageiro","card_rating",true)
        toggle("Detalhes do embarque","card_details",false)
        toggle("Comentário da IA","card_ai",false)
        title("APARÊNCIA",13)
        toggle("Aviso em destaque","card_banner",true)
        toggle("Exibir o motivo","card_reason",true)
        title("Posição do aviso",16)
        choices(listOf("Acima do cartão","Abaixo do cartão"),if(prefs.getString("card_banner_position","above")=="above")0 else 1){prefs.edit().putString("card_banner_position",if(it==0)"above" else "below").apply();refresh()}
        title("Tema",16)
        val themes=listOf("light","dark","colored")
        choices(listOf("Claro","Escuro","Colorido"),themes.indexOf(prefs.getString("card_theme","light"))){prefs.edit().putString("card_theme",themes[it]).apply();refresh()}
        title("Tamanho do cartão",16)
        val size=TextView(this).apply{setTextColor(Color.DKGRAY)};page.addView(size)
        page.addView(SeekBar(this).apply{max=65;progress=prefs.getInt("overlay_scale",100)-65;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){size.text="${n+65}%";if(user)prefs.edit().putInt("overlay_scale",n+65).apply()};override fun onStartTrackingTouch(v:SeekBar?){};override fun onStopTrackingTouch(v:SeekBar?) {}});size.text="${progress+65}%"})
        title("Posição inicial por aplicativo",16)
        listOf("UBER","99","INDRIVE","IFOOD").forEach {app->title(app,13);choices(listOf("Esquerda","Centro","Direita"),prefs.getInt("card_position_$app",0)){prefs.edit().putInt("card_position_$app",it).apply()}}
        title("Arraste o cartão para ajustar a posição durante a leitura. Os cálculos aparecem antes da resposta da IA.",13)
        refresh()
    }
    private fun title(text:String,size:Int){page.addView(TextView(this).apply{this.text=text;textSize=size.toFloat();setTextColor(Color.rgb(28,30,29));typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(14),0,dp(10))})}
    private fun toggle(text:String,key:String,default:Boolean){page.addView(Switch(this).apply{this.text=text;textSize=16f;setTextColor(Color.BLACK);isChecked=prefs.getBoolean(key,default);setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat()};setOnCheckedChangeListener{_,value->prefs.edit().putBoolean(key,value).apply();refresh()}})}
    private fun choices(labels:List<String>,selected:Int,change:(Int)->Unit){val row=LinearLayout(this);val buttons=labels.mapIndexed {i,label->Button(this).apply{text=label;textSize=12f;isAllCaps=false;setTextColor(if(i==selected)Color.rgb(0,137,108) else Color.DKGRAY);row.addView(this,LinearLayout.LayoutParams(0,dp(52),1f))}};buttons.forEachIndexed{i,b->b.setOnClickListener{buttons.forEachIndexed{j,v->v.setTextColor(if(i==j)Color.rgb(0,137,108)else Color.DKGRAY)};change(i)}};page.addView(row)}
    private fun refresh(){if(!::preview.isInitialized)return;preview.setOffer(listOf("RECUSAR","ATENÇÃO","ACEITAR")[state],60.0,27.8,1.05,29.0,"4.20",listOf(Color.rgb(209,48,57),Color.rgb(204,137,22),Color.rgb(0,146,99))[state],"UBER",29.0,15.0,6.0);preview.setAiInsight("Embarque longo para o valor desta oferta.")}
}
