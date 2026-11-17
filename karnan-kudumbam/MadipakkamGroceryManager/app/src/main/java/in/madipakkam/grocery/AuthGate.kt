package `in`.madipakkam.grocery

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val AuthGreen = Color(0xFF16734A)

/**
 * Firebase email/password gate. Firebase ID tokens are the only credentials
 * accepted by the Cloudflare Worker; do not use the legacy Java JWT here.
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (currentUser != null) {
        content()
        return
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F7F2)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🧺", style = MaterialTheme.typography.displayLarge)
            Text("Karnan Kudumbam", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF173B2B))
            Text("Family & Grocery Manager", color = Color.Gray)
            Spacer(Modifier.height(28.dp))
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Sign in securely with email and password. New accounts receive an email verification link.", color = Color.Gray)
                    if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        email, { email = it.trim() }, label = { Text("Email address") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            errorMessage = ""
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || password.length < 8 || (register && name.isBlank())) {
                                errorMessage = "Enter a valid email. Password must contain at least 8 characters."
                                return@Button
                            }
                            busy = true
                            scope.launch {
                                try {
                                    val user = if (register) {
                                        val result = auth.createUserWithEmailAndPassword(email, password).await()
                                        if (name.isNotBlank()) result.user?.updateProfile(userProfileChangeRequest { displayName = name.trim() })?.await()
                                        result.user?.sendEmailVerification()?.await()
                                        result.user
                                    } else {
                                        auth.signInWithEmailAndPassword(email, password).await().user
                                    } ?: error("Firebase did not return a user.")
                                    val idToken = user.getIdToken(true).await().token ?: error("Firebase did not return an ID token.")
                                    SessionStore.save(context, idToken)
                                    currentUser = user
                                } catch (e: Exception) {
                                    errorMessage = when {
                                        e.message?.contains("CONFIGURATION_NOT_FOUND", true) == true -> "Enable Email/Password in Firebase Authentication."
                                        else -> e.localizedMessage ?: "Authentication failed. Please try again."
                                    }
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthGreen)
                    ) {
                        if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text(if (register) "Create account" else "Login")
                    }
                    TextButton(onClick = { register = !register; errorMessage = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (register) "Already registered? Login" else "New here? Create account", color = AuthGreen)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Use a unique password. Verify your email before enabling scheduled reports.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
