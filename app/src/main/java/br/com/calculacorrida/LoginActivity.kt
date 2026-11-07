package br.com.calculacorrida

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LoginActivity : Activity() {
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 10, 10)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)

        val savedToken = getSharedPreferences("auth", MODE_PRIVATE).getString("access_token", null)
        if (!savedToken.isNullOrBlank()) {
            openMain()
            return
        }

        loginButton.setOnClickListener { authenticate(isRegistration = false) }
        registerButton.setOnClickListener { authenticate(isRegistration = true) }
    }

    private fun authenticate(isRegistration: Boolean) {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Informe um e-mail válido"
            return
        }
        if (password.length < 8) {
            passwordInput.error = "A senha precisa ter pelo menos 8 caracteres"
            return
        }
        if (SupabaseConfig.PROJECT_URL.contains("SEU-PROJETO") ||
            SupabaseConfig.PUBLISHABLE_KEY.startsWith("SUA_CHAVE")) {
            Toast.makeText(this, "Falta configurar o projeto Supabase. Veja SupabaseConfig.kt.", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)
        thread {
            var success = false
            var message = "Não foi possível conectar."
            var token: String? = null
            try {
                val endpoint = if (isRegistration) "/auth/v1/signup"
                    else "/auth/v1/token?grant_type=password"
                val body = JSONObject().put("email", email).put("password", password).toString()
                val connection = (URL(SupabaseConfig.PROJECT_URL.trimEnd('/') + endpoint).openConnection() as HttpURLConnection)
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                connection.setRequestProperty("Authorization", "Bearer " + SupabaseConfig.PUBLISHABLE_KEY)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                val response = JSONObject(responseText)
                if (code in 200..299) {
                    token = response.optString("access_token").takeIf { it.isNotBlank() && it != "null" }
                    if (isRegistration && token == null) {
                        success = true
                        message = "Cadastro criado! Confirme seu e-mail e depois entre."
                    } else if (token != null) {
                        success = true
                        message = "Login realizado!"
                    } else {
                        message = "Cadastro recebido. Verifique seu e-mail para confirmar a conta."
                        success = true
                    }
                } else {
                    message = response.optString("msg",
                        response.optString("message", response.optString("error_description",
                            response.optString("error", "Erro HTTP $code"))))
                }
                connection.disconnect()
            } catch (e: Exception) {
                message = "Falha de conexão: " + (e.localizedMessage ?: "tente novamente")
            }

            runOnUiThread {
                setLoading(false)
                if (success && token != null) {
                    getSharedPreferences("auth", MODE_PRIVATE).edit()
                        .putString("access_token", token)
                        .putString("email", email)
                        .apply()
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    openMain()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        registerButton.isEnabled = !loading
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
