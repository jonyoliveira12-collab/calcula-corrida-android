package br.com.calculacorrida

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LoginActivity : android.app.Activity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    // Servidor temporário - depois a gente troca
    private val SERVER_URL = "http://10.0.2.2:3000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tema preto e dourado
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Título
        val tvTitle = TextView(this).apply {
            text = "JHONDRIVER"
            textSize = 32f
            setTextColor(0xFFFFD700.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 8)
            layoutParams = params
        }
        root.addView(tvTitle)

        // Subtítulo
        val tvSubtitle = TextView(this).apply {
            text = "Faça login para continuar"
            textSize = 16f
            setTextColor(0xFFFFD700.toInt())
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
        }
        root.addView(tvSubtitle)

        // Campo Email
        etEmail = EditText(this).apply {
            hint = "E-mail"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(24, 20, 24, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params
        }
        root.addView(etEmail)

        // Campo Senha
        etPassword = EditText(this).apply {
            hint = "Senha"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(24, 20, 24, 20)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }
        root.addView(etPassword)

        // Botão Login
        btnLogin = Button(this).apply {
            text = "ENTRAR"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFFFFD700.toInt())
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 12)
            layoutParams = params
        }
        root.addView(btnLogin)

        // Botão Cadastrar
        btnRegister = Button(this).apply {
            text = "CADASTRAR"
            setTextColor(0xFFFFD700.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params
        }
        root.addView(btnRegister)

        // Status
        tvStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFFFFD700.toInt())
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 0)
            layoutParams = params
        }
        root.addView(tvStatus)

        // ProgressBar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER
            params.setMargins(0, 16, 0, 0)
            layoutParams = params
        }
        root.addView(progressBar)

        setContentView(root)

        // Verifica se já está logado
        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            goToMain()
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                tvStatus.text = "Preencha todos os campos!"
                return@setOnClickListener
            }

            authenticate(email, password, isRegister = false)
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                tvStatus.text = "Preencha todos os campos!"
                return@setOnClickListener
            }

            authenticate(email, password, isRegister = true)
        }
    }

    private fun authenticate(email: String, password: String, isRegister: Boolean) {
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false
        btnRegister.isEnabled = false
        tvStatus.text = if (isRegister) "Cadastrando..." else "Entrando..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = if (isRegister) "/v1/credentials/register" else "/v1/credentials/login"
                val jsonBody = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }.toString()

                val request = Request.Builder()
                    .url("$SERVER_URL$endpoint")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    btnRegister.isEnabled = true

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val success = json.optBoolean("success", true)
                        val token = json.optString("token", "")

                        if (success) {
                            SessionManager(this@LoginActivity).saveSession(email, token)
                            tvStatus.text = "✅ Sucesso!"
                            Toast.makeText(this@LoginActivity, "Bem-vindo!", Toast.LENGTH_SHORT).show()
                            goToMain()
                        } else {
                            tvStatus.text = "❌ ${json.optString("message", "Erro ao autenticar")}"
                        }
                    } else {
                        val json = try { JSONObject(responseBody) } catch (e: Exception) { null }
                        val msg = json?.optString("message") ?: "Erro ${response.code}"
                        tvStatus.text = "❌ $msg"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    btnRegister.isEnabled = true
                    tvStatus.text = "❌ Erro de conexão: ${e.message}"
                }
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
