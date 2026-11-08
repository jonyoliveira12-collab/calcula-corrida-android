package br.com.calculacorrida

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveUser(email: String, accessToken: String? = null) {
        prefs.edit().putBoolean("logged_in", true).putString("email", email)
            .putString("access_token", accessToken).apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("logged_in", false)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun getEmail(): String? {
        return prefs.getString("email", null)
    }
}
