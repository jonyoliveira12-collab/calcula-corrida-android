package br.com.calculacorrida

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/** Reads only an image explicitly chosen/shared by the user. Never captures another app. */
class ManualOfferActivity : Activity() {
    private lateinit var fare: EditText
    private lateinit var pickup: EditText
    private lateinit var trip: EditText
    private lateinit var minutes: EditText
    private lateinit var result: TextView
    private lateinit var photo: Button
    private lateinit var calculate: Button
    private val gold = Color.rgb(239,190,65)
    private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private var reading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10,10,12)) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val frame = FrameLayout(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20),dp(20),dp(20),dp(20))
        }
        val width = minOf(dp(460),resources.displayMetrics.widthPixels - dp(24))
        frame.addView(form,FrameLayout.LayoutParams(width,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(frame)
        outer.addView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer) { view,insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text=value; textSize=size; setTextColor(color)
            setPadding(0,dp(8),0,dp(8))
        }
        form.addView(text("JHONDRIVER",25f,gold).apply { typeface=Typeface.DEFAULT_BOLD })
        form.addView(text("Analisar uma oferta",20f))
        form.addView(text("Digite os valores ou escolha uma foto. A leitura da imagem acontece no aparelho. Confira os dados antes de calcular.",13f,Color.LTGRAY))
        photo = Button(this).apply {
            text="ESCOLHER FOTO DA OFERTA"
            setTextColor(Color.BLACK); backgroundTintList=android.content.res.ColorStateList.valueOf(gold)
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },4203)
            }
        }
        form.addView(photo)
        fun input(title: String,id: Int,hintValue: String): EditText {
            form.addView(text(title,13f,gold))
            return EditText(this).apply {
                this.id=id; hint=hintValue; textSize=18f
                inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setSingleLine(true)
                setPadding(dp(14),dp(10),dp(14),dp(10))
                background=GradientDrawable().apply { setColor(Color.rgb(35,35,39)); cornerRadius=dp(12).toFloat();setStroke(dp(1),Color.rgb(132,103,42)) }
                form.addView(this,LinearLayout.LayoutParams(-1,dp(52)))
            }
        }
        fare=input("Valor total da oferta (R$)",R.id.manualFare,"18,40")
        pickup=input("Distância até o passageiro (km)",R.id.manualPickup,"0,00")
        pickup.setText("0")
        trip=input("Distância da viagem (km)",R.id.manualTrip,"12,60")
        minutes=input("Tempo total: chegada + viagem (min)",R.id.manualMinutes,"28")
        form.addView(text("Os indicadores são brutos, antes dos custos. Somente os valores confirmados serão salvos no histórico local; a foto não é salva nem enviada ao servidor.",12f,Color.LTGRAY))
        result=text("Preencha os dados para calcular.",17f,gold).apply { id=R.id.manualResult }
        calculate=Button(this).apply {
            id=R.id.manualCalculate; text="CALCULAR E SALVAR"
            setTextColor(Color.BLACK);backgroundTintList=android.content.res.ColorStateList.valueOf(gold)
            setOnClickListener { calculateOffer() }
        }
        form.addView(calculate)
        form.addView(result)
        form.addView(Button(this).apply { text="VOLTAR";setTextColor(gold);setOnClickListener { finish() } })
        setContentView(outer)
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) readImage(uri)
        }
    }

    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==4203 && resultCode==RESULT_OK) data?.data?.let(::readImage)
    }

    private fun readImage(uri: Uri) {
        if (reading || uri.scheme != "content") { result.text="Escolha uma imagem pelo seletor do Android.";return }
        reading=true
        photo.isEnabled=false;calculate.isEnabled=false
        listOf(fare,pickup,trip,minutes).forEach { it.isEnabled=false }
        result.text="Lendo a imagem no aparelho…"
        var bitmap: Bitmap? = null
        try {
            bitmap=if (Build.VERSION.SDK_INT>=28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)) { decoder,info,_ ->
                    decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                    val largest=maxOf(info.size.width,info.size.height)
                    if (largest>2048) decoder.setTargetSampleSize((largest+2047)/2048)
                }
            } else {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
                var sample=1
                while (maxOf(options.outWidth,options.outHeight)/sample>2048) sample*=2
                options.inJustDecodeBounds=false;options.inSampleSize=sample
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
                    ?: error("Unreadable image")
            }
            val image=requireNotNull(bitmap)
            val reader=recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer=it }
            reader.process(InputImage.fromBitmap(image,0)).addOnSuccessListener { recognized ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val offer=OfferTextParser.parse(recognized.text)
                if (offer==null) result.text="Não consegui identificar uma oferta completa. Digite os valores abaixo."
                else {
                    fun show(n:Double)=String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n)
                    fare.setText(show(offer.fare));pickup.setText(show(offer.pickupKm));trip.setText(show(offer.tripKm));minutes.setText(show(offer.minutes))
                    result.text="Dados extraídos. Confira o valor, as distâncias e o tempo antes de calcular."
                }
            }.addOnFailureListener {
                if (!isDestroyed) result.text="Não foi possível ler essa imagem. Você pode digitar os valores."
            }.addOnCompleteListener {
                image.recycle()
                reading=false
                if (!isDestroyed) {
                    photo.isEnabled=true;calculate.isEnabled=true
                    listOf(fare,pickup,trip,minutes).forEach { it.isEnabled=true }
                }
            }
        } catch (_: Exception) {
            bitmap?.recycle();reading=false
            photo.isEnabled=true;calculate.isEnabled=true
            listOf(fare,pickup,trip,minutes).forEach { it.isEnabled=true }
            result.text="Não foi possível abrir essa imagem. Digite os valores da oferta."
        }
    }

    private fun calculateOffer() {
        fun value(field: EditText): Double? {
            val raw=field.text.toString().trim()
            val normalized=if (raw.contains(',')) raw.replace(".","").replace(',','.') else raw
            return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
        val f=value(fare);val p=value(pickup);val t=value(trip);val m=value(minutes)
        if (f==null || p==null || t==null || m==null) { result.text="Preencha todos os campos com números válidos.";return }
        val prefs=getSharedPreferences("settings",MODE_PRIVATE)
        val calculated=TripCalculator.calculate(f,p,t,m,
            prefs.getFloat("bad_per_km",1.5f).toDouble(),prefs.getFloat("min_per_km",1.8f).toDouble(),
            prefs.getFloat("min_per_hour",50f).toDouble(),prefs.getFloat("good_per_hour",60f).toDouble())
        if (calculated==null) { result.text="Valor, distância da viagem e tempo devem ser maiores que zero; chegada não pode ser negativa.";return }
        val offer=OfferTextParser.ParsedOffer(f,p,t,m,null)
        JourneyStore.record(this,offer,calculated,"manual")
        fun show(n:Double)=String.format(Locale.forLanguageTag("pt-BR"),"%.2f",n)
        result.text="R$ ${show(calculated.perKm)}/km   •   R$ ${show(calculated.perHour)}/hora\nPercurso total: ${show(calculated.totalKm)} km\nAnálise salva no histórico local."
    }

    override fun onDestroy() { recognizer?.close();super.onDestroy() }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
}
