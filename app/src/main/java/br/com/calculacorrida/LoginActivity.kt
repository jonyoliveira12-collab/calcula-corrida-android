private lateinit var emailInput: EditText
private lateinit var passwordInput: EditText
private lateinit var loginButton: Button
private lateinit var registerButton: Button
private lateinit var progressBar: ProgressBar
private lateinit var rememberEmailCheck: android.widget.CheckBox
private lateinit var forgotPasswordText: android.widget.TextView

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
    // Reaproveita a sessão local ao voltar ou abrir novamente o aplicativo.
    if (sessionManager.isLoggedIn()) {
        openMain()
        return
    }
    setContentView(R.layout.activity_login)

    emailInput = findViewById(R.id.emailInput)
    passwordInput = findViewById(R.id.passwordInput)
    loginButton = findViewById(R.id.loginButton)
    registerButton = findViewById(R.id.registerButton)
    progressBar = findViewById(R.id.progressBar)
    rememberEmailCheck = findViewById(R.id.rememberEmailCheck)
    forgotPasswordText = findViewById(R.id.forgotPasswordText)
    forgotPasswordText.setOnClickListener { sendPasswordRecovery() }
    val loginPrefs = getSharedPreferences("login_preferences", MODE_PRIVATE)
    emailInput.setText(loginPrefs.getString("saved_email", "") ?: "")
    rememberEmailCheck.isChecked = loginPrefs.getBoolean("remember_email", true)

    loginButton.setOnClickListener { authenticate(isRegistration = false) }
    registerButton.setOnClickListener { authenticate(isRegistration = true) }
}

private fun authenticate(isRegistration: Boolean) {
    val email = emailInput.text?.toString()?.trim().orEmpty()
    val password = passwordInput.text?.toString().orEmpty()
    getSharedPreferences("login_preferences", MODE_PRIVATE).edit()
        .putBoolean("remember_email", rememberEmailCheck.isChecked)
        .putString("saved_email", if (rememberEmailCheck.isChecked) email else "")
        .apply()

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
                openMain()
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

private fun openMain() {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    finish()
}

private fun sendPasswordRecovery() {
    val email = emailInput.text?.toString()?.trim().orEmpty()
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        emailInput.error = "Digite o e-mail cadastrado para receber o link"
        emailInput.requestFocus()
        return
    }
    setLoading(true)
    activityScope.launch {
        try {
            withContext(Dispatchers.IO) { callSupabaseRecovery(email) }
            Toast.makeText(this@LoginActivity, "Se esse e-mail estiver cadastrado, enviaremos um link para redefinir sua senha. Confira também o spam.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this@LoginActivity, e.message ?: "Não foi possível solicitar a recuperação. Tente novamente.", Toast.LENGTH_LONG).show()
        } finally {
            setLoading(false)
        }
    }
}

private fun callSupabaseRecovery(email: String) {
    val payload = JSONObject().put("email", email)
    val request = Request.Builder()
        .url("$SUPABASE_URL/auth/v1/recover")
        .addHeader("apikey", SUPABASE_PUBLISHABLE_KEY)
        .addHeader("Content-Type", "application/json")
        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()
    httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            val message = json.optString("msg").ifBlank { json.optString("message") }.ifBlank { json.optString("error_description") }.ifBlank { "Não foi possível solicitar a recuperação (HTTP " + response.code + ")." }
            throw IOException(message)
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
    forgotPasswordText.isEnabled = !loading
    forgotPasswordText.alpha = if (loading) 0.5f else 1f
}

override fun onDestroy() {
    activityScope.cancel()
    httpClient.dispatcher.cancelAll()
    super.onDestroy()
}

private data class AuthResult(val hasSession: Boolean)
