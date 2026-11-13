package in.madipakkam.grocery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportPdf {
    fun create(context: Context, expenses: List<Expense>, monthlyTotal: Double): File {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "Karnan-Kudumbam-${LocalDate.now()}.pdf")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(22, 115, 74); textSize = 21f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 10f }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; textSize = 9f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
        val currency = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
        val monthLabel = YearMonth.now().toString()
        val rows = expenses.filter { it.date == LocalDate.now().toString() }.sortedBy { it.id }
        val dailyTotal = rows.sumOf { it.amount }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 54f
        fun header(target: Canvas, pageNo: Int) {
            target.drawText("KARNAN KUDUMBAM  •  கர்ணன் குடும்பம்", margin, 42f, titlePaint)
            target.drawText("Daily household expense statement  |  $dateLabel", margin, 65f, mutedPaint)
            target.drawLine(margin, 78f, pageWidth - margin, 78f, linePaint)
            target.drawText("Date", margin, 98f, headingPaint)
            target.drawText("Category / description", 112f, 98f, headingPaint)
            target.drawText("Payment", 410f, 98f, headingPaint)
            target.drawText("Amount", 490f, 98f, headingPaint)
            target.drawLine(margin, 106f, pageWidth - margin, 106f, linePaint)
            target.drawText("Page $pageNo", pageWidth - 80f, pageHeight - 24f, mutedPaint)
        }
        header(canvas, pageNumber)
        y = 126f
        rows.forEach { expense ->
            if (y > pageHeight - 80f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                header(canvas, pageNumber)
                y = 126f
            }
            canvas.drawText(expense.date, margin, y, bodyPaint)
            val description = (expense.category + if (expense.note.isNotBlank()) " / ${expense.note}" else "").take(46)
            canvas.drawText(description, 112f, y, bodyPaint)
            canvas.drawText(expense.payment.take(14), 410f, y, bodyPaint)
            val amount = currency.format(expense.amount)
            canvas.drawText(amount, 490f, y, bodyPaint)
            canvas.drawLine(margin, y + 7f, pageWidth - margin, y + 7f, linePaint)
            y += 24f
        }
        if (y > pageHeight - 130f) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            header(canvas, pageNumber)
            y = 126f
        }
        y += 12f
        canvas.drawText("Today's total: ${currency.format(dailyTotal)}", margin, y, headingPaint)
        y += 24f
        canvas.drawText("Monthly summary ($monthLabel)", margin, y, headingPaint)
        y += 22f
        canvas.drawText("Recorded expenses: ${currency.format(monthlyTotal)}", margin, y, bodyPaint)
        y += 18f
        canvas.drawText("Household monthly budget: ${currency.format(15000.0)}", margin, y, bodyPaint)
        y += 18f
        canvas.drawText("Remaining budget: ${currency.format(15000.0 - monthlyTotal)}", margin, y, bodyPaint)
        y += 24f
        canvas.drawText("This statement reflects expenses recorded in Karnan Kudumbam on this device.", margin, y, mutedPaint)
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
