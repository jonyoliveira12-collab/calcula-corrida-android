package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

class LoginActivity : Activity() {

    // Cores Tema Preto e Dourado
    private val bg = Color.rgb(12, 12, 13)
    private val panel = Color.rgb(25, 24, 22)
    private val gold = Color.rgb(218, 170, 78)
    private val white = Color.rgb(247, 244, 237)
    private val muted = Color.rgb(160, 154, 143)
    private val border = Color.rgb(60, 56, 50)
    private val bgInput = Color.rgb(35, 33, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(60), dp(24), dp(24))
            gravity = Gravity.CENTER_VERTICAL
        }

        // LOGO/TITULO
        val title = TextView(this).apply {
            text = "Calcula Corrida"
            textSize = 28f
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Faça login para continuar"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        }
        root.addView(subtitle)

        // CAMPO EMAIL
        val emailLabel = TextView(this).apply {
            text = "E-mail"
            textSize = 13f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(emailLabel)

        val emailInput = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = "seu@email.com"
            setTextColor(white)
            setHintTextColor(muted)
            background = GradientDrawable().apply {
                setColor(bgInput)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), border)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(emailInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        // CAMPO SENHA
        val passLabel = TextView(this).apply {
            text = "Senha"
            textSize = 13f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(passLabel)

        val passInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Sua senha"
            setTextColor(white)
            setHintTextColor(muted)
            background = GradientDrawable().apply {
                setColor(bgInput)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), border)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(passInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })

        // AVISO DE ERRO
        val errorTxt = TextView(this).apply {
            text = ""
            setTextColor(Color.rgb(202, 65, 65))
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = TextView.GONE
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(errorTxt)

        // BOTÃO LOGIN
        val btnLogin = Button(this).apply {
            text = "ENTRAR"
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(gold)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                val email = emailInput.text.toString().trim()
                val pass = passInput.text.toString().trim()

                if (email.isEmpty() || pass.isEmpty()) {
                    errorTxt.text = "Preencha e-mail e senha."
                    errorTxt.visibility = TextView.VISIBLE
                    return@setOnClickListener
                }

                SessionManager(this@LoginActivity).saveUser(email)
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }
        root.addView(btnLogin, LinearLayout.LayoutParams(-1, dp(52)))

        // LINK CADASTRAR
        val txtCadastro = TextView(this).apply {
            text = "Não tem conta? Cadastre-se"
            setTextColor(gold)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
            setOnClickListener {
                Toast.makeText(this@LoginActivity, "Tela de cadastro em breve!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(txtCadastro)

        setContentView(root)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
