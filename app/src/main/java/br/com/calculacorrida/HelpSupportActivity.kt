package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.*

class HelpSupportActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        AppAppearance.apply(this);super.onCreate(state);AppAppearance.styleWindow(this)
        val colors=AppAppearance.palette(this)
        fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
        fun surface()=GradientDrawable().apply{setColor(colors.panel);cornerRadius=dp(22).toFloat();setStroke(dp(1),colors.border)}
        val page=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(24));setBackgroundColor(colors.background)}
        setContentView(ScrollView(this).apply {addView(page)})
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(dp(18)+bars.left,dp(16)+bars.top,dp(18)+bars.right,dp(24)+bars.bottom);insets}
        fun label(value:String,size:Float=14f)=TextView(this).apply{text=value;textSize=size;setTextColor(if(size>=18)colors.text else colors.muted);setPadding(0,dp(6),0,dp(10))}
        fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));background=surface();layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)}}
        fun action(value:String,run:()->Unit)=Button(this).apply{
            text=value;isAllCaps=false;setTextColor(colors.gold);typeface=Typeface.DEFAULT_BOLD;minHeight=dp(50);backgroundTintList=null
            background=GradientDrawable().apply{setColor(colors.input);cornerRadius=dp(14).toFloat();setStroke(dp(1),colors.border)}
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)};setOnClickListener{run()}
        }
        page.addView(action("‹ Voltar"){finish()})
        page.addView(label("Ajuda e suporte",28f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("Estamos por perto. Escolha o assunto para encontrar o melhor caminho."))
        val contact=card();contact.addView(label("Fale com a gente",20f).apply{typeface=Typeface.DEFAULT_BOLD})
        val reasons=arrayOf("Leitura de corridas e etiquetas","Atualização do aplicativo","Conta e acesso","Amigos, localização e mensagens","Dúvidas sobre os futuros planos","Outros assuntos")
        val topic=Spinner(this).apply{adapter=ArrayAdapter(this@HelpSupportActivity,android.R.layout.simple_spinner_dropdown_item,reasons)};contact.addView(topic,LinearLayout.LayoutParams(-1,dp(54)))
        contact.addView(action("Enviar e-mail ao suporte"){
            val version=packageManager.getPackageInfo(packageName,0).versionName
            val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:gbrigante71@gmail.com")).putExtra(Intent.EXTRA_SUBJECT,"JHONDRIVER • "+topic.selectedItem.toString())
                .putExtra(Intent.EXTRA_TEXT,"Assunto: ${topic.selectedItem}\nVersão: $version\n\nDescreva o que aconteceu:\n")
            runCatching{startActivity(intent)}.onFailure{Toast.makeText(this,"E-mail: gbrigante71@gmail.com",Toast.LENGTH_LONG).show()}
        })
        contact.addView(action("Ligar: (41) 99597-6826"){runCatching{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:+5541995976826")))}.onFailure{Toast.makeText(this,"Telefone: (41) 99597-6826",Toast.LENGTH_LONG).show()}})
        page.addView(contact)
        fun info(title:String,body:String,extra:(LinearLayout)->Unit={}) {
            page.addView(card().apply{addView(label(title,19f).apply{typeface=Typeface.DEFAULT_BOLD});addView(label(body));extra(this)})
        }
        info("Seu aplicativo atualizado","As novidades chegam pelo verificador do aplicativo. Confirme Atualizar no Android para manter seus dados."){it.addView(action("Verificar atualização"){AppUpdates.check(this,true)})}
        info("Assinaturas • em breve","Mensal: R$ 10,00\nTrimestral: R$ 25,00\nAnual: R$ 90,00\n\nA contratação ainda não está disponível.")
        info("Leitura e privacidade","A leitura exige acessibilidade e sobreposição autorizadas. Para bancos que exigem desligar a acessibilidade, desligue a leitura e reative pelos ajustes do Android ao voltar a trabalhar.")
        info("Seus amigos, suas escolhas","Somente amigos aceitos podem conversar. Compartilhar localização é opcional e tem duração limitada. Sem resposta do celular, o status deixa de aparecer online. Os áudios exigem permissão do microfone apenas ao gravar.")
    }
}
