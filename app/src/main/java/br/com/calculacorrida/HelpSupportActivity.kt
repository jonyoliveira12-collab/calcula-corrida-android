package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.*

class HelpSupportActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        AppAppearance.apply(this);super.onCreate(state);AppAppearance.styleWindow(this)
        val colors=AppAppearance.palette(this)
        val page=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(24,20,24,30);setBackgroundColor(colors.background)}
        val scroll=ScrollView(this).apply {addView(page)};setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(24+bars.left,20+bars.top,24+bars.right,30+bars.bottom);insets}
        fun label(value:String,size:Float=15f)=TextView(this).apply{text=value;textSize=size;setTextColor(colors.text);setPadding(0,16,0,12)}
        fun action(value:String,run:()->Unit)=Button(this).apply{text=value;isAllCaps=false;setTextColor(colors.gold);setOnClickListener{run()}}
        page.addView(action("‹ Voltar"){finish()})
        page.addView(label("Ajuda e suporte",28f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("Como podemos ajudar? Selecione o motivo e escolha como entrar em contato."))
        val reasons=arrayOf("Leitura de corridas e etiquetas","Atualização do aplicativo","Conta e acesso","Amigos, localização e mensagens","Dúvidas sobre os futuros planos","Outros assuntos")
        val topic=Spinner(this).apply{adapter=ArrayAdapter(this@HelpSupportActivity,android.R.layout.simple_spinner_dropdown_item,reasons)};page.addView(topic)
        page.addView(action("Enviar e-mail ao suporte"){
            val version=packageManager.getPackageInfo(packageName,0).versionName
            val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:gbrigante71@gmail.com")).putExtra(Intent.EXTRA_SUBJECT,"JHONDRIVER • "+topic.selectedItem.toString())
                .putExtra(Intent.EXTRA_TEXT,"Assunto: ${topic.selectedItem}\nVersão: $version\n\nDescreva o que aconteceu:\n")
            runCatching{startActivity(intent)}.onFailure{Toast.makeText(this,"E-mail: gbrigante71@gmail.com",Toast.LENGTH_LONG).show()}
        })
        page.addView(action("Ligar: (41) 99597-6826"){runCatching{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:+5541995976826")))}.onFailure{Toast.makeText(this,"Telefone: (41) 99597-6826",Toast.LENGTH_LONG).show()}})
        page.addView(label("Atualizações",21f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("Novidades serão disponibilizadas pelo verificador do aplicativo. Baixe a versão compatível e confirme Atualizar no Android para manter seus dados."))
        page.addView(action("Verificar atualização"){AppUpdates.check(this,true)})
        page.addView(label("Assinaturas • em breve",21f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("Mensal: R$ 10,00\nTrimestral: R$ 25,00\nAnual: R$ 90,00\n\nA contratação ainda não está disponível."))
        page.addView(label("Leitura e privacidade",21f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("A leitura exige acessibilidade e sobreposição autorizadas. Para bancos que exigem desligar a acessibilidade, use a opção de desligar a leitura e reative pelos ajustes do Android ao voltar a trabalhar."))
        page.addView(label("Localização e mensagens",21f).apply{typeface=Typeface.DEFAULT_BOLD})
        page.addView(label("Somente amigos aceitos podem conversar. Compartilhar localização é opcional e tem duração limitada. Sem resposta do celular, o status deixa de aparecer online. Os áudios exigem permissão do microfone apenas ao gravar."))
    }
}
