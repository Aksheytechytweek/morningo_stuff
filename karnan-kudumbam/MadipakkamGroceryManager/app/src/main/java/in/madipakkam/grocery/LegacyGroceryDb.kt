package in.madipakkam.grocery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

 data class Expense(val id: Long, val date: String, val amount: Double, val category: String, val note: String, val payment: String)
 data class GroceryItem(val id: Long, val name: String, val quantity: String, val category: String, val checked: Boolean)
 data class PriceEntry(val id: Long, val name: String, val pack: String, val price: Double, val source: String, val date: String)
data class BasketItem(val id: Long, val name: String, val quantity: String, val checked: Boolean)

class LegacyGroceryDb(context: Context) : SQLiteOpenHelper(context, "madipakkam_grocery.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, amount REAL NOT NULL, category TEXT NOT NULL, note TEXT, payment TEXT)")
        db.execSQL("CREATE TABLE groceries(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, category TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE prices(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, pack TEXT, price REAL NOT NULL, source TEXT, date TEXT NOT NULL)")
        val defaults = listOf(
            Triple("Tomato", "500 g", "Vegetables"), Triple("Onion", "500 g", "Vegetables"),
            Triple("Carrot", "250 g", "Vegetables"), Triple("Beans", "250 g", "Vegetables"),
            Triple("Cabbage", "1 small", "Vegetables"), Triple("Greens", "1 bunch", "Vegetables"),
            Triple("Cucumber", "500 g", "Vegetables"), Triple("Banana", "6 pcs", "Fruits")
        )
        defaults.forEach { (name, qty, cat) -> db.insert("groceries", null, ContentValues().apply { put("name", name); put("quantity", qty); put("category", cat); put("checked", 0) }) }
        db.execSQL("CREATE TABLE basket_items(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0)")
        listOf(Triple("Tomato", "500 g", 0), Triple("Onion", "500 g", 0), Triple("Milk", "1 L", 0), Triple("Curd", "500 g", 0), Triple("Rice", "5 kg", 0), Triple("Toor dal", "1 kg", 0), Triple("Potato", "500 g", 0), Triple("Banana", "6 pcs", 0), Triple("Eggs", "6 pcs", 0), Triple("Cooking oil", "1 L", 0)).forEach { (name, qty, checked) ->
            db.insert("basket_items", null, ContentValues().apply { put("name", name); put("quantity", qty); put("checked", checked) })
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS basket_items(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0)")
            listOf("Tomato" to "500 g", "Onion" to "500 g", "Milk" to "1 L", "Curd" to "500 g", "Rice" to "5 kg", "Toor dal" to "1 kg", "Potato" to "500 g", "Banana" to "6 pcs", "Eggs" to "6 pcs", "Cooking oil" to "1 L").forEach { (name, qty) ->
                db.insert("basket_items", null, ContentValues().apply { put("name", name); put("quantity", qty); put("checked", 0) })
            }
        }
    }

    fun addExpense(amount: Double, category: String, note: String, payment: String) {
        writableDatabase.insert("expenses", null, ContentValues().apply {
            put("date", LocalDate.now().toString()); put("amount", amount); put("category", category); put("note", note); put("payment", payment)
        })
    }
    fun expenses(month: String = LocalDate.now().toString().take(7)): List<Expense> {
        val out = mutableListOf<Expense>()
        readableDatabase.rawQuery("SELECT id,date,amount,category,note,payment FROM expenses WHERE date LIKE ? ORDER BY date DESC,id DESC", arrayOf("$month%")).use { c ->
            while (c.moveToNext()) out += Expense(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3), c.getString(4) ?: "", c.getString(5) ?: "Cash")
        }; return out
    }
    fun groceries(): List<GroceryItem> {
        val out = mutableListOf<GroceryItem>()
        readableDatabase.rawQuery("SELECT id,name,quantity,category,checked FROM groceries ORDER BY category,name", null).use { c ->
            while (c.moveToNext()) out += GroceryItem(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1)
        }; return out
    }
    fun toggleGrocery(id: Long, checked: Boolean) { writableDatabase.execSQL("UPDATE groceries SET checked=? WHERE id=?", arrayOf(if (checked) 1 else 0, id)) }
    fun addPrice(name: String, pack: String, price: Double, source: String) {
        writableDatabase.insert("prices", null, ContentValues().apply { put("name", name); put("pack", pack); put("price", price); put("source", source); put("date", LocalDate.now().toString()) })
    }
    fun prices(): List<PriceEntry> {
        val out = mutableListOf<PriceEntry>()
        readableDatabase.rawQuery("SELECT id,name,pack,price,source,date FROM prices ORDER BY id DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) out += PriceEntry(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getDouble(3), c.getString(4) ?: "Manual", c.getString(5))
        }; return out
    }
    fun basketItems(): List<BasketItem> {
        val out = mutableListOf<BasketItem>()
        readableDatabase.rawQuery("SELECT id,name,quantity,checked FROM basket_items ORDER BY id", null).use { c ->
            while (c.moveToNext()) out += BasketItem(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1)
        }
        return out
    }
    fun addBasketItem(name: String, quantity: String) {
        writableDatabase.insert("basket_items", null, ContentValues().apply { put("name", name.trim()); put("quantity", quantity.trim()); put("checked", 0) })
    }
    fun toggleBasketItem(id: Long, checked: Boolean) {
        writableDatabase.execSQL("UPDATE basket_items SET checked=? WHERE id=?", arrayOf(if (checked) 1 else 0, id))
    }
    fun removeBasketItem(id: Long) { writableDatabase.delete("basket_items", "id=?", arrayOf(id.toString())) }
    fun addExpenseForDate(date: String, amount: Double, category: String, note: String, payment: String) {
        writableDatabase.insert("expenses", null, ContentValues().apply { put("date", date); put("amount", amount); put("category", category); put("note", note); put("payment", payment) })
    }

}
