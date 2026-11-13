package in.madipakkam.grocery

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Entity(tableName = "expenses") data class ExpenseRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val amount: Double, val category: String, val note: String, val payment: String, val syncId: String = "")
@Entity(tableName = "groceries") data class GroceryRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val quantity: String, val category: String, val checked: Boolean = false)
@Entity(tableName = "prices") data class PriceRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val pack: String, val price: Double, val source: String, val date: String)
@Entity(tableName = "basket_items") data class BasketRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val quantity: String, val checked: Boolean = false)

@Dao interface HouseholdDao {
    @Query("SELECT * FROM expenses WHERE date LIKE :month ORDER BY date DESC,id DESC") suspend fun expenses(month: String): List<ExpenseRecord>
    @Insert suspend fun insertExpense(record: ExpenseRecord): Long
    @Query("SELECT * FROM groceries ORDER BY category,name") suspend fun groceries(): List<GroceryRecord>
    @Insert suspend fun insertGrocery(record: GroceryRecord)
    @Query("UPDATE groceries SET checked=:checked WHERE id=:id") suspend fun toggleGrocery(id: Long, checked: Boolean)
    @Query("SELECT * FROM prices ORDER BY id DESC LIMIT 100") suspend fun prices(): List<PriceRecord>
    @Insert suspend fun insertPrice(record: PriceRecord)
    @Query("SELECT * FROM basket_items ORDER BY id") suspend fun basket(): List<BasketRecord>
    @Insert suspend fun insertBasket(record: BasketRecord)
    @Query("UPDATE basket_items SET checked=:checked WHERE id=:id") suspend fun toggleBasket(id: Long, checked: Boolean)
    @Query("DELETE FROM basket_items WHERE id=:id") suspend fun deleteBasket(id: Long)
    @Query("SELECT COUNT(*) FROM basket_items") suspend fun basketCount(): Int
    @Query("SELECT COUNT(*) FROM groceries") suspend fun groceryCount(): Int
    @Query("SELECT COUNT(*) FROM expenses") suspend fun expenseCount(): Int
    @Query("SELECT COUNT(*) FROM prices") suspend fun priceCount(): Int
}
@Database(entities = [ExpenseRecord::class, GroceryRecord::class, PriceRecord::class, BasketRecord::class], version = 1, exportSchema = false)
abstract class HouseholdRoomDatabase: RoomDatabase() { abstract fun dao(): HouseholdDao }

/** Compatibility facade. Queries run on IO, never Room's main thread. Legacy SQLite data
 * is copied once on upgrade, preserving the old database as a recovery backup. */
class GroceryDb(context: Context) {
    private val appContext = context.applicationContext
    private val room = Room.databaseBuilder(appContext, HouseholdRoomDatabase::class.java, "karnan_household_room.db").build()
    private val dao = room.dao()
    private fun <T> io(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }
    init { io { importLegacy() } }
    private suspend fun importLegacy() {
        val prefs = appContext.getSharedPreferences("karnan_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sqlite_imported", false)) return
        if (appContext.getDatabasePath("madipakkam_grocery.db").exists()) {
            val old = LegacyGroceryDb(appContext)
            try {
                if (dao.expenseCount() == 0) old.expenses("%") .forEach { dao.insertExpense(ExpenseRecord(date=it.date,amount=it.amount,category=it.category,note=it.note,payment=it.payment)) }
                if (dao.groceryCount() == 0) old.groceries().forEach { dao.insertGrocery(GroceryRecord(name=it.name,quantity=it.quantity,category=it.category,checked=it.checked)) }
                if (dao.priceCount() == 0) old.prices().forEach { dao.insertPrice(PriceRecord(name=it.name,pack=it.pack,price=it.price,source=it.source,date=it.date)) }
                if (dao.basketCount() == 0) old.basketItems().forEach { dao.insertBasket(BasketRecord(name=it.name,quantity=it.quantity,checked=it.checked)) }
            } finally { old.close() }
        }
        if (dao.basketCount() == 0) listOf("Tomato" to "500 g", "Onion" to "500 g", "Milk" to "1 L", "Curd" to "500 g", "Rice" to "5 kg", "Toor dal" to "1 kg", "Potato" to "500 g", "Banana" to "6 pcs", "Eggs" to "6 pcs", "Cooking oil" to "1 L").forEach { dao.insertBasket(BasketRecord(name=it.first,quantity=it.second)) }
        if (dao.groceryCount() == 0) listOf("Tomato" to "500 g", "Onion" to "500 g", "Carrot" to "250 g", "Beans" to "250 g", "Greens" to "1 bunch", "Banana" to "6 pcs").forEach { dao.insertGrocery(GroceryRecord(name=it.first,quantity=it.second,category="Vegetables")) }
        prefs.edit().putBoolean("sqlite_imported", true).apply()
    }
    fun expenses(month: String = LocalDate.now().toString().take(7)): List<Expense> = io { dao.expenses("$month%").map { Expense(it.id,it.date,it.amount,it.category,it.note,it.payment) } }
    fun addExpense(amount: Double,category: String,note: String,payment: String) = addExpenseForDate(LocalDate.now().toString(),amount,category,note,payment)
    fun addExpenseForDate(date: String,amount: Double,category: String,note: String,payment: String) { io { dao.insertExpense(ExpenseRecord(date=date,amount=amount,category=category,note=note,payment=payment)) } }
    fun groceries(): List<GroceryItem> = io { dao.groceries().map { GroceryItem(it.id,it.name,it.quantity,it.category,it.checked) } }
    fun toggleGrocery(id: Long, checked: Boolean) { io { dao.toggleGrocery(id,checked) } }
    fun prices(): List<PriceEntry> = io { dao.prices().map { PriceEntry(it.id,it.name,it.pack,it.price,it.source,it.date) } }
    fun addPrice(name: String,pack: String,price: Double,source: String) { io { dao.insertPrice(PriceRecord(name=name,pack=pack,price=price,source=source,date=LocalDate.now().toString())) } }
    fun basketItems(): List<BasketItem> = io { dao.basket().map { BasketItem(it.id,it.name,it.quantity,it.checked) } }
    fun addBasketItem(name: String,quantity: String) { io { dao.insertBasket(BasketRecord(name=name.trim(),quantity=quantity.trim())) } }
    fun toggleBasketItem(id: Long,checked: Boolean) { io { dao.toggleBasket(id,checked) } }
    fun removeBasketItem(id: Long) { io { dao.deleteBasket(id) } }
}
