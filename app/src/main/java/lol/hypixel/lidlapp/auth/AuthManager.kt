package lol.hypixel.lidlapp.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Handles the OAuth 2.0 / PKCE flow against accounts.lidl.com.
 *
 * Values verified from LittleMinus open-source client:
 *   Client ID:     LidlPlusNativeClient
 *   Client secret: secret  (sent as HTTP Basic Auth)
 *   Redirect URI:  com.lidlplus.app://callback
 *   Scopes:        openid profile offline_access lpprofile lpapis
 *
 * Flow:
 *  1. Build auth URL with PKCE challenge → load in WebView
 *  2. WebView intercepts redirect → extract ?code=
 *  3. Exchange code + verifier for tokens via POST (Basic Auth)
 */
object AuthManager {

    // ── Constants (verified from LittleMinus) ───────────────────────────────
    const val AUTH_URL       = "https://accounts.lidl.com/connect/authorize"
    const val TOKEN_URL      = "https://accounts.lidl.com/connect/token"
    const val REDIRECT_URI   = "com.lidlplus.app://callback"
    const val CLIENT_ID      = "LidlPlusNativeClient"
    const val CLIENT_SECRET  = "secret"
    const val SCOPE          = "openid profile offline_access lpprofile lpapis"
    const val USER_AGENT     = "LidlPlus/16.0.0 (iPhone; iOS 17.0; Scale/3.00)"

    // ── Prefs keys ───────────────────────────────────────────────────────────
    private const val PREFS_NAME          = "lidlpay_prefs"
    private const val KEY_ACCESS_TOKEN    = "access_token"
    private const val KEY_REFRESH_TOKEN   = "refresh_token"

    // ── PKCE ─────────────────────────────────────────────────────────────────

    data class PKCEPair(val verifier: String, val challenge: String)

    fun generatePKCE(): PKCEPair {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val verifier = base64Url(bytes)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII)))
        return PKCEPair(verifier, challenge)
    }

    private fun base64Url(data: ByteArray) =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    // ── Build auth URL ───────────────────────────────────────────────────────

    fun buildAuthUrl(pkce: PKCEPair, country: String = "ES", language: String = "es-ES"): String {
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("Country", country)
            .appendQueryParameter("language", language)
            .appendQueryParameter("state", "12345")
            .appendQueryParameter("nonce", "67890")
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    // ── Token exchange (runs on background thread) ───────────────────────────

    fun exchangeCode(context: Context, code: String, pkce: PKCEPair): String {
        val basicAuth = Base64.encodeToString(
            "$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP
        )

        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .add("code", code)
            .add("code_verifier", pkce.verifier)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty token response")
        if (!response.isSuccessful) throw Exception("Token exchange failed (${response.code}): $responseBody")

        val json = Gson().fromJson(responseBody, JsonObject::class.java)
        val access = json.get("access_token")?.asString ?: throw Exception("No access_token in response")
        val refresh = json.get("refresh_token")?.asString ?: ""

        saveTokens(context, access, refresh)
        return access
    }

    // ── Token refresh (runs on background thread) ────────────────────────────

    fun refreshToken(context: Context): String? {
        val refresh = getRefreshToken(context) ?: return null
        val basicAuth = Base64.encodeToString(
            "$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP
        )

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .addHeader("User-Agent", USER_AGENT)
            .post(body)
            .build()

        return try {
            val response = OkHttpClient().newCall(request).execute()
            val json = Gson().fromJson(response.body?.string(), JsonObject::class.java)
            val newAccess = json.get("access_token")?.asString ?: return null
            val newRefresh = json.get("refresh_token")?.asString ?: refresh
            saveTokens(context, newAccess, newRefresh)
            newAccess
        } catch (e: Exception) {
            null
        }
    }

    // ── Storage ──────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveTokens(context: Context, access: String, refresh: String) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .apply()
    }

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH_TOKEN, null)

    fun isLoggedIn(context: Context) = getAccessToken(context) != null

    fun clearTokens(context: Context) =
        prefs(context).edit().clear().apply()
}
