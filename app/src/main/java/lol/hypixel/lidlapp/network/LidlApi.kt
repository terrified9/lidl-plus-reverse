package lol.hypixel.lidlapp.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class PaymentMethod(val id: String, val type: String, val last4: String?)

class LidlApi {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun baseHeaders(bearer: String) = mapOf(
        "Accept-Encoding" to "gzip",
        "Authorization" to "Bearer $bearer",
        "User-Agent" to "okhttp/5.3.2",
        "Version" to "3.31.4"
    )

    /** GET /loyalty — returns raw loyalty ID string */
    fun getLoyaltyId(bearer: String): String {
        val req = Request.Builder()
            .url("https://profile.lidlplus.com/api/v1/ES/loyalty")
            .apply { baseHeaders(bearer).forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("Loyalty fetch failed: ${response.code}")
        return response.body?.string()?.trim('"') ?: throw Exception("Empty loyalty response")
    }

    /** GET /store — returns list of payment methods */
    fun fetchPaymentMethods(bearer: String): List<PaymentMethod> {
        val req = Request.Builder()
            .url("https://payments.lidlplus.com/user-profiles/v5/lidl/ES/store")
            .apply { baseHeaders(bearer).forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("Payment methods fetch failed: ${response.code}")

        val body = response.body?.string() ?: throw Exception("Empty response")
        val root = gson.fromJson(body, JsonObject::class.java)
        val array = root.getAsJsonArray("paymentMethods") ?: return emptyList()

        val type = object : TypeToken<List<JsonObject>>() {}.type
        val rawList: List<JsonObject> = gson.fromJson(array, type)

        return rawList.map { obj ->
            PaymentMethod(
                id = obj.get("id")?.asString ?: "",
                type = obj.get("type")?.asString ?: "Card",
                last4 = obj.get("last4")?.asString
            )
        }
    }

    /** POST /pin/validate — returns a short-lived pin token */
    fun getPinToken(bearer: String, pin: String): String {
        val body = gson.toJson(mapOf("pin" to pin)).toRequestBody(JSON)
        val req = Request.Builder()
            .url("https://payments.lidlplus.com/user-profiles/v2/lidl/ES/pin/validate")
            .apply { baseHeaders(bearer).forEach { (k, v) -> addHeader(k, v) } }
            .post(body)
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("PIN validation failed: ${response.code}")

        val respBody = response.body?.string() ?: throw Exception("Empty response")
        return gson.fromJson(respBody, JsonObject::class.java)
            .get("token")?.asString ?: throw Exception("No token in response")
    }

    /** POST /qr — generates a payment QR string */
    fun generateQrCode(
        bearer: String,
        pinToken: String,
        loyaltyId: String,
        paymentMethodId: String
    ): String {
        val payload = mapOf(
            "loyaltyId" to loyaltyId,
            "paymentMethodId" to paymentMethodId
        )
        val body = gson.toJson(payload).toRequestBody(JSON)

        val req = Request.Builder()
            .url("https://payments.lidlplus.com/payment-methods/v2/lidl/ES/store/qr")
            .apply { baseHeaders(bearer).forEach { (k, v) -> addHeader(k, v) } }
            .addHeader("Pin-Token", pinToken)
            .post(body)
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("QR generation failed: ${response.code}")

        val respBody = response.body?.string() ?: throw Exception("Empty response")
        return gson.fromJson(respBody, JsonObject::class.java)
            .get("paymentQR")?.asString ?: throw Exception("No QR in response")
    }

    /** PUT /activate — activates Lidl Pay for the user */
    fun activateLidlPay(bearer: String): Boolean {
        val req = Request.Builder()
            .url("https://payments.lidlplus.com/user-profiles/v3/lidl/ES/store/activate")
            .apply { baseHeaders(bearer).forEach { (k, v) -> addHeader(k, v) } }
            .put("".toRequestBody(null))
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("Activation failed: ${response.code}")

        val respBody = response.body?.string() ?: return false
        return gson.fromJson(respBody, JsonObject::class.java)
            .get("isActive")?.asBoolean ?: false
    }
}
