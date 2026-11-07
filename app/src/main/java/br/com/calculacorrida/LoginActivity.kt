package br.com.calculacorrida

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // O XML atual usa EditText diretamente, não TextInputLayout.
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        loginButton.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Preencha e-mail e senha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Não simula autenticação nem envia senha para um servidor fictício.
            Toast.makeText(
                this,
                "A autenticação Supabase precisa ser configurada antes do primeiro acesso.",
                Toast.LENGTH_LONG
            ).show()
        }

        registerButton.setOnClickListener {
            Toast.makeText(
                this,
                "O cadastro será liberado após configurar a autenticação Supabase.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
