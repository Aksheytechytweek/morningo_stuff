package `in`.madipakkam.grocery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SessionStore {
    private const val PREFS = "karnan_auth"
    private const val TOKEN = "access_token"
    fun token(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, "").orEmpty()
    fun save(context: Context, token: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN, token).apply() }
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply() }
}

object AuthApi {
    suspend fun authenticate(baseUrl: String, path: String, identifier: String, password: String, name: String = ""): JSONObject =
        withContext(Dispatchers.IO) {
            require(baseUrl.startsWith("https://")) { "Configure the deployed HTTPS Java backend URL first." }
            val body = JSONObject().put("identifier", identifier.trim()).put("password", password)
            if (name.isNotBlank()) body.put("name", name.trim())
            val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                if (status !in 200..299) error(json.optString("message", "Authentication failed ($status)"))
                json
            } finally { connection.disconnect() }
        }
}
