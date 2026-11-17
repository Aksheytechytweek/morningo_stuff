package `in`.madipakkam.grocery

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val AuthGreen = Color(0xFF16734A)

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("karnan_cloud", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(SessionStore.token(context)) }
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(prefs.getString("backend_url", "").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (token.isNotBlank()) {
        content()
        return
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F7F2)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🧺", style = MaterialTheme.typography.displayLarge)
            Text("Karnan Kudumbam", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF173B2B))
            Text("Family & Grocery Manager", color = Color.Gray)
            Spacer(Modifier.height(28.dp))
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Sign in using your email address or mobile number and password. No OTP.", color = Color.Gray)
                    if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(identifier, { identifier = it }, label = { Text("Email or mobile number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(backend, { backend = it.trim(); prefs.edit().putString("backend_url", it.trim()).apply() }, label = { Text("Java backend HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://your-api.example.com") })
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        error = ""
                        if (identifier.isBlank() || password.length < 8 || (register && name.isBlank())) { error = "Enter your details. Password must be at least 8 characters."; return@Button }
                        busy = true
                        scope.launch {
                            try {
                                val result = AuthApi.authenticate(backend, if (register) "/api/v1/auth/register" else "/api/v1/auth/login", identifier, password, name)
                                val newToken = result.optString("accessToken")
                                check(newToken.isNotBlank()) { "Backend did not return an access token." }
                                SessionStore.save(context, newToken); token = newToken
                            } catch (e: Exception) { error = e.localizedMessage ?: "Could not sign in." }
                            finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AuthGreen)) {
                        if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text(if (register) "Create account" else "Login")
                    }
                    TextButton(onClick = { register = !register; error = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (register) "Already registered? Login" else "New here? Create account", color = AuthGreen)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Your password is securely hashed by the Java backend. Never reuse your banking password.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
