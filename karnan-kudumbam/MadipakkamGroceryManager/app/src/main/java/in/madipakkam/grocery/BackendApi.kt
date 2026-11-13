package in.madipakkam.grocery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Secure client-side transport for the optional Karnan Kudumbam backend.
 * Firebase ID tokens are supplied by the authentication layer at runtime;
 * no service-account key, email key or provider key belongs in this app.
 */
object BackendApi {
    @Volatile
    var baseUrl: String = "" // Set to the deployed HTTPS backend URL after deployment.

    suspend fun compareRewaltoBasket(idToken: String, pincode: String, lines: JSONArray): JSONObject =
        request("POST", "/v1/rewalto/basket/compare", idToken, JSONObject().apply {
            put("pincode", pincode)
            put("lines", lines)
        })

    suspend fun checkRewaltoFreeSample(idToken: String): JSONObject =
        get("/v1/rewalto/sample", idToken)

    suspend fun syncExpenses(idToken: String, householdId: String, expenses: JSONArray): JSONObject =
        request("POST", "/v1/expenses/sync", idToken, JSONObject().apply {
            put("householdId", householdId)
            put("expenses", expenses)
        })

    suspend fun saveReportSubscription(
        idToken: String,
        email: String,
        enabled: Boolean = true,
        monthlyBudget: Double = 15000.0,
        reportTime: String = "20:00"
    ): JSONObject = request("PUT", "/v1/report-subscription", idToken, JSONObject().apply {
        put("email", email)
        put("enabled", enabled)
        put("monthlyBudget", monthlyBudget)
        put("reportTime", reportTime)
    })

    private suspend fun get(path: String, idToken: String): JSONObject = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        require(root.startsWith("https://")) { "Backend HTTPS URL has not been configured." }
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw IllegalStateException(result.optString("error", "Backend request failed ($status)"))
            result
        } finally { connection.disconnect() }
    }

    suspend fun fetchExpenses(idToken: String): JSONObject = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        require(root.startsWith("https://")) { "Backend HTTPS URL has not been configured." }
        val connection = (URL(root + "/v1/expenses/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        try {
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (status !in 200..299) throw IllegalStateException("Sync download failed ($status)")
            JSONObject(response)
        } finally { connection.disconnect() }
    }

    private suspend fun request(method: String, path: String, idToken: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val root = baseUrl.trimEnd('/')
            require(root.startsWith("https://")) { "Backend HTTPS URL has not been configured." }
            val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = if (text.isBlank()) JSONObject() else JSONObject(text)
                if (status !in 200..299) throw IllegalStateException(result.optString("error", "Backend request failed ($status)"))
                result
            } finally {
                connection.disconnect()
            }
        }
}
