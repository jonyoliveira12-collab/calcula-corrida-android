package com.jhondriver.calculacorrida

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
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

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputLayout
    private lateinit var passwordInput: TextInputLayout
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    // Cliente HTTP para fazer as requisições no servidor
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        
        // Adicionamos uma barra de carregamento para enquanto o servidor responde
        // (Lembrando de adicionar o ProgressBar no seu activity_login.xml depois)
        progressBar = ProgressBar(this)

        loginButton.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim()
            val password = passwordInput.editText?.text.toString().trim()

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Preencha e-mail e senha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Desativa o botão e mostra a barra de progresso
            loginButton.isEnabled = false
            progressBar.visibility = View.VISIBLE

            // Inicia a chamada ao servidor em segundo plano
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // TROQUE PELA URL DO SEU SERVIDOR NODE.JS
                    val serverUrl = "https://SEU_SERVIDOR.com/v1/credentials"

                    val jsonBody = JSONObject()
                    jsonBody.put("email", email)
                    jsonBody.put("password", password)

                    val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url(serverUrl)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        loginButton.isEnabled = true

                        if (response.isSuccessful) {
                            // Login OK! Vai para a MainActivity
                            Toast.makeText(this@LoginActivity, "Login realizado!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            // Servidor respondeu com erro (ex: senha errada)
                            Toast.makeText(this@LoginActivity, "Erro: $responseBody", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        loginButton.isEnabled = true
                        Toast.makeText(this@LoginActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        registerButton.setOnClickListener {
            Toast.makeText(this, "Tela de cadastro futura", Toast.LENGTH_SHORT).show()
        }
    }
}
