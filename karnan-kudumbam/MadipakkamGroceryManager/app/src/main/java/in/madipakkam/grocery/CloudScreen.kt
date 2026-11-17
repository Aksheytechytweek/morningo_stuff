package `in`.madipakkam.grocery

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable fun CloudScreen(db: GroceryDb) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("karnan_cloud", Context.MODE_PRIVATE) }
    var url by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var postalCode by remember { mutableStateOf(prefs.getString("postal_code", "600091") ?: "600091") }
    var householdId by remember { mutableStateOf(prefs.getString("household_id", "") ?: "") }
    var inviteEmail by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var joinHouseholdId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Cloud connection is optional. Local expenses work without it.") }
    var signedIn by remember { mutableStateOf(try { FirebaseAuth.getInstance().currentUser?.email } catch (_: Exception) { null }) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener { task ->
                    if (task.isSuccessful) { signedIn = task.result.user?.email; message = "Google sign-in successful" }
                    else message = "Firebase sign-in failed: ${task.exception?.localizedMessage}"
                }
            } catch (e: Exception) { message = "Google sign-in failed: ${e.localizedMessage}" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cloud & live shopping", style = MaterialTheme.typography.headlineSmall)
        Text("Firebase sign-in, secure expense sync, and Rewalto price checks using free credits only. No paid fallback is permitted.")
        OutlinedTextField(value=url,onValueChange={url=it},label={Text("HTTPS backend URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=postalCode,onValueChange={postalCode=it},label={Text("Delivery PIN")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            prefs.edit().putString("backend_url",url.trim()).putString("postal_code",postalCode.trim()).apply()
            BackendApi.baseUrl=url.trim(); message="Saved locally."
        }) { Text("Save connection settings") }
        Text("Signed in: ${signedIn ?: "No"}")
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        Text("Email verification: ${if (firebaseUser?.isEmailVerified == true) "Verified" else "Not verified"}")
        if (firebaseUser != null && firebaseUser.isEmailVerified != true) {
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        firebaseUser.sendEmailVerification().await()
                        message = "Verification email sent to ${firebaseUser.email}. Open the link, then sign in again."
                    } catch (e: Exception) { message = "Could not send verification email: ${e.localizedMessage}" }
                }
            }) { Text("Resend verification email") }
        }
        Button(onClick={
            try {
                val resId=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
                if (resId==0) { message="Add Firebase google-services.json and enable Google Sign-In first." }
                else {
                    val options=GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(context.getString(resId)).requestEmail().build()
                    launcher.launch(GoogleSignIn.getClient(context,options).signInIntent)
                }
            } catch(e:Exception) { message="Firebase setup required: ${e.localizedMessage}" }
        }) { Text("Sign in with Google") }
        OutlinedTextField(value=householdId,onValueChange={householdId=it},label={Text("Household ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(true).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val result=BackendApi.createHousehold(token)
                    householdId=result.getString("householdId")
                    prefs.edit().putString("household_id",householdId).apply()
                    message="Household created. Share this Household ID with invited family members."
                } catch(e:Exception) { message="Could not create household: ${e.localizedMessage}" }
            }
        }) { Text("Create my household") }
        OutlinedTextField(value=inviteEmail,onValueChange={inviteEmail=it},label={Text("Family member email to invite")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(true).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val result=BackendApi.inviteHouseholdMember(token,householdId.ifBlank { user.uid },inviteEmail)
                    message="Invitation created for ${result.optString("invitedEmail")}. Share Household ID ${result.optString("householdId")} and code ${result.optString("inviteCode")} with them. Expires in 7 days."
                } catch(e:Exception) { message="Could not create invitation: ${e.localizedMessage}" }
            }
        },enabled=inviteEmail.contains("@")) { Text("Create family invitation") }
        OutlinedTextField(value=joinHouseholdId,onValueChange={joinHouseholdId=it},label={Text("Invited household ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=joinCode,onValueChange={joinCode=it},label={Text("Invitation code")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    require(user.isEmailVerified) { "Verify your email before joining a household." }
                    val token=user.getIdToken(true).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val result=BackendApi.joinHousehold(token,joinHouseholdId,joinCode)
                    householdId=result.getString("householdId")
                    prefs.edit().putString("household_id",householdId).apply()
                    message="Joined household successfully. Shared expense sync is now enabled."
                } catch(e:Exception) { message="Could not join household: ${e.localizedMessage}" }
            }
        },enabled=joinHouseholdId.isNotBlank()&&joinCode.isNotBlank()) { Text("Join family household") }
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(false).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val sample=BackendApi.checkRewaltoFreeSample(token)
                    message="Rewalto public sample OK · version ${sample.optString("version")} · catalogue reachable · 0 credits consumed."
                } catch(e:Exception) { message="Free sample check failed: ${e.localizedMessage}" }
            }
        }) { Text("Test Rewalto free sample (0 credits)") }
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(false).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val deviceId=prefs.getString("device_id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id",it).apply() }
                    val expenses=JSONArray()
                    db.expenses("%") .forEach { item -> expenses.put(JSONObject().apply {
                        put("id", "${deviceId}_${item.id}");put("date",item.date);put("amount",item.amount);put("category",item.category);put("note",item.note);put("payment",item.payment)
                    }) }
                    BackendApi.syncExpenses(token,householdId.ifBlank { user.uid },expenses)
                    message="Uploaded ${expenses.length()} expense records. Other-device downloads are available using the sync API."
                } catch(e:Exception) { message="Sync unavailable: ${e.localizedMessage}" }
            }
        }) { Text("Sync expenses to cloud") }
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(false).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val deviceId=prefs.getString("device_id", "") ?: ""
                    val imported=prefs.getStringSet("imported_remote_ids",emptySet())!!.toMutableSet()
                    val remote=BackendApi.fetchExpenses(token,householdId.ifBlank { user.uid }).getJSONArray("expenses")
                    var count=0
                    for(i in 0 until remote.length()) {
                        val item=remote.getJSONObject(i)
                        val id=item.getString("id")
                        if(id.startsWith("${deviceId}_") || id in imported) continue
                        db.addExpenseForDate(item.getString("date"),item.getDouble("amount"),item.optString("category","Other"),item.optString("note",""),item.optString("payment","Cash"))
                        imported.add(id)
                        prefs.edit().putStringSet("imported_remote_ids",imported.toSet()).apply()
                        count++
                    }
                    message="Downloaded $count new expense records."
                } catch(e:Exception) { message="Download unavailable: ${e.localizedMessage}" }
            }
        }) { Text("Download expenses from cloud") }
        Button(onClick={
            scope.launch {
                try {
                    val user=FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
                    val token=user.getIdToken(false).await().token ?: error("No Firebase token")
                    BackendApi.baseUrl=url.trim()
                    val accountEmail = user.email?.trim().orEmpty()
                    require(user.isEmailVerified && accountEmail.isNotBlank()) {
                        "Verify the signed-in account email first. Then sign out and sign in again."
                    }
                    BackendApi.saveReportSubscription(token,accountEmail,householdId=householdId.ifBlank { user.uid })
                    message="Daily PDF report saved for $accountEmail at 8:00 PM IST. It will send after the backend email provider is configured."
                } catch(e:Exception) { message="Email activation unavailable: ${e.localizedMessage}" }
            }
        }) { Text("Enable daily report for my verified email") }
        Text("Open the Basket tab and tap Check Live Prices to run a manual Rewalto comparison. The backend uses only the free monthly credit balance and stops if it cannot safely verify that allowance.", style=MaterialTheme.typography.bodySmall)
        Text(message, style=MaterialTheme.typography.bodyMedium)
        Text("Rewalto public sample and price observations are not retailer-owned APIs or guaranteed checkout quotes. No orders are placed by this app.", style=MaterialTheme.typography.bodySmall)
    }
}

