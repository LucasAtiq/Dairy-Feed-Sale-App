package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// --- Domain Summary Models ---

data class LedgerEntryRow(
    val id: String,
    val date: String,
    val referenceCode: String,
    val description: String,
    val debit: Double,
    val credit: Double,
    val runningBalance: Double,
    val entryType: String, // OPENING, SALE, PAYMENT
    val sourceId: Long = 0
)

data class DealerFinancialSummary(
    val dealer: DealerEntity,
    val totalOrders: Int,
    val totalBags: Int,
    val totalKg: Double,
    val totalMt: Double,
    val totalSales: Double,
    val totalPayments: Double,
    val outstandingBalance: Double,
    val overdueAmount: Double,
    val lastOrderDate: String,
    val lastPaymentDate: String,
    val productsPurchasedSummary: String,
    val ledgerEntries: List<LedgerEntryRow>
)

data class ProductStockStatus(
    val product: ProductEntity,
    val openingBags: Int,
    val stockInBags: Int,
    val stockOutBags: Int,
    val currentBags: Int,
    val currentKg: Double,
    val currentMt: Double,
    val stockValueAtCost: Double,
    val stockValueAtSale: Double,
    val isLowStock: Boolean
)

data class ConsolidatedOrderLine(
    val productId: Long,
    val productCode: String,
    val productName: String,
    val bagSizeLabel: String,
    val bagWeightKg: Double,
    val totalBags: Int,
    val totalKg: Double,
    val totalMt: Double,
    val totalValue: Double,
    val orderCount: Int
)

data class ConsolidatedSummary(
    val lines: List<ConsolidatedOrderLine>,
    val totalBags: Int,
    val totalKg: Double,
    val totalMt: Double,
    val totalValue: Double,
    val matchingOrdersCount: Int
)

data class DailyMilkPoint(
    val date: String,
    val avgMilkLiters: Double,
    val animalCountRecorded: Int
)

data class TrialAnalytics(
    val startingAvgMilk: Double,
    val currentAvgMilk: Double,
    val finalAvgMilk: Double,
    val changeLiters: Double,
    val percentageChange: Double,
    val durationDays: Int,
    val distinctAnimals: List<String>,
    val dailyChartPoints: List<DailyMilkPoint>
)

data class SmartReminderItem(
    val id: String,
    val category: String, // PAYMENT_DUE, FOLLOW_UP, TRIAL_FOLLOW_UP, PENDING_DELIVERY, LOW_STOCK, TARGET
    val title: String,
    val subtitle: String,
    val severity: String, // HIGH, MEDIUM, INFO
    val dateLabel: String,
    val targetModule: String
)

object BusinessCalculations {

    fun formatCurrency(amount: Double, currencySymbol: String = "Rs."): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "$currencySymbol ${formatter.format(amount.roundToInt())}"
    }

    fun formatDecimal(value: Double, decimals: Int = 2): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    fun calculateTotalKg(bags: Int, bagWeightKg: Double): Double = bags * bagWeightKg

    fun calculateTotalMt(totalKg: Double): Double = totalKg / 1000.0

    fun computeDealerSummary(
        dealer: DealerEntity,
        allOrders: List<OrderWithItems>,
        allPayments: List<PaymentEntity>
    ): DealerFinancialSummary {
        val activeOrders = allOrders.filter {
            it.order.dealerId == dealer.id &&
                it.order.status != "Cancelled" &&
                it.order.status != "Draft"
        }
        val dealerPayments = allPayments.filter { it.dealerId == dealer.id }

        val totalOrders = activeOrders.size
        val totalBags = activeOrders.sumOf { it.order.totalBags }
        val totalKg = activeOrders.sumOf { it.order.totalKg }
        val totalMt = activeOrders.sumOf { it.order.totalMt }
        val totalSales = activeOrders.sumOf { it.order.netAmount }
        val totalPayments = dealerPayments.sumOf { it.amount }

        // Build chronological ledger events
        data class RawLedgerEvent(
            val date: String,
            val timestamp: Long,
            val ref: String,
            val desc: String,
            val debit: Double,
            val credit: Double,
            val type: String,
            val id: Long
        )

        val events = mutableListOf<RawLedgerEvent>()
        activeOrders.forEach { owi ->
            val o = owi.order
            val itemsSummary = owi.items.joinToString(", ") { "${it.quantityBags}x ${it.productCode} (${it.bagSizeLabel})" }
            events.add(
                RawLedgerEvent(
                    date = o.date,
                    timestamp = o.createdAt,
                    ref = o.invoiceNumber,
                    desc = "Sale ${o.orderNumber}: $itemsSummary",
                    debit = o.netAmount,
                    credit = 0.0,
                    type = "SALE",
                    id = o.id
                )
            )
        }
        dealerPayments.forEach { p ->
            events.add(
                RawLedgerEvent(
                    date = p.date,
                    timestamp = p.createdAt,
                    ref = p.paymentNumber,
                    desc = "Payment (${p.paymentMethod}) ${if (p.referenceNumber.isNotBlank()) "Ref: ${p.referenceNumber}" else ""}".trim(),
                    debit = 0.0,
                    credit = p.amount,
                    type = "PAYMENT",
                    id = p.id
                )
            )
        }

        events.sortWith(compareBy<RawLedgerEvent> { it.date }.thenBy { it.timestamp })

        val ledgerRows = mutableListOf<LedgerEntryRow>()
        var running = dealer.openingBalance
        ledgerRows.add(
            LedgerEntryRow(
                id = "OPEN-${dealer.id}",
                date = dealer.dateAdded,
                referenceCode = "OPEN-BAL",
                description = "Opening Balance",
                debit = if (dealer.openingBalance > 0) dealer.openingBalance else 0.0,
                credit = if (dealer.openingBalance < 0) abs(dealer.openingBalance) else 0.0,
                runningBalance = running,
                entryType = "OPENING",
                sourceId = dealer.id
            )
        )

        events.forEachIndexed { idx, ev ->
            running += (ev.debit - ev.credit)
            ledgerRows.add(
                LedgerEntryRow(
                    id = "${ev.type}-${ev.id}-$idx",
                    date = ev.date,
                    referenceCode = ev.ref,
                    description = ev.desc,
                    debit = ev.debit,
                    credit = ev.credit,
                    runningBalance = running,
                    entryType = ev.type,
                    sourceId = ev.id
                )
            )
        }

        // Products purchased breakdown
        val productBagsMap = mutableMapOf<String, Int>()
        activeOrders.flatMap { it.items }.forEach { item ->
            productBagsMap[item.productCode] = (productBagsMap[item.productCode] ?: 0) + item.quantityBags
        }
        val productsSummary = if (productBagsMap.isEmpty()) {
            "No products purchased yet"
        } else {
            productBagsMap.entries.joinToString(" • ") { "${it.key}: ${it.value} bags" }
        }

        val lastOrderDate = activeOrders.maxByOrNull { it.order.date }?.order?.date ?: "None"
        val lastPaymentDate = dealerPayments.maxByOrNull { it.date }?.date ?: "None"

        // Overdue calculation: any outstanding balance beyond credit limit or orders older than 3 days with unpaid balance
        val threeDaysAgo = SampleDataSeeder.daysOffset(-3)
        val oldOrderSales = activeOrders.filter { it.order.date <= threeDaysAgo }.sumOf { it.order.netAmount } + dealer.openingBalance
        val overdueAmount = (oldOrderSales - totalPayments).coerceAtLeast(0.0).coerceAtMost(running.coerceAtLeast(0.0))

        return DealerFinancialSummary(
            dealer = dealer,
            totalOrders = totalOrders,
            totalBags = totalBags,
            totalKg = totalKg,
            totalMt = totalMt,
            totalSales = totalSales,
            totalPayments = totalPayments,
            outstandingBalance = running,
            overdueAmount = overdueAmount,
            lastOrderDate = lastOrderDate,
            lastPaymentDate = lastPaymentDate,
            productsPurchasedSummary = productsSummary,
            ledgerEntries = ledgerRows
        )
    }

    fun computeProductStockStatuses(
        products: List<ProductEntity>,
        movements: List<StockMovementEntity>
    ): List<ProductStockStatus> {
        return products.filter { !it.isArchived }.map { product ->
            val prodMovements = movements.filter { it.productId == product.id }
            val extraOpening = prodMovements.filter { it.type == "OPENING" }.sumOf { it.quantityBags }
            val openingBags = product.openingStockBags + extraOpening
            val stockInBags = prodMovements.filter { it.type == "STOCK_IN" }.sumOf { it.quantityBags }
            val stockOutBags = prodMovements.filter { it.type == "STOCK_OUT" }.sumOf { it.quantityBags }
            val adjustmentBags = prodMovements.filter { it.type == "ADJUSTMENT" }.sumOf { it.quantityBags }

            // Exact formula: Opening Stock + Stock In - Stock Out (+ Adjustments) = Current Stock
            val currentBags = openingBags + stockInBags - stockOutBags + adjustmentBags
            val currentKg = currentBags * product.defaultBagWeightKg
            val currentMt = currentKg / 1000.0
            val isLow = currentBags <= product.minStockBags

            ProductStockStatus(
                product = product,
                openingBags = openingBags,
                stockInBags = stockInBags,
                stockOutBags = stockOutBags,
                currentBags = currentBags,
                currentKg = currentKg,
                currentMt = currentMt,
                stockValueAtCost = currentBags.coerceAtLeast(0) * product.purchasePrice,
                stockValueAtSale = currentBags.coerceAtLeast(0) * product.salePrice,
                isLowStock = isLow
            )
        }
    }

    fun computeConsolidatedOrders(
        orders: List<OrderWithItems>,
        startDate: String = "",
        endDate: String = "",
        areaFilter: String = "All",
        statusFilter: String = "All"
    ): ConsolidatedSummary {
        val filteredOrders = orders.filter { owi ->
            val o = owi.order
            val validStatus = if (statusFilter == "All") {
                o.status != "Cancelled"
            } else {
                o.status.equals(statusFilter, ignoreCase = true)
            }
            val validStart = startDate.isBlank() || o.date >= startDate
            val validEnd = endDate.isBlank() || o.date <= endDate
            val validArea = areaFilter == "All" || o.area.equals(areaFilter, ignoreCase = true)
            validStatus && validStart && validEnd && validArea
        }

        data class GroupKey(val productId: Long, val productCode: String, val productName: String, val bagSizeLabel: String, val bagWeightKg: Double)

        val grouped = mutableMapOf<GroupKey, MutableList<Pair<Long, OrderItemEntity>>>()
        filteredOrders.forEach { owi ->
            owi.items.forEach { item ->
                val key = GroupKey(item.productId, item.productCode, item.productName, item.bagSizeLabel, item.bagWeightKg)
                grouped.getOrPut(key) { mutableListOf() }.add(owi.order.id to item)
            }
        }

        val lines = grouped.entries.map { (key, pairs) ->
            val bags = pairs.sumOf { it.second.quantityBags }
            val kg = bags * key.bagWeightKg
            val mt = kg / 1000.0
            val value = pairs.sumOf { it.second.netAmount }
            val distinctOrders = pairs.map { it.first }.distinct().size
            ConsolidatedOrderLine(
                productId = key.productId,
                productCode = key.productCode,
                productName = key.productName,
                bagSizeLabel = key.bagSizeLabel,
                bagWeightKg = key.bagWeightKg,
                totalBags = bags,
                totalKg = kg,
                totalMt = mt,
                totalValue = value,
                orderCount = distinctOrders
            )
        }.sortedWith(compareBy<ConsolidatedOrderLine> { it.productCode }.thenBy { it.bagWeightKg })

        val totalBags = lines.sumOf { it.totalBags }
        val totalKg = lines.sumOf { it.totalKg }
        val totalMt = totalKg / 1000.0
        val totalValue = lines.sumOf { it.totalValue }

        return ConsolidatedSummary(
            lines = lines,
            totalBags = totalBags,
            totalKg = totalKg,
            totalMt = totalMt,
            totalValue = totalValue,
            matchingOrdersCount = filteredOrders.size
        )
    }

    fun computeTrialAnalytics(trialWithMilk: TrialWithMilkRecords): TrialAnalytics {
        val records = trialWithMilk.milkRecords.sortedBy { it.recordDate }
        if (records.isEmpty()) {
            return TrialAnalytics(
                startingAvgMilk = 0.0,
                currentAvgMilk = 0.0,
                finalAvgMilk = 0.0,
                changeLiters = 0.0,
                percentageChange = 0.0,
                durationDays = trialWithMilk.trial.transitionDays,
                distinctAnimals = emptyList(),
                dailyChartPoints = emptyList()
            )
        }

        val byDate = records.groupBy { it.recordDate }.toSortedMap()
        val dailyPoints = byDate.entries.map { (date, list) ->
            DailyMilkPoint(
                date = date,
                avgMilkLiters = list.map { it.dailyMilkLiters }.average(),
                animalCountRecorded = list.size
            )
        }

        val startingAvg = records.map { it.startingMilkLiters }.average()
        val latestAvg = dailyPoints.lastOrNull()?.avgMilkLiters ?: startingAvg
        val change = latestAvg - startingAvg
        val pct = if (startingAvg > 0.0) (change / startingAvg) * 100.0 else 0.0
        val distinctAnimals = records.map { it.animalIdTag }.distinct()

        // Calculate duration days between first and last date or trial start/end
        val duration = try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = fmt.parse(trialWithMilk.trial.startDate)?.time ?: 0L
            val end = fmt.parse(dailyPoints.last().date)?.time ?: start
            (((end - start) / (1000L * 60 * 60 * 24)).toInt() + 1).coerceAtLeast(1)
        } catch (_: Exception) {
            dailyPoints.size.coerceAtLeast(1)
        }

        return TrialAnalytics(
            startingAvgMilk = startingAvg,
            currentAvgMilk = latestAvg,
            finalAvgMilk = latestAvg,
            changeLiters = change,
            percentageChange = pct,
            durationDays = duration,
            distinctAnimals = distinctAnimals,
            dailyChartPoints = dailyPoints
        )
    }

    fun parseCustomFields(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optString(k, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun serializeCustomFields(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                obj.put(k, v)
            }
        }
        return obj.toString()
    }
}

object DocumentAndExportManager {

    private fun getExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareTextMessage(context: Context, subject: String, body: String, preferWhatsApp: Boolean = false) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (preferWhatsApp) {
                setPackage("com.whatsapp")
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Fallback to generic share sheet if WhatsApp isn't installed
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(fallback, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun dialPhoneNumber(context: Context, phone: String) {
        if (phone.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun openWhatsAppChat(context: Context, phone: String, message: String = "") {
        if (phone.isBlank()) return
        val cleanPhone = phone.replace("[^0-9+]".toRegex(), "")
        try {
            val url = "https://wa.me/${cleanPhone.removePrefix("+")}?text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            shareTextMessage(context, "Message", message, preferWhatsApp = false)
        }
    }

    fun openMapLocation(context: Context, locationQuery: String) {
        if (locationQuery.isBlank()) return
        try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(locationQuery)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun generateInvoicePdfFile(
        context: Context,
        config: BusinessConfigEntity,
        orderWithItems: OrderWithItems
    ): File {
        val order = orderWithItems.order
        val items = orderWithItems.items
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(15, 82, 51)
            textSize = 20f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = AndroidColor.rgb(66, 84, 75)
            textSize = 11f
        }
        val boldPaint = Paint().apply {
            color = AndroidColor.rgb(17, 28, 22)
            textSize = 12f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(17, 28, 22)
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = AndroidColor.rgb(196, 209, 202)
            strokeWidth = 1f
        }
        val headerBgPaint = Paint().apply {
            color = AndroidColor.rgb(210, 245, 225)
        }

        var y = 45f
        canvas.drawText(config.companyName, 40f, y, titlePaint)
        y += 18f
        canvas.drawText("${config.businessTitle} • Dairy Feed Business", 40f, y, subtitlePaint)
        y += 15f
        canvas.drawText("${config.companyAddress} | Tel: ${config.contactPhone}", 40f, y, subtitlePaint)
        y += 15f
        canvas.drawText("Tax / Reg: ${config.taxRegistrationNumber}", 40f, y, subtitlePaint)

        // Invoice Badge on Right
        canvas.drawText("TAX INVOICE", 430f, 45f, titlePaint)
        canvas.drawText("Invoice #: ${order.invoiceNumber}", 430f, 65f, boldPaint)
        canvas.drawText("Order #: ${order.orderNumber}", 430f, 82f, bodyPaint)
        canvas.drawText("Date: ${order.date}", 430f, 98f, bodyPaint)
        canvas.drawText("Status: ${order.status}", 430f, 114f, bodyPaint)

        y = 135f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 22f

        // Bill To Section
        canvas.drawText("BILL TO DEALER:", 40f, y, boldPaint)
        canvas.drawText("DELIVERY LOCATION:", 320f, y, boldPaint)
        y += 16f
        canvas.drawText(order.dealerName, 40f, y, boldPaint)
        canvas.drawText("${order.location} (${order.area})", 320f, y, bodyPaint)
        y += 15f
        canvas.drawText(order.dealerAddress.ifBlank { order.location }, 40f, y, bodyPaint)
        if (order.farmerName.isNotBlank()) {
            canvas.drawText("Farmer: ${order.farmerName}", 320f, y, bodyPaint)
        }
        y += 24f

        // Table Header
        canvas.drawRect(40f, y - 14f, 555f, y + 8f, headerBgPaint)
        canvas.drawText("Product", 48f, y, boldPaint)
        canvas.drawText("Bag Size", 210f, y, boldPaint)
        canvas.drawText("Qty (Bags)", 280f, y, boldPaint)
        canvas.drawText("Weight", 350f, y, boldPaint)
        canvas.drawText("Rate", 420f, y, boldPaint)
        canvas.drawText("Net (${config.currencySymbol})", 485f, y, boldPaint)
        y += 22f

        items.forEach { item ->
            canvas.drawText("${item.productCode} - ${item.productName.take(20)}", 48f, y, bodyPaint)
            canvas.drawText(item.bagSizeLabel, 210f, y, bodyPaint)
            canvas.drawText("${item.quantityBags}", 285f, y, bodyPaint)
            canvas.drawText("${item.totalKg.roundToInt()} KG", 350f, y, bodyPaint)
            canvas.drawText("${item.pricePerBag.roundToInt()}", 420f, y, bodyPaint)
            canvas.drawText("${item.netAmount.roundToInt()}", 485f, y, boldPaint)
            y += 18f
            canvas.drawLine(40f, y - 10f, 555f, y - 10f, linePaint)
        }

        y += 16f
        // Totals Box
        canvas.drawText("Total Bags: ${order.totalBags} Bags", 40f, y, boldPaint)
        canvas.drawText("Gross Amount:", 370f, y, bodyPaint)
        canvas.drawText(BusinessCalculations.formatCurrency(order.grossAmount, config.currencySymbol), 470f, y, bodyPaint)
        y += 18f

        canvas.drawText("Total Weight: ${order.totalKg.roundToInt()} KG (${BusinessCalculations.formatDecimal(order.totalMt)} MT)", 40f, y, boldPaint)
        canvas.drawText("Discount:", 370f, y, bodyPaint)
        canvas.drawText(BusinessCalculations.formatCurrency(order.discountAmount, config.currencySymbol), 470f, y, bodyPaint)
        y += 18f

        canvas.drawText("Net Payable:", 370f, y, boldPaint)
        canvas.drawText(BusinessCalculations.formatCurrency(order.netAmount, config.currencySymbol), 470f, y, boldPaint)
        y += 18f

        canvas.drawText("Amount Received:", 370f, y, bodyPaint)
        canvas.drawText(BusinessCalculations.formatCurrency(order.paidAmount, config.currencySymbol), 470f, y, bodyPaint)
        y += 18f

        canvas.drawText("Balance Outstanding:", 370f, y, boldPaint)
        canvas.drawText(BusinessCalculations.formatCurrency(order.outstandingAmount, config.currencySymbol), 470f, y, boldPaint)
        y += 30f

        if (order.remarks.isNotBlank()) {
            canvas.drawText("Remarks: ${order.remarks}", 40f, y, bodyPaint)
            y += 24f
        }

        canvas.drawLine(40f, 770f, 555f, 770f, linePaint)
        canvas.drawText("Generated by DairyFeed Pro • Authorized Signature: ______________________", 40f, 795f, subtitlePaint)

        pdfDocument.finishPage(page)

        val safeName = order.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(getExportsDir(context), "${safeName}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    fun printPdfFile(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    file.inputStream().use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    fun exportCsvReport(
        context: Context,
        fileNamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(getExportsDir(context), "${fileNamePrefix}_$stamp.csv")
        file.writeText(sb.toString())
        return file
    }

    fun generateSummaryReportPdf(
        context: Context,
        config: BusinessConfigEntity,
        reportTitle: String,
        periodSubtitle: String,
        summaryMetrics: List<Pair<String, String>>,
        tableHeaders: List<String>,
        tableRows: List<List<String>>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(15, 82, 51)
            textSize = 18f
            isFakeBoldText = true
        }
        val boldPaint = Paint().apply {
            color = AndroidColor.rgb(17, 28, 22)
            textSize = 11f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 35)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = AndroidColor.rgb(196, 209, 202)
            strokeWidth = 1f
        }

        var y = 40f
        canvas.drawText("${config.companyName} — $reportTitle", 40f, y, titlePaint)
        y += 18f
        canvas.drawText(periodSubtitle, 40f, y, bodyPaint)
        y += 14f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f

        // Summary metrics in 2 columns
        summaryMetrics.chunked(2).forEach { pair ->
            val left = pair[0]
            canvas.drawText("${left.first}: ${left.second}", 40f, y, boldPaint)
            if (pair.size > 1) {
                val right = pair[1]
                canvas.drawText("${right.first}: ${right.second}", 310f, y, boldPaint)
            }
            y += 16f
        }

        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 18f

        // Table Header
        val colCount = tableHeaders.size.coerceAtLeast(1)
        val colWidth = 515f / colCount
        tableHeaders.forEachIndexed { idx, h ->
            canvas.drawText(h.take(18), 40f + idx * colWidth, y, boldPaint)
        }
        y += 8f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 16f

        tableRows.take(32).forEach { row ->
            row.forEachIndexed { idx, cell ->
                canvas.drawText(cell.take(22), 40f + idx * colWidth, y, bodyPaint)
            }
            y += 15f
        }

        pdfDocument.finishPage(page)
        val safeName = reportTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(getExportsDir(context), "${safeName}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    // --- Full Database JSON Backup & Restore ---
    suspend fun createFullBackupJson(dao: DairyDao): String {
        val root = JSONObject()
        root.put("backupVersion", 1)
        root.put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        // Products
        val productsArr = JSONArray()
        dao.getAllProductsOnce().forEach { p ->
            productsArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("code", p.code)
                    put("name", p.name)
                    put("category", p.category)
                    put("description", p.description)
                    put("defaultBagWeightKg", p.defaultBagWeightKg)
                    put("purchasePrice", p.purchasePrice)
                    put("dealerPrice", p.dealerPrice)
                    put("salePrice", p.salePrice)
                    put("retailPrice", p.retailPrice)
                    put("specialPrice", p.specialPrice)
                    put("minStockBags", p.minStockBags)
                    put("openingStockBags", p.openingStockBags)
                    put("isActive", p.isActive)
                    put("isArchived", p.isArchived)
                }
            )
        }
        root.put("products", productsArr)

        // Bag sizes
        val bagsArr = JSONArray()
        dao.getAllBagSizesOnce().forEach { b ->
            bagsArr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("label", b.label)
                    put("weightKg", b.weightKg)
                    put("isActive", b.isActive)
                    put("isArchived", b.isArchived)
                }
            )
        }
        root.put("bagSizes", bagsArr)

        // Dealers
        val dealersArr = JSONArray()
        dao.getAllDealersOnce().forEach { d ->
            dealersArr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("code", d.code)
                    put("name", d.name)
                    put("businessName", d.businessName)
                    put("mobile", d.mobile)
                    put("whatsapp", d.whatsapp)
                    put("address", d.address)
                    put("village", d.village)
                    put("city", d.city)
                    put("area", d.area)
                    put("dealerType", d.dealerType)
                    put("openingBalance", d.openingBalance)
                    put("creditLimit", d.creditLimit)
                    put("assignedSalesPerson", d.assignedSalesPerson)
                    put("dateAdded", d.dateAdded)
                    put("status", d.status)
                    put("notes", d.notes)
                    put("customFieldsJson", d.customFieldsJson)
                    put("isArchived", d.isArchived)
                }
            )
        }
        root.put("dealers", dealersArr)

        // Farmers
        val farmersArr = JSONArray()
        dao.getAllFarmersOnce().forEach { f ->
            farmersArr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("code", f.code)
                    put("name", f.name)
                    put("mobile", f.mobile)
                    put("whatsapp", f.whatsapp)
                    put("farmName", f.farmName)
                    put("address", f.address)
                    put("village", f.village)
                    put("city", f.city)
                    put("area", f.area)
                    put("animalCount", f.animalCount)
                    put("currentFeed", f.currentFeed)
                    put("monthlyConsumptionBags", f.monthlyConsumptionBags)
                    put("dealerId", f.dealerId ?: 0L)
                    put("dealerName", f.dealerName)
                    put("dateAdded", f.dateAdded)
                    put("status", f.status)
                    put("notes", f.notes)
                    put("customFieldsJson", f.customFieldsJson)
                    put("isArchived", f.isArchived)
                }
            )
        }
        root.put("farmers", farmersArr)

        // Orders
        val ordersArr = JSONArray()
        dao.getAllOrdersOnce().forEach { o ->
            ordersArr.put(
                JSONObject().apply {
                    put("id", o.id)
                    put("orderNumber", o.orderNumber)
                    put("invoiceNumber", o.invoiceNumber)
                    put("date", o.date)
                    put("dealerId", o.dealerId)
                    put("dealerName", o.dealerName)
                    put("dealerAddress", o.dealerAddress)
                    put("farmerId", o.farmerId ?: 0L)
                    put("farmerName", o.farmerName)
                    put("location", o.location)
                    put("area", o.area)
                    put("totalBags", o.totalBags)
                    put("totalKg", o.totalKg)
                    put("totalMt", o.totalMt)
                    put("grossAmount", o.grossAmount)
                    put("discountAmount", o.discountAmount)
                    put("netAmount", o.netAmount)
                    put("paidAmount", o.paidAmount)
                    put("outstandingAmount", o.outstandingAmount)
                    put("estimatedProfit", o.estimatedProfit)
                    put("status", o.status)
                    put("expectedDeliveryDate", o.expectedDeliveryDate)
                    put("remarks", o.remarks)
                }
            )
        }
        root.put("orders", ordersArr)

        // Order Items
        val itemsArr = JSONArray()
        dao.getAllOrderItemsOnce().forEach { i ->
            itemsArr.put(
                JSONObject().apply {
                    put("id", i.id)
                    put("orderId", i.orderId)
                    put("productId", i.productId)
                    put("productCode", i.productCode)
                    put("productName", i.productName)
                    put("bagSizeLabel", i.bagSizeLabel)
                    put("bagWeightKg", i.bagWeightKg)
                    put("quantityBags", i.quantityBags)
                    put("totalKg", i.totalKg)
                    put("totalMt", i.totalMt)
                    put("purchasePriceSnapshot", i.purchasePriceSnapshot)
                    put("pricePerBag", i.pricePerBag)
                    put("discountPerBag", i.discountPerBag)
                    put("grossAmount", i.grossAmount)
                    put("discountTotal", i.discountTotal)
                    put("netAmount", i.netAmount)
                }
            )
        }
        root.put("orderItems", itemsArr)

        // Payments
        val paymentsArr = JSONArray()
        dao.getAllPaymentsOnce().forEach { p ->
            paymentsArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("paymentNumber", p.paymentNumber)
                    put("date", p.date)
                    put("dealerId", p.dealerId)
                    put("dealerName", p.dealerName)
                    put("amount", p.amount)
                    put("paymentMethod", p.paymentMethod)
                    put("referenceNumber", p.referenceNumber)
                    put("bankAccount", p.bankAccount)
                    put("remarks", p.remarks)
                }
            )
        }
        root.put("payments", paymentsArr)

        // Stock Movements
        val stockArr = JSONArray()
        dao.getAllStockMovementsOnce().forEach { s ->
            stockArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("date", s.date)
                    put("type", s.type)
                    put("productId", s.productId)
                    put("productCode", s.productCode)
                    put("productName", s.productName)
                    put("bagSizeLabel", s.bagSizeLabel)
                    put("bagWeightKg", s.bagWeightKg)
                    put("quantityBags", s.quantityBags)
                    put("totalKg", s.totalKg)
                    put("totalMt", s.totalMt)
                    put("purchasePricePerBag", s.purchasePricePerBag)
                    put("supplierName", s.supplierName)
                    put("invoiceNumber", s.invoiceNumber)
                    put("relatedOrderId", s.relatedOrderId ?: 0L)
                    put("remarks", s.remarks)
                }
            )
        }
        root.put("stockMovements", stockArr)

        return root.toString(2)
    }

    suspend fun saveBackupToLocalFile(context: Context, dao: DairyDao): File {
        val json = createFullBackupJson(dao)
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val file = File(backupDir, "dairyfeed_backup_latest.json")
        file.writeText(json)

        val exportCopy = File(getExportsDir(context), "DairyFeedPro_Backup_${stamp.replace(" ", "_").replace(":", "")}.json")
        exportCopy.writeText(json)

        val cfg = dao.getBusinessConfigOnce() ?: BusinessConfigEntity()
        dao.saveBusinessConfig(cfg.copy(lastBackupAt = stamp))
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = cfg.activeRole,
                actionType = "BACKUP",
                module = "Backup & Restore",
                recordCode = "BKP",
                summary = "Full manual backup saved ($stamp)"
            )
        )
        return exportCopy
    }

    suspend fun restoreFromBackupJson(context: Context, dao: DairyDao, jsonString: String? = null): Boolean {
        return try {
            val content = jsonString ?: run {
                val file = File(File(context.filesDir, "backups"), "dairyfeed_backup_latest.json")
                if (!file.exists()) return false
                file.readText()
            }
            val root = JSONObject(content)

            // Restore Products
            root.optJSONArray("products")?.let { arr ->
                val list = mutableListOf<ProductEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        ProductEntity(
                            id = o.optLong("id"),
                            code = o.optString("code"),
                            name = o.optString("name"),
                            category = o.optString("category"),
                            description = o.optString("description"),
                            defaultBagWeightKg = o.optDouble("defaultBagWeightKg", 40.0),
                            purchasePrice = o.optDouble("purchasePrice"),
                            dealerPrice = o.optDouble("dealerPrice"),
                            salePrice = o.optDouble("salePrice"),
                            retailPrice = o.optDouble("retailPrice"),
                            specialPrice = o.optDouble("specialPrice"),
                            minStockBags = o.optInt("minStockBags", 50),
                            openingStockBags = o.optInt("openingStockBags", 0),
                            isActive = o.optBoolean("isActive", true),
                            isArchived = o.optBoolean("isArchived", false)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertProducts(list)
            }

            // Restore Dealers
            root.optJSONArray("dealers")?.let { arr ->
                dao.clearAllDealers()
                val list = mutableListOf<DealerEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        DealerEntity(
                            id = o.optLong("id"),
                            code = o.optString("code"),
                            name = o.optString("name"),
                            businessName = o.optString("businessName"),
                            mobile = o.optString("mobile"),
                            whatsapp = o.optString("whatsapp"),
                            address = o.optString("address"),
                            village = o.optString("village"),
                            city = o.optString("city"),
                            area = o.optString("area"),
                            dealerType = o.optString("dealerType"),
                            openingBalance = o.optDouble("openingBalance"),
                            creditLimit = o.optDouble("creditLimit", 500000.0),
                            assignedSalesPerson = o.optString("assignedSalesPerson"),
                            dateAdded = o.optString("dateAdded"),
                            status = o.optString("status", "Active"),
                            notes = o.optString("notes"),
                            customFieldsJson = o.optString("customFieldsJson", "{}"),
                            isArchived = o.optBoolean("isArchived", false)
                        )
                    )
                }
                dao.insertDealers(list)
            }

            // Restore Farmers
            root.optJSONArray("farmers")?.let { arr ->
                dao.clearAllFarmers()
                val list = mutableListOf<FarmerEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val dId = o.optLong("dealerId", 0L)
                    list.add(
                        FarmerEntity(
                            id = o.optLong("id"),
                            code = o.optString("code"),
                            name = o.optString("name"),
                            mobile = o.optString("mobile"),
                            whatsapp = o.optString("whatsapp"),
                            farmName = o.optString("farmName"),
                            address = o.optString("address"),
                            village = o.optString("village"),
                            city = o.optString("city"),
                            area = o.optString("area"),
                            animalCount = o.optInt("animalCount"),
                            currentFeed = o.optString("currentFeed"),
                            monthlyConsumptionBags = o.optInt("monthlyConsumptionBags"),
                            dealerId = if (dId == 0L) null else dId,
                            dealerName = o.optString("dealerName"),
                            dateAdded = o.optString("dateAdded"),
                            status = o.optString("status", "Active"),
                            notes = o.optString("notes"),
                            customFieldsJson = o.optString("customFieldsJson", "{}"),
                            isArchived = o.optBoolean("isArchived", false)
                        )
                    )
                }
                dao.insertFarmers(list)
            }

            // Restore Orders & Order Items
            root.optJSONArray("orders")?.let { arr ->
                dao.clearAllOrders()
                val list = mutableListOf<SaleOrderEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val fId = o.optLong("farmerId", 0L)
                    list.add(
                        SaleOrderEntity(
                            id = o.optLong("id"),
                            orderNumber = o.optString("orderNumber"),
                            invoiceNumber = o.optString("invoiceNumber"),
                            date = o.optString("date"),
                            dealerId = o.optLong("dealerId"),
                            dealerName = o.optString("dealerName"),
                            dealerAddress = o.optString("dealerAddress"),
                            farmerId = if (fId == 0L) null else fId,
                            farmerName = o.optString("farmerName"),
                            location = o.optString("location"),
                            area = o.optString("area"),
                            totalBags = o.optInt("totalBags"),
                            totalKg = o.optDouble("totalKg"),
                            totalMt = o.optDouble("totalMt"),
                            grossAmount = o.optDouble("grossAmount"),
                            discountAmount = o.optDouble("discountAmount"),
                            netAmount = o.optDouble("netAmount"),
                            paidAmount = o.optDouble("paidAmount"),
                            outstandingAmount = o.optDouble("outstandingAmount"),
                            estimatedProfit = o.optDouble("estimatedProfit"),
                            status = o.optString("status"),
                            expectedDeliveryDate = o.optString("expectedDeliveryDate"),
                            remarks = o.optString("remarks")
                        )
                    )
                }
                dao.insertSaleOrders(list)
            }

            root.optJSONArray("orderItems")?.let { arr ->
                dao.clearAllOrderItems()
                val list = mutableListOf<OrderItemEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        OrderItemEntity(
                            id = o.optLong("id"),
                            orderId = o.optLong("orderId"),
                            productId = o.optLong("productId"),
                            productCode = o.optString("productCode"),
                            productName = o.optString("productName"),
                            bagSizeLabel = o.optString("bagSizeLabel"),
                            bagWeightKg = o.optDouble("bagWeightKg"),
                            quantityBags = o.optInt("quantityBags"),
                            totalKg = o.optDouble("totalKg"),
                            totalMt = o.optDouble("totalMt"),
                            purchasePriceSnapshot = o.optDouble("purchasePriceSnapshot"),
                            pricePerBag = o.optDouble("pricePerBag"),
                            discountPerBag = o.optDouble("discountPerBag"),
                            grossAmount = o.optDouble("grossAmount"),
                            discountTotal = o.optDouble("discountTotal"),
                            netAmount = o.optDouble("netAmount")
                        )
                    )
                }
                dao.insertOrderItems(list)
            }

            // Restore Payments
            root.optJSONArray("payments")?.let { arr ->
                dao.clearAllPayments()
                val list = mutableListOf<PaymentEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        PaymentEntity(
                            id = o.optLong("id"),
                            paymentNumber = o.optString("paymentNumber"),
                            date = o.optString("date"),
                            dealerId = o.optLong("dealerId"),
                            dealerName = o.optString("dealerName"),
                            amount = o.optDouble("amount"),
                            paymentMethod = o.optString("paymentMethod"),
                            referenceNumber = o.optString("referenceNumber"),
                            bankAccount = o.optString("bankAccount"),
                            remarks = o.optString("remarks")
                        )
                    )
                }
                dao.insertPayments(list)
            }

            // Restore Stock Movements
            root.optJSONArray("stockMovements")?.let { arr ->
                dao.clearAllStockMovements()
                val list = mutableListOf<StockMovementEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val relId = o.optLong("relatedOrderId", 0L)
                    list.add(
                        StockMovementEntity(
                            id = o.optLong("id"),
                            date = o.optString("date"),
                            type = o.optString("type"),
                            productId = o.optLong("productId"),
                            productCode = o.optString("productCode"),
                            productName = o.optString("productName"),
                            bagSizeLabel = o.optString("bagSizeLabel"),
                            bagWeightKg = o.optDouble("bagWeightKg"),
                            quantityBags = o.optInt("quantityBags"),
                            totalKg = o.optDouble("totalKg"),
                            totalMt = o.optDouble("totalMt"),
                            purchasePricePerBag = o.optDouble("purchasePricePerBag"),
                            supplierName = o.optString("supplierName"),
                            invoiceNumber = o.optString("invoiceNumber"),
                            relatedOrderId = if (relId == 0L) null else relId,
                            remarks = o.optString("remarks")
                        )
                    )
                }
                dao.insertStockMovements(list)
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = "Administrator",
                    actionType = "RESTORE",
                    module = "Backup & Restore",
                    recordCode = "RST",
                    summary = "Database restored from backup file"
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
