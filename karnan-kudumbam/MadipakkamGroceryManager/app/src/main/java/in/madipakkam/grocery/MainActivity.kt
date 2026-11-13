package in.madipakkam.grocery

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private val Green = Color(0xFF16734A)
private val Pale = Color(0xFFF1F7F2)
private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyPlanner.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val db = GroceryDb(this)
        setContent { GroceryApp(db) }
    }
}

@Composable fun GroceryApp(db: GroceryDb) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var showExpense by remember { mutableStateOf(false) }
    var showPrice by remember { mutableStateOf(false) }
    var showBasketItem by remember { mutableStateOf(false) }
    var checkingLivePrices by remember { mutableStateOf(false) }
    var livePriceMessage by remember { mutableStateOf("Live price checks use Rewalto free credits only. Sign in and configure the backend first.") }
    var livePriceResult by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val expenses = remember(refresh) { db.expenses() }
    val basket = remember(refresh) { db.basketItems() }
    val prices = remember(refresh) { db.prices() }
    val spent = expenses.sumOf { it.amount }
    val planned = 12500.0
    Scaffold(containerColor = Pale, topBar = {
        Column(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("KARNAN KUDUMBAM  •  கர்ணன் குடும்பம்", color = Green, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("Family & Grocery Manager", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF173B2B))
            Text("Madipakkam 600091  •  3 adults  •  Offline-first", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            listOf("Home", "Basket", "Prices", "Reports", "Analysis", "Cloud").forEachIndexed { i, label ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("⌂", "🛒", "₹", "✉", "▥", "☁")[i]) }, label = { Text(label, maxLines = 1) })
            }
        }
    }, floatingActionButton = {
        if (tab == 0 || tab == 4) FloatingActionButton(onClick = { showExpense = true }, containerColor = Green, contentColor = Color.White) { Text("+ Expense", modifier = Modifier.padding(horizontal = 10.dp)) }
        else if (tab == 2) FloatingActionButton(onClick = { showPrice = true }, containerColor = Green, contentColor = Color.White) { Text("+ Price") }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> Dashboard(spent, planned, expenses.take(5), onAdd = { showExpense = true })
                1 -> BasketScreen(
                    basket, prices,
                    onAdd = { showBasketItem = true },
                    onToggle = { id, checked -> db.toggleBasketItem(id, checked); refresh++ },
                    onRemove = { id -> db.removeBasketItem(id); refresh++ },
                    onCompareLive = {
                        if (!checkingLivePrices) scope.launch {
                            checkingLivePrices = true
                            livePriceMessage = "Checking free-credit balance before any product search…"
                            livePriceResult = null
                            try {
                                val user = FirebaseAuth.getInstance().currentUser ?: error("Sign in with Google in the Cloud tab before checking live prices.")
                                val token = user.getIdToken(false).await().token ?: error("Could not obtain a secure sign-in token.")
                                val cloudPrefs = context.getSharedPreferences("karnan_cloud", android.content.Context.MODE_PRIVATE)
                                val backendUrl = cloudPrefs.getString("backend_url", "")?.trim().orEmpty()
                                val pincode = cloudPrefs.getString("postal_code", "600091")?.trim().orEmpty()
                                BackendApi.baseUrl = backendUrl
                                require(pincode.matches(Regex("[1-9][0-9]{5}"))) { "Enter a valid six-digit delivery PIN in the Cloud tab." }
                                val lines = JSONArray()
                                basket.filterNot { it.checked }.forEach { item ->
                                    lines.put(JSONObject().apply {
                                        put("id", item.id.toString())
                                        put("name", item.name)
                                        put("quantity", item.quantity)
                                        put("quantityCount", 1)
                                    })
                                }
                                require(lines.length() > 0) { "Add at least one item to today's basket." }
                                livePriceMessage = "Checking ${lines.length()} items across the latest available provider records…"
                                livePriceResult = BackendApi.compareRewaltoBasket(token, pincode, lines)
                                val freeAfter = livePriceResult?.optInt("freeCreditsAfter", -1) ?: -1
                                livePriceMessage = if (freeAfter >= 0) "Price observations loaded. Free credits remaining: $freeAfter. Confirm current prices in each retailer app; checkout fees are not included." else "Price observations loaded. Confirm timestamps and checkout prices in each retailer app."
                            } catch (e: Exception) {
                                livePriceMessage = e.localizedMessage ?: "Live price check failed. No paid fallback is allowed."
                            } finally { checkingLivePrices = false }
                        }
                    },
                    checkingLive = checkingLivePrices,
                    liveMessage = livePriceMessage,
                    liveResult = livePriceResult
                )
                2 -> PriceScreen(prices, onAdd = { showPrice = true })
                3 -> ReportsScreen(expenses, spent)
                4 -> AnalysisScreen(expenses, spent)
                else -> CloudScreen(db)
            }
        }
    }
    if (showExpense) ExpenseDialog(onDismiss = { showExpense = false }, onSave = { amount, category, note, payment -> db.addExpense(amount, category, note, payment); refresh++; showExpense = false })
    if (showPrice) PriceDialog(onDismiss = { showPrice = false }, onSave = { name, pack, price, source -> db.addPrice(name, pack, price, source); refresh++; showPrice = false })
    if (showBasketItem) BasketItemDialog(onDismiss = { showBasketItem = false }, onSave = { name, quantity -> db.addBasketItem(name, quantity); refresh++; showBasketItem = false })
}

@Composable private fun Dashboard(spent: Double, planned: Double, recent: List<Expense>, onAdd: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("MONTHLY FAMILY BUDGET", color = Color(0xFFD7F4E1), style = MaterialTheme.typography.labelMedium)
                    Text(money.format(15000), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Spent", money.format(spent), Color.White)
                        Metric("Remaining", money.format(15000-spent), Color.White)
                        Metric("Plan", money.format(planned), Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { (spent / 15000.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFFB9F6CA), trackColor = Color(0xFF4C8D6D))
                    Text("${(spent / 15000.0 * 100).toInt()}% of monthly budget used", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SmallStat("Daily guide", "₹417", modifier = Modifier.weight(1f)); SmallStat("Weekly guide", "₹2,885", modifier = Modifier.weight(1f)); SmallStat("Family", "3 adults", modifier = Modifier.weight(1f)) } }
        item { SectionTitle("Today's grocery routine", "7:00 AM daily reminder") }
        item { Text("Suggested rotation: idli / dosa breakfast • rice, sambar and poriyal lunch • dosa or chapati dinner. Buy fresh vegetables in small batches.", color = Color(0xFF4E6257), style = MaterialTheme.typography.bodyMedium) }
        item { SectionTitle("Recent expenses", "This month") }
        if (recent.isEmpty()) item { EmptyCard("No expenses recorded yet", "Tap + Expense to record your first purchase.") }
        items(recent) { ExpenseRow(it) }
        item { Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) { Text("Record an expense") } }
    }
}

@Composable private fun ShoppingScreen(items: List<GroceryItem>, onToggle: (Long, Boolean) -> Unit) {
    Column {
        SectionTitle("Daily shopping list", "For 3 adults")
        Text("Tick items as you buy them. Quantities are editable in a future release; adjust this starter list to your meals.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id, it) }, colors = CheckboxDefaults.colors(checkedColor = Green))
                    Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.category, color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                    Text(item.quantity, color = Green, fontWeight = FontWeight.Bold)
                }
            } }
        }
    }
}

@Composable private fun PriceScreen(prices: List<PriceEntry>, onAdd: () -> Unit) {
    Column {
        SectionTitle("Grocery price notebook", "Zepto • Blinkit • Instamart • Flipkart Minutes • BigBasket")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) {
            Text("Record prices checked in each shopping app. The source label and date are saved with every observation.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("Use the Smart Basket tab to request the latest available Rewalto readings manually. The provider may return older observations; each timestamp is shown and checkout prices must be confirmed in the retailer app.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A5A00))
            Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onAdd) { Text("Add verified price") }
        } }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(prices) { p -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(p.name, fontWeight = FontWeight.SemiBold); Text("${p.pack} • ${p.source} • ${p.date}", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                Text(money.format(p.price), color = Green, fontWeight = FontWeight.Bold)
            }
        } } }
    }
}

@Composable private fun BasketScreen(
    basketItems: List<BasketItem>, prices: List<PriceEntry>, onAdd: () -> Unit,
    onToggle: (Long, Boolean) -> Unit, onRemove: (Long) -> Unit,
    onCompareLive: () -> Unit, checkingLive: Boolean, liveMessage: String, liveResult: JSONObject?
) {
    val providers = listOf("Zepto", "Blinkit", "Swiggy Instamart", "Flipkart Minutes", "BigBasket")
    val active = basketItems.filterNot { it.checked }
    val comparison = providers.map { provider ->
        val matched = active.mapNotNull { item -> prices.filter { it.name.equals(item.name, ignoreCase = true) && it.pack.equals(item.quantity, ignoreCase = true) && it.source.contains(provider, ignoreCase = true) }.maxByOrNull { it.date } }
        Triple(provider, matched.sumOf { it.price }, matched.size)
    }
    val complete = comparison.filter { it.third == active.size && active.isNotEmpty() }.minByOrNull { it.second }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("One-app smart basket", "All items must be available from the same app") }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EA))) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Single-order rule", color = Green, fontWeight = FontWeight.Bold)
            Text("Karnan Kudumbam will never split today's basket across multiple apps. An app is eligible only when every unchecked item has a matching price observation.", color = Color(0xFF355444), style = MaterialTheme.typography.bodySmall)
        } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Today's items (${active.size})", fontWeight = FontWeight.Bold); TextButton(onClick = onAdd) { Text("+ Add item", color = Green) } } }
        if (basketItems.isEmpty()) item { EmptyCard("Your basket is empty", "Add the items you want to buy today.") }
        items(basketItems, key = { it.id }) { item -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id, it) }, colors = CheckboxDefaults.colors(checkedColor = Green))
                Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.quantity, color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onRemove(item.id) }) { Text("Remove", color = Color(0xFF9B3D36)) }
            }
        } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check latest available prices", fontWeight = FontWeight.Bold, color = Green)
                    Text("Manual refresh only. Rewalto free API credits are used; the app blocks the request if the free allowance cannot cover its worst-case cost. No paid fallback.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onCompareLive, enabled = !checkingLive && active.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                        if (checkingLive) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                        Text(if (checkingLive) "Checking prices…" else "Check Live Prices")
                    }
                    if (liveMessage.isNotBlank()) Text(liveMessage, color = Color(0xFF53665B), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        liveResult?.let { result ->
            val provisional = result.optJSONObject("recommendation")
            if (provisional != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3EA))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Lowest complete PIN-specific product subtotal", color = Green, fontWeight = FontWeight.Bold)
                        Text(provisional.optString("providerName"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(money.format(provisional.optDouble("productSubtotal")), style = MaterialTheme.typography.headlineMedium, color = Green, fontWeight = FontWeight.Bold)
                        Text("Provisional only. Delivery, handling, platform fees, coupons and final checkout total are not included.", color = Color(0xFF53665B), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle("Provider results", "Latest stored readings · not checkout quotes") }
            val providerRows = result.optJSONArray("providers")
            if (providerRows != null) {
                items(providerRows.length()) { index ->
                    val row = providerRows.optJSONObject(index) ?: JSONObject()
                    val completeLive = row.optBoolean("complete")
                    Card(colors = CardDefaults.cardColors(containerColor = if (completeLive) Color(0xFFE7F3EA) else Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.optString("providerName"), fontWeight = FontWeight.Bold)
                                Text(if (completeLive) "Complete items" else "${row.optInt("matchedItems")}/${row.optInt("requiredItems")} matched", color = if (completeLive) Green else Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(if (completeLive) "Product subtotal: ${money.format(row.optDouble("productSubtotal"))}" else "Missing: ${row.optJSONArray("missingItems")?.let { arr -> (0 until arr.length()).joinToString() { arr.optString(it) } } ?: "items"}", style = MaterialTheme.typography.bodySmall)
                            if (row.optString("locationCaveat").isNotBlank()) Text(row.optString("locationCaveat"), color = Color(0xFF94611D), style = MaterialTheme.typography.labelSmall)
                            Text("Checkout fees and final payable amount are not supplied by this data source.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            val observationRows = result.optJSONArray("observations")
            if (observationRows != null && observationRows.length() > 0) {
                item { SectionTitle("Price observations", "Source timestamps and pack matching") }
                items(observationRows.length()) { index ->
                    val observation = observationRows.optJSONObject(index) ?: JSONObject()
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(observation.optString("providerName"), fontWeight = FontWeight.SemiBold)
                                Text(if (observation.has("price") && !observation.isNull("price")) money.format(observation.optDouble("price")) else "Price unavailable", color = Green, fontWeight = FontWeight.Bold)
                            }
                            Text(observation.optString("title") + if (observation.optString("packSize").isNotBlank()) " · ${observation.optString("packSize")}" else "", style = MaterialTheme.typography.bodySmall)
                            Text("Stock: ${observation.optString("stock")} · Read: ${observation.optString("checkedAt", "time not supplied")}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            if (!observation.optBoolean("locationSpecific", false)) Text("Not verified for this PIN", color = Color(0xFF94611D), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Text("Source: Rewalto public-page observations, not retailer-owned APIs. Readings may be older than now; confirm prices and stock in the retailer app before ordering. Checkout fees are unavailable from this data source. BigBasket national readings are not PIN-specific and are excluded from local complete-basket eligibility.", color = Color(0xFF6A5B38), style = MaterialTheme.typography.bodySmall) }
        }
        item { SectionTitle("Compare complete baskets", "Latest manually recorded price per app") }
        items(comparison) { (provider, total, count) ->
            val isBest = complete?.first == provider
            Card(colors = CardDefaults.cardColors(containerColor = if (isBest) Color(0xFFE7F3EA) else Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(provider, fontWeight = FontWeight.Bold); Text("$count / ${active.size} items priced", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                    Column(horizontalAlignment = Alignment.End) { Text(if (count == active.size && active.isNotEmpty()) money.format(total) else "Incomplete", color = if (isBest) Green else Color.DarkGray, fontWeight = FontWeight.Bold); if (isBest) Text("Best complete basket", color = Green, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        item { Text(if (complete != null) "Recommendation: ${complete.first} has the lowest recorded product subtotal for the complete basket. Delivery, handling and checkout fees are not yet included." else "No app has recorded prices for every basket item yet. Add verified prices for the missing products. No split-order recommendation will be made.", color = Color(0xFF6A5B38), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun BasketItemDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("1 pack") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add basket item") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Product name") }, singleLine = true)
        OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity / pack size") }, singleLine = true)
    } }, confirmButton = { TextButton(onClick = { if (name.isNotBlank() && quantity.isNotBlank()) onSave(name, quantity) }) { Text("Add", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun ReportsScreen(expenses: List<Expense>, spent: Double) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("karnan_report_settings", android.content.Context.MODE_PRIVATE) }
    var email by remember { mutableStateOf(prefs.getString("email", "aksheysun@gmail.com") ?: "aksheysun@gmail.com") }
    var time by remember { mutableStateOf(prefs.getString("time", "20:00") ?: "20:00") }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Daily email reports", "Detailed family spending statement") }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Report recipient email") }, singleLine = true)
            OutlinedTextField(time, { time = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Daily report time (24-hour IST)") }, singleLine = true)
            Button(onClick = { prefs.edit().putString("email", email.trim()).putString("time", time.trim()).apply(); saved = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green), enabled = email.contains("@") && time.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) { Text("Save report preferences") }
            if (saved) Text("Preferences saved on this device.", color = Green, style = MaterialTheme.typography.bodySmall)
        } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E3))) { Column(Modifier.padding(14.dp)) {
            Text("Automatic email is not active yet", fontWeight = FontWeight.Bold, color = Color(0xFF80551A))
            Text("Secure server-side email delivery still needs an email provider and backend credentials. No email will be sent from this screen. Secrets must never be stored in the Android app.", color = Color(0xFF765F3D), style = MaterialTheme.typography.bodySmall)
        } } }
        item { SectionTitle("Report preview", "Current month's recorded expenses") }
        item { SmallStat("Total recorded", money.format(spent), "Monthly budget ₹15,000") }
        item { SmallStat("Remaining budget", money.format(15000 - spent), "Based on saved expenses") }
        item { Text("Generate a PDF statement and share it through your email app. Automatic scheduled email delivery still requires a secure backend and email-provider setup.", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        item { Button(onClick = {
            try {
                val pdf = ReportPdf.create(context, expenses, spent)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email.trim()))
                    putExtra(Intent.EXTRA_SUBJECT, "Karnan Kudumbam - Daily Expense Statement")
                    putExtra(Intent.EXTRA_TEXT, "Please find the household expense statement attached.")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share expense PDF"))
            } catch (error: Exception) {
                Toast.makeText(context, "Could not create PDF: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Generate PDF & share by email") } }
    }
}

@Composable private fun AnalysisScreen(expenses: List<Expense>, spent: Double) {
    val groups = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Monthly analysis", LocalDate.now().toString().take(7)) }
        item { SmallStat("Total recorded", money.format(spent), "Budget ₹15,000") }
        item { SmallStat("Available", money.format(15000-spent), "Based on recorded expenses") }
        item { SectionTitle("Category breakdown", "Current month") }
        if (groups.isEmpty()) item { EmptyCard("Nothing to analyse yet", "Add expenses to see category totals.") }
        items(groups) { (category, total) -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, fontWeight = FontWeight.SemiBold); Text(money.format(total), color = Green, fontWeight = FontWeight.Bold) }
            LinearProgressIndicator(progress = { (total / 15000.0).coerceIn(0.0,1.0).toFloat() }, modifier = Modifier.fillMaxWidth().padding(top=8.dp), color = Green)
        } } }
        item { Text("Tip: Record every household purchase, including small cash expenses, for accurate results.", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ExpenseDialog(onDismiss: () -> Unit, onSave: (Double, String, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Vegetables") }; var note by remember { mutableStateOf("") }; var payment by remember { mutableStateOf("UPI") }
    val categories = listOf("Groceries", "Vegetables", "Milk & dairy", "Food", "Transport", "Bills", "Medical", "Household", "Other")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add expense") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        Text("Category", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { categories.take(3).forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { categories.drop(3).take(3).forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) } }
        OutlinedTextField(note, { note = it }, label = { Text("Description") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("UPI", "Cash", "Card").forEach { FilterChip(selected = payment == it, onClick = { payment = it }, label = { Text(it) }) } }
    } }, confirmButton = { TextButton(onClick = { val v = amount.toDoubleOrNull(); if (v != null && v > 0) onSave(v, category, note, payment) }) { Text("Save", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun PriceDialog(onDismiss: () -> Unit, onSave: (String, String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var pack by remember { mutableStateOf("500 g") }; var price by remember { mutableStateOf("") }; var source by remember { mutableStateOf("Zepto (manual verified)") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Record product price") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Product") }, singleLine = true)
        OutlinedTextField(pack, { pack = it }, label = { Text("Pack size") }, singleLine = true)
        OutlinedTextField(price, { price = it }, label = { Text("Price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(source, { source = it }, label = { Text("Source / verification") }, singleLine = true)
    } }, confirmButton = { TextButton(onClick = { val v = price.toDoubleOrNull(); if (name.isNotBlank() && v != null && v > 0) onSave(name, pack, v, source) }) { Text("Save price", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun Metric(label: String, value: String, color: Color) { Column { Text(label, color = color.copy(alpha=.8f), style = MaterialTheme.typography.labelSmall); Text(value, color = color, fontWeight = FontWeight.Bold) } }
@Composable private fun SmallStat(label: String, value: String, sub: String = "", modifier: Modifier = Modifier) { Card(modifier.widthIn(min=95.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(12.dp)) { Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall); Text(value, color = Green, fontWeight = FontWeight.Bold); if (sub.isNotBlank()) Text(sub, color = Color.Gray, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun SectionTitle(title: String, subtitle: String) { Column(Modifier.padding(vertical=6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF173B2B)); Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun EmptyCard(title: String, sub: String) { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Text(sub, color = Color.Gray, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun ExpenseRow(e: Expense) { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(e.category, fontWeight = FontWeight.SemiBold); Text("${e.note.ifBlank { "Household expense" }} • ${e.date} • ${e.payment}", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }; Text(money.format(e.amount), color = Green, fontWeight = FontWeight.Bold) } } }
