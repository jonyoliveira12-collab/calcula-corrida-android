package br.com.calculacorrida

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val sessionManager by lazy { SessionManager(this) }

    companion object {
        // Chave publishable: própria para uso no aplicativo. Nunca colocar uma secret/service_role aqui.
        private const val SUPABASE_URL = "https://hyoaomusfpeccdchgoik.supabase.co"
        private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_pLJTLctnmN2Z1DVNIoqyBA_DmQZv-EX"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener { authenticate(isRegistration = false) }
        registerButton.setOnClickListener { authenticate(isRegistration = true) }
    }

    private fun authenticate(isRegistration: Boolean) {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Digite um e-mail válido"
            emailInput.requestFocus()
            return
        }
        if (password.length < 8) {
            passwordInput.error = "A senha precisa ter pelo menos 8 caracteres"
            passwordInput.requestFocus()
            return
        }

        setLoading(true)
        activityScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callSupabaseAuth(email, password, isRegistration)
                }

                if (isRegistration && !result.hasSession) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Conta criada! Confira seu e-mail para confirmar o cadastro antes de entrar.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    sessionManager.saveUser(email)
                    Toast.makeText(
                        this@LoginActivity,
                        if (isRegistration) "Conta criada com sucesso!" else "Login realizado!",
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() }
                    ?: "Não foi possível conectar ao Supabase. Verifique sua internet e tente novamente."
                Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun callSupabaseAuth(email: String, password: String, isRegistration: Boolean): AuthResult {
        val endpoint = if (isRegistration) {
            "$SUPABASE_URL/auth/v1/signup"
        } else {
            "$SUPABASE_URL/auth/v1/token?grant_type=password"
        }

        val payload = JSONObject()
            .put("email", email)
            .put("password", password)

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            val json = try {
                if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            } catch (_: Exception) {
                JSONObject()
            }

            if (!response.isSuccessful) {
                val error = json.optString("msg")
                    .ifBlank { json.optString("message") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { json.optString("error") }
                    .ifBlank { "Falha na autenticação (HTTP ${response.code})." }
                throw IOException(error)
            }

            val hasSession = json.optString("access_token").isNotBlank()
            return AuthResult(hasSession)
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        registerButton.isEnabled = !loading
    }

    override fun onDestroy() {
        activityScope.cancel()
        httpClient.dispatcher.cancelAll()
        super.onDestroy()
    }

    private data class AuthResult(val hasSession: Boolean)
}
