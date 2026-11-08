package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable

class LoginActivity : Activity() {

    private val gold = Color.rgb(244, 183, 36)
    private val goldDark = Color.rgb(176, 111, 0)
    private val black = Color.rgb(8, 8, 10)
    private val panel = Color.rgb(20, 20, 24)
    private val input = Color.rgb(40, 40, 47)
    private val white = Color.rgb(245, 245, 245)
    private val gray = Color.rgb(170, 170, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = black
        window.navigationBarColor = black

        val scroll = ScrollView(this).apply {
            setBackgroundColor(black)
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(28))
        }

        scroll.addView(content)
        setContentView(scroll)

        // Logo / marca
        val logoCircle = TextView(this).apply {
            text = "JD"
            gravity = Gravity.CENTER
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = circleDrawable(gold)
        }

        content.addView(
            logoCircle,
            LinearLayout.LayoutParams(dp(104), dp(104)).apply {
                bottomMargin = dp(12)
            }
        )

        val appName = TextView(this).apply {
            text = "JHONDRIVER"
            gravity = Gravity.CENTER
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            letterSpacing = 0.04f
        }

        content.addView(
            appName,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val slogan = TextView(this).apply {
            text = "INTELIGÊNCIA PARA CADA PERCURSO"
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 215, 120))
            letterSpacing = 0.12f
        }

        content.addView(
            slogan,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(32)
            }
        )

        // Card do login
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(25), dp(22), dp(22))
            background = roundedDrawable(
                color = panel,
                radius = 28,
                strokeColor = gold,
                strokeWidth = 2
            )
        }

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val title = TextView(this).apply {
            text = "Acesse sua conta"
            gravity = Gravity.CENTER
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        }

        card.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(22)
            }
        )

        val emailInput = createInput(
            hint = "✉   E-mail",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        card.addView(
            emailInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val passwordInput = createInput(
            hint = "🔒   Senha",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        card.addView(
            passwordInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        val enterButton = TextView(this).apply {
            text = "⇥   ENTRAR"
            gravity = Gravity.CENTER
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = roundedDrawable(
                color = gold,
                radius = 18,
                strokeColor = Color.rgb(255, 224, 122),
                strokeWidth = 1
            )
            isClickable = true
            isFocusable = true
        }

        card.addView(
            enterButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(16)
            }
        )

        val createAccountButton = TextView(this).apply {
            text = "♙   CRIAR CONTA"
            gravity = Gravity.CENTER
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 215, 110))
            background = roundedDrawable(
                color = Color.TRANSPARENT,
                radius = 18,
                strokeColor = gold,
                strokeWidth = 2
            )
            isClickable = true
            isFocusable = true
        }

        card.addView(
            createAccountButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )
        )

        val forgotPassword = TextView(this).apply {
            text = "Esqueci minha senha"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(gray)
            isClickable = true
        }

        content.addView(
            forgotPassword,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
        )

        // Benefícios abaixo do card
        val benefits = TextView(this).apply {
            text = "▣  Cálculo Inteligente     ▥  Mais Lucro     ⚡  Em Tempo Real"
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(235, 235, 235))
        }

        content.addView(
            benefits,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(34)
            }
        )

        enterButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Preencha o e-mail e a senha.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Digite um e-mail válido"
                return@setOnClickListener
            }

            if (password.length < 6) {
                passwordInput.error = "A senha precisa ter pelo menos 6 caracteres"
                return@setOnClickListener
            }

            /*
             * Nesta primeira versão, o login libera a tela principal
             * após validar os campos.
             *
             * Depois vamos trocar este trecho pela conexão segura
             * com o servidor Node.js, sem mudar o visual.
             */
            getSharedPreferences("jhondriver", MODE_PRIVATE)
                .edit()
                .putBoolean("logged_in", true)
                .putString("user_email", email)
                .apply()

            Toast.makeText(this, "Bem-vindo ao JHONDRIVER!", Toast.LENGTH_SHORT).show()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        createAccountButton.setOnClickListener {
            Toast.makeText(
                this,
                "A tela de cadastro será a próxima etapa.",
                Toast.LENGTH_SHORT
            ).show()
        }

        forgotPassword.setOnClickListener {
            Toast.makeText(
                this,
                "A recuperação de senha será conectada ao servidor.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createInput(hint: String, inputType: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(white)
            setHintTextColor(Color.rgb(160, 160, 170))
            textSize = 18f
            setSingleLine(true)
            setPadding(dp(18), 0, dp(18), 0)
            background = roundedDrawable(
                color = input,
                radius = 18,
                strokeColor = Color.rgb(58, 58, 66),
                strokeWidth = 1
            )
        }
    }

    private fun roundedDrawable(
        color: Int,
        radius: Int,
        strokeColor: Int,
        strokeWidth: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(3), Color.rgb(255, 225, 135))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
