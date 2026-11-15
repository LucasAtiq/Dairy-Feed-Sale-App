package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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

data class PartnerStatementRow(
    val id: String,
    val date: String,                    // WHEN (Purchase date or payment date)
    val refCode: String,                 // Order # / Invoice # / Payment #
    val type: String,                    // "PURCHASE", "PAYMENT", "OPENING"
    val productsSummary: String,         // WHAT PURCHASED
    val bags: Int,                       // Total bags
    val totalMt: Double,                 // Total MT
    val feedSendDate: String,            // WHEN FEED SEND (Expected delivery / dispatch date)
    val dispatchStatus: String,          // Delivery & Dispatch status
    val billAmount: Double,              // Debit billed amount
    val amountReceived: Double,          // HOW MUCH AMOUNT RECEIVED
    val paymentReceivedDate: String,     // WHEN RECEIVED
    val paymentMethod: String,           // Cash, Bank Transfer, Online, etc.
    val runningBalance: Double           // Running Balance
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

    fun getOrderPaymentStatus(paidAmount: Double, netAmount: Double, outstandingAmount: Double): String {
        return when {
            outstandingAmount <= 0.01 || (netAmount > 0 && paidAmount >= netAmount - 0.01) -> "Paid"
            paidAmount > 0.01 && outstandingAmount > 0.01 -> "Partial"
            else -> "Unpaid"
        }
    }

    fun getDealerPaymentStatus(totalSales: Double, totalPayments: Double, outstandingBalance: Double): String {
        return when {
            outstandingBalance <= 0.01 -> "Paid"
            totalPayments > 0.01 && outstandingBalance > 0.01 -> "Partial"
            else -> "Unpaid"
        }
    }

    fun computeNextBackupSchedule(frequency: String, timeLabel: String = "07:30 PM"): String {
        val cal = Calendar.getInstance()
        return when (frequency) {
            "OFF" -> "Automatic Backup OFF"
            "Weekly" -> {
                cal.add(Calendar.DAY_OF_YEAR, 7)
                val dateStr = SimpleDateFormat("EEE, MMM dd", Locale.US).format(cal.time)
                "$dateStr, $timeLabel"
            }
            else -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val dateStr = SimpleDateFormat("MMM dd", Locale.US).format(cal.time)
                "Tomorrow ($dateStr), $timeLabel"
            }
        }
    }

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

    /**
     * Builds comprehensive chronological Partner (Dealer or Farmer) Statement & Report rows.
     * Contains answers to:
     * - WHEN: date
     * - WHAT PURCHASED: productsSummary, bags, totalMt
     * - WHEN FEED SEND: feedSendDate, dispatchStatus
     * - HOW MUCH AMOUNT RECEIVED & WHEN: amountReceived, paymentReceivedDate, paymentMethod
     * - RUNNING BALANCE: runningBalance
     */
    fun buildPartnerStatementRows(
        orders: List<OrderWithItems>,
        payments: List<PaymentEntity>,
        openingBalance: Double,
        openingDate: String = "",
        period: String = "Overall" // "Overall", "Monthly", "Weekly"
    ): List<PartnerStatementRow> {
        val sevenDaysAgo = SampleDataSeeder.daysOffset(-7)
        val monthPrefix = SampleDataSeeder.currentMonthPrefix()

        val activeOrders = orders.filter { owi ->
            val o = owi.order
            if (o.status == "Cancelled" || o.status == "Draft") return@filter false
            when (period) {
                "Weekly" -> o.date >= sevenDaysAgo
                "Monthly" -> o.date.startsWith(monthPrefix)
                else -> true
            }
        }

        val activePayments = payments.filter { p ->
            when (period) {
                "Weekly" -> p.date >= sevenDaysAgo
                "Monthly" -> p.date.startsWith(monthPrefix)
                else -> true
            }
        }

        data class RawEvent(
            val date: String,
            val timestamp: Long,
            val row: PartnerStatementRow
        )

        val events = mutableListOf<RawEvent>()

        activeOrders.forEach { owi ->
            val o = owi.order
            val productsList = if (owi.items.isEmpty()) {
                "${o.totalBags} Bags Feed"
            } else {
                owi.items.joinToString(", ") { "${it.quantityBags}x ${it.productCode} (${it.bagSizeLabel})" }
            }
            events.add(
                RawEvent(
                    date = o.date,
                    timestamp = o.createdAt,
                    row = PartnerStatementRow(
                        id = "ORD-${o.id}",
                        date = o.date,
                        refCode = o.orderNumber,
                        type = "PURCHASE",
                        productsSummary = productsList,
                        bags = o.totalBags,
                        totalMt = o.totalMt,
                        feedSendDate = o.expectedDeliveryDate.ifBlank { o.date },
                        dispatchStatus = o.status,
                        billAmount = o.netAmount,
                        amountReceived = o.paidAmount,
                        paymentReceivedDate = if (o.paidAmount > 0) o.date else "-",
                        paymentMethod = if (o.paidAmount > 0) "At Order" else "-",
                        runningBalance = 0.0
                    )
                )
            )
        }

        activePayments.forEach { p ->
            events.add(
                RawEvent(
                    date = p.date,
                    timestamp = p.createdAt,
                    row = PartnerStatementRow(
                        id = "PAY-${p.id}",
                        date = p.date,
                        refCode = p.paymentNumber,
                        type = "PAYMENT",
                        productsSummary = "Payment Collection: ${p.paymentMethod} ${if (p.referenceNumber.isNotBlank()) "Ref: " + p.referenceNumber else ""}".trim(),
                        bags = 0,
                        totalMt = 0.0,
                        feedSendDate = "-",
                        dispatchStatus = "Received",
                        billAmount = 0.0,
                        amountReceived = p.amount,
                        paymentReceivedDate = p.date,
                        paymentMethod = p.paymentMethod,
                        runningBalance = 0.0
                    )
                )
            )
        }

        events.sortWith(compareBy<RawEvent> { it.date }.thenBy { it.timestamp })

        val result = mutableListOf<PartnerStatementRow>()
        var running = openingBalance
        if (period == "Overall" && openingBalance != 0.0) {
            result.add(
                PartnerStatementRow(
                    id = "OPENING",
                    date = openingDate.ifBlank { "Initial" },
                    refCode = "OPEN-BAL",
                    type = "OPENING",
                    productsSummary = "Opening Balance Carried Forward",
                    bags = 0,
                    totalMt = 0.0,
                    feedSendDate = "-",
                    dispatchStatus = "Carried Forward",
                    billAmount = if (openingBalance > 0) openingBalance else 0.0,
                    amountReceived = if (openingBalance < 0) -openingBalance else 0.0,
                    paymentReceivedDate = "-",
                    paymentMethod = "-",
                    runningBalance = running
                )
            )
        }

        events.forEach { ev ->
            val netDelta = ev.row.billAmount - ev.row.amountReceived
            running += netDelta
            result.add(ev.row.copy(runningBalance = running))
        }

        return result
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
        canvas.drawText("Generated by WandaBook Pro • Dairy Feed Sales & Management • Authorized Signature: ________________", 40f, 795f, subtitlePaint)

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

    /**
     * Generates a downloadable, high-resolution printable PDF Statement of Account for a Dealer or Farmer.
     * Includes:
     * - Official Mill/Company Letterhead
     * - Partner Info & Period (Overall / Monthly / Weekly)
     * - Key summary metrics: Total Bags, Total MT, Total Purchased, Total Received, Net Balance
     * - Itemized Transaction Table with When, What Purchased, When Feed Send, Amount Received, Balance
     */
    fun generatePartnerStatementPdfFile(
        context: Context,
        config: BusinessConfigEntity,
        partnerName: String,
        partnerCode: String,
        partnerType: String, // "Dealer" or "Farmer"
        partnerPhone: String,
        partnerArea: String,
        periodLabel: String,
        totalPurchasedAmount: Double,
        totalPaidAmount: Double,
        netBalance: Double,
        totalBags: Int,
        totalMt: Double,
        rows: List<PartnerStatementRow>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val primaryGreen = AndroidColor.rgb(15, 82, 51)
        val darkText = AndroidColor.rgb(17, 28, 22)
        val mutedText = AndroidColor.rgb(100, 116, 139)
        val lightBorder = AndroidColor.rgb(226, 232, 240)
        val headerBg = AndroidColor.rgb(240, 253, 244)

        val titlePaint = Paint().apply {
            color = primaryGreen
            textSize = 15f
            isFakeBoldText = true
        }
        val subTitlePaint = Paint().apply {
            color = mutedText
            textSize = 9.5f
        }
        val boldPaint = Paint().apply {
            color = darkText
            textSize = 9.5f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = darkText
            textSize = 8.5f
        }
        val smallMutedPaint = Paint().apply {
            color = mutedText
            textSize = 7.5f
        }
        val linePaint = Paint().apply {
            color = lightBorder
            strokeWidth = 1f
        }
        val cardBgPaint = Paint().apply {
            color = headerBg
        }
        val cardBorderPaint = Paint().apply {
            color = primaryGreen
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        var y = 35f
        // 1. Company Letterhead
        canvas.drawText(config.companyName, 35f, y, titlePaint)
        canvas.drawText("STATEMENT OF ACCOUNT", 400f, y, titlePaint)
        y += 14f
        canvas.drawText("${config.businessTitle} • Contact: ${config.contactPhone} • ${config.companyAddress}", 35f, y, subTitlePaint)
        canvas.drawText("NTN: ${config.taxRegistrationNumber}", 400f, y, subTitlePaint)
        y += 12f
        canvas.drawLine(35f, y, 560f, y, linePaint)
        y += 18f

        // 2. Partner Info Box
        canvas.drawText("ACCOUNT: $partnerName ($partnerCode)", 35f, y, boldPaint)
        canvas.drawText("PERIOD: $periodLabel", 400f, y, boldPaint)
        y += 13f
        canvas.drawText("Type: $partnerType • Area: $partnerArea • Phone: $partnerPhone", 35f, y, subTitlePaint)
        val dateGenerated = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas.drawText("Generated: $dateGenerated", 400f, y, subTitlePaint)
        y += 14f

        // 3. Summary Metric Cards
        val cardWidth = 125f
        val cardHeight = 38f
        val cardSpacing = 6f
        val startX = 35f

        val metrics = listOf(
            Pair("Total Bags / MT", "$totalBags Bags (${String.format(Locale.US, "%.2f", totalMt)} MT)"),
            Pair("Total Purchased", "${config.currencySymbol} ${String.format(Locale.US, "%,.0f", totalPurchasedAmount)}"),
            Pair("Total Received", "${config.currencySymbol} ${String.format(Locale.US, "%,.0f", totalPaidAmount)}"),
            Pair("Net Outstanding", "${config.currencySymbol} ${String.format(Locale.US, "%,.0f", netBalance)}")
        )

        metrics.forEachIndexed { i, m ->
            val cx = startX + i * (cardWidth + cardSpacing)
            canvas.drawRect(cx, y, cx + cardWidth, y + cardHeight, cardBgPaint)
            canvas.drawRect(cx, y, cx + cardWidth, y + cardHeight, cardBorderPaint)
            canvas.drawText(m.first, cx + 8f, y + 14f, smallMutedPaint)
            canvas.drawText(m.second, cx + 8f, y + 28f, boldPaint)
        }
        y += cardHeight + 16f

        // 4. Table Header (7 Columns)
        canvas.drawRect(35f, y - 11f, 560f, y + 8f, cardBgPaint)
        canvas.drawText("When (Date)", 38f, y, boldPaint)
        canvas.drawText("Order / Ref", 98f, y, boldPaint)
        canvas.drawText("What Purchased (Feed Details)", 155f, y, boldPaint)
        canvas.drawText("Feed Send", 310f, y, boldPaint)
        canvas.drawText("Bill Amount", 370f, y, boldPaint)
        canvas.drawText("Amount Recvd", 440f, y, boldPaint)
        canvas.drawText("Balance", 510f, y, boldPaint)
        y += 14f
        canvas.drawLine(35f, y - 4f, 560f, y - 4f, linePaint)

        // 5. Table Rows
        rows.take(28).forEach { r ->
            canvas.drawText(r.date.take(10), 38f, y, bodyPaint)
            canvas.drawText(r.refCode.take(11), 98f, y, bodyPaint)
            canvas.drawText(r.productsSummary.take(28), 155f, y, bodyPaint)
            canvas.drawText("${r.feedSendDate.take(8)} ${r.dispatchStatus.take(4)}", 310f, y, bodyPaint)
            canvas.drawText(if (r.billAmount > 0) String.format(Locale.US, "%,.0f", r.billAmount) else "-", 370f, y, bodyPaint)
            canvas.drawText(if (r.amountReceived > 0) String.format(Locale.US, "%,.0f", r.amountReceived) else "-", 440f, y, bodyPaint)
            canvas.drawText(String.format(Locale.US, "%,.0f", r.runningBalance), 510f, y, boldPaint)
            y += 16f
            canvas.drawLine(35f, y - 4f, 560f, y - 4f, linePaint)
        }

        // Footer
        canvas.drawLine(35f, 790f, 560f, 790f, linePaint)
        canvas.drawText("Official WandaBook Pro Statement • System-verified ledger records • Authorized Signature: __________________", 35f, 805f, subTitlePaint)

        pdfDocument.finishPage(page)
        val safeName = "Statement_${partnerType}_${partnerCode.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
        val file = File(getExportsDir(context), "${safeName}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    /**
     * Exports a structured CSV spreadsheet of partner statement records.
     */
    fun exportPartnerStatementCsv(
        context: Context,
        partnerType: String,
        partnerCode: String,
        partnerName: String,
        periodLabel: String,
        rows: List<PartnerStatementRow>
    ): File {
        val headers = listOf(
            "When (Date)",
            "Order / Ref #",
            "Transaction Type",
            "What Purchased",
            "Bags Qty",
            "Weight MT",
            "Feed Send Date",
            "Dispatch Status",
            "Bill Amount (Rs)",
            "Amount Received (Rs)",
            "When Received",
            "Payment Method",
            "Running Balance (Rs)"
        )
        val dataRows = rows.map { r ->
            listOf(
                r.date,
                r.refCode,
                r.type,
                r.productsSummary,
                "${r.bags}",
                String.format(Locale.US, "%.2f", r.totalMt),
                r.feedSendDate,
                r.dispatchStatus,
                String.format(Locale.US, "%.2f", r.billAmount),
                String.format(Locale.US, "%.2f", r.amountReceived),
                r.paymentReceivedDate,
                r.paymentMethod,
                String.format(Locale.US, "%.2f", r.runningBalance)
            )
        }
        return exportCsvReport(
            context = context,
            fileNamePrefix = "Statement_${partnerType}_${partnerCode}",
            headers = headers,
            rows = dataRows
        )
    }

    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    // --- Full Database JSON Backup & Restore ---
    suspend fun createFullBackupJson(dao: DairyDao): String {
        val root = JSONObject()
        root.put("appName", "WandaBook Pro")
        root.put("subtitle", "Dairy Feed Sales & Management")
        root.put("developer", "Made by Attique ur Rehman")
        root.put("backupVersion", 2)
        root.put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        // Business Config / Settings
        dao.getBusinessConfigOnce()?.let { cfg ->
            root.put(
                "businessConfig",
                JSONObject().apply {
                    put("companyName", cfg.companyName)
                    put("businessTitle", cfg.businessTitle)
                    put("contactPhone", cfg.contactPhone)
                    put("contactEmail", cfg.contactEmail)
                    put("companyAddress", cfg.companyAddress)
                    put("currencySymbol", cfg.currencySymbol)
                    put("taxRegistrationNumber", cfg.taxRegistrationNumber)
                    put("invoicePrefix", cfg.invoicePrefix)
                    put("activeRole", cfg.activeRole)
                    put("themeMode", cfg.themeMode)
                    put("autoBackupFrequency", cfg.autoBackupFrequency)
                    put("emailBackupEnabled", cfg.emailBackupEnabled)
                    put("backupEmailAddress", cfg.backupEmailAddress)
                    put("emailBackupFrequency", cfg.emailBackupFrequency)
                    put("emailBackupTime", cfg.emailBackupTime)
                }
            )
        }

        // Lookups
        val lookupsArr = JSONArray()
        dao.getAllLookupsOnce().forEach { l ->
            lookupsArr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("categoryType", l.categoryType)
                    put("name", l.name)
                    put("description", l.description)
                    put("isActive", l.isActive)
                    put("isArchived", l.isArchived)
                    put("sortOrder", l.sortOrder)
                }
            )
        }
        root.put("lookups", lookupsArr)

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
                    put("defaultDiscount", p.defaultDiscount)
                    put("minStockBags", p.minStockBags)
                    put("openingStockBags", p.openingStockBags)
                    put("isActive", p.isActive)
                    put("isArchived", p.isArchived)
                }
            )
        }
        root.put("products", productsArr)

        // Price History
        val priceHistArr = JSONArray()
        dao.getAllPriceHistoryOnce().forEach { ph ->
            priceHistArr.put(
                JSONObject().apply {
                    put("id", ph.id)
                    put("productId", ph.productId)
                    put("productCode", ph.productCode)
                    put("productName", ph.productName)
                    put("oldPurchasePrice", ph.oldPurchasePrice)
                    put("newPurchasePrice", ph.newPurchasePrice)
                    put("oldDealerPrice", ph.oldDealerPrice)
                    put("newDealerPrice", ph.newDealerPrice)
                    put("oldSalePrice", ph.oldSalePrice)
                    put("newSalePrice", ph.newSalePrice)
                    put("oldRetailPrice", ph.oldRetailPrice)
                    put("newRetailPrice", ph.newRetailPrice)
                    put("changedAt", ph.changedAt)
                    put("changedBy", ph.changedBy)
                    put("remarks", ph.remarks)
                }
            )
        }
        root.put("priceHistory", priceHistArr)

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

        // Feed Trials & Milk Records
        val trialsArr = JSONArray()
        dao.getAllTrialsOnce().forEach { t ->
            trialsArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("trialCode", t.trialCode)
                    put("farmerId", t.farmerId)
                    put("farmerName", t.farmerName)
                    put("dealerId", t.dealerId ?: 0L)
                    put("dealerName", t.dealerName)
                    put("farmName", t.farmName)
                    put("location", t.location)
                    put("animalCount", t.animalCount)
                    put("currentFeed", t.currentFeed)
                    put("trialFeed", t.trialFeed)
                    put("currentFeedCode", t.currentFeedCode)
                    put("trialFeedCode", t.trialFeedCode)
                    put("startDate", t.startDate)
                    put("transitionDays", t.transitionDays)
                    put("endDate", t.endDate)
                    put("status", t.status)
                    put("remarks", t.remarks)
                    put("customFieldsJson", t.customFieldsJson)
                }
            )
        }
        root.put("feedTrials", trialsArr)

        val milkArr = JSONArray()
        dao.getAllMilkRecordsOnce().forEach { m ->
            milkArr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("trialId", m.trialId)
                    put("animalIdTag", m.animalIdTag)
                    put("recordDate", m.recordDate)
                    put("startingMilkLiters", m.startingMilkLiters)
                    put("dailyMilkLiters", m.dailyMilkLiters)
                    put("feedQuantityKg", m.feedQuantityKg)
                    put("remarks", m.remarks)
                }
            )
        }
        root.put("milkRecords", milkArr)

        // Complaints
        val complaintsArr = JSONArray()
        dao.getAllComplaintsOnce().forEach { c ->
            complaintsArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("complaintCode", c.complaintCode)
                    put("date", c.date)
                    put("partnerType", c.partnerType)
                    put("partnerId", c.partnerId)
                    put("partnerName", c.partnerName)
                    put("productId", c.productId ?: 0L)
                    put("productName", c.productName)
                    put("complaintType", c.complaintType)
                    put("description", c.description)
                    put("investigation", c.investigation)
                    put("actionTaken", c.actionTaken)
                    put("followUpDate", c.followUpDate)
                    put("status", c.status)
                    put("resolution", c.resolution)
                }
            )
        }
        root.put("complaints", complaintsArr)

        // Targets, Expenses & Travel
        val targetsArr = JSONArray()
        dao.getAllTargetsOnce().forEach { tg ->
            targetsArr.put(
                JSONObject().apply {
                    put("id", tg.id)
                    put("periodLabel", tg.periodLabel)
                    put("periodType", tg.periodType)
                    put("targetBags", tg.targetBags)
                    put("targetMt", tg.targetMt)
                    put("targetSalesValue", tg.targetSalesValue)
                    put("targetCollection", tg.targetCollection)
                    put("notes", tg.notes)
                }
            )
        }
        root.put("targets", targetsArr)

        val expensesArr = JSONArray()
        dao.getAllExpensesOnce().forEach { ex ->
            expensesArr.put(
                JSONObject().apply {
                    put("id", ex.id)
                    put("date", ex.date)
                    put("category", ex.category)
                    put("amount", ex.amount)
                    put("paymentMethod", ex.paymentMethod)
                    put("location", ex.location)
                    put("description", ex.description)
                }
            )
        }
        root.put("expenses", expensesArr)

        val travelArr = JSONArray()
        dao.getAllTravelLogsOnce().forEach { tr ->
            travelArr.put(
                JSONObject().apply {
                    put("id", tr.id)
                    put("date", tr.date)
                    put("startingOdometer", tr.startingOdometer)
                    put("endingOdometer", tr.endingOdometer)
                    put("startingLocation", tr.startingLocation)
                    put("destination", tr.destination)
                    put("totalKm", tr.totalKm)
                    put("workingKm", tr.workingKm)
                    put("personalKm", tr.personalKm)
                    put("purpose", tr.purpose)
                }
            )
        }
        root.put("travelLogs", travelArr)

        return root.toString(2)
    }

    fun validateBackupJson(jsonString: String): Pair<Boolean, String> {
        if (jsonString.isBlank()) {
            return false to "Backup file is empty. Your current data has not been changed."
        }
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("backupVersion", 0)
            val hasCoreArrays = root.has("products") || root.has("dealers") || root.has("orders")
            if (version <= 0 || !hasCoreArrays) {
                false to "Invalid or incompatible backup file format. Your current data is safe."
            } else {
                true to "Valid WandaBook Pro backup (v$version)"
            }
        } catch (_: Exception) {
            false to "Corrupted backup file. Could not parse JSON structure. Your data has not been modified."
        }
    }

    suspend fun saveBackupToLocalFile(context: Context, dao: DairyDao, isAutoBackup: Boolean = false): File {
        val json = createFullBackupJson(dao)
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val humanStamp = SimpleDateFormat("yyyy-MM-dd, hh:mm a", Locale.US).format(Date())

        val latestFile = File(backupDir, "wandabook_backup_latest.json")
        latestFile.writeText(json)
        // Maintain legacy filename as well for seamless backward compatibility
        File(backupDir, "dairyfeed_backup_latest.json").writeText(json)

        val exportCopy = File(getExportsDir(context), "WandaBookPro_Backup_${dateOnly}.json")
        exportCopy.writeText(json)

        val cfg = dao.getBusinessConfigOnce() ?: BusinessConfigEntity()
        val nextSched = BusinessCalculations.computeNextBackupSchedule(cfg.autoBackupFrequency, cfg.emailBackupTime)
        dao.saveBusinessConfig(
            cfg.copy(
                lastBackupAt = humanStamp,
                lastBackupStatus = "Successful",
                nextBackupAt = nextSched
            )
        )
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = cfg.activeRole,
                actionType = "BACKUP",
                module = "Backup & Restore",
                recordCode = if (isAutoBackup) "AUTO-BKP" else "BKP",
                summary = "${if (isAutoBackup) "Automatic" else "Manual"} backup saved ($humanStamp)"
            )
        )
        return exportCopy
    }

    suspend fun checkAndRunAutoBackupIfDue(context: Context, dao: DairyDao) {
        try {
            val cfg = dao.getBusinessConfigOnce() ?: return
            if (cfg.autoBackupFrequency == "OFF") return

            val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val needsBackup = when (cfg.autoBackupFrequency) {
                "Daily" -> !cfg.lastBackupAt.startsWith(todayPrefix)
                "Weekly" -> {
                    if (cfg.lastBackupAt == "Never") true
                    else {
                        val lastDateStr = cfg.lastBackupAt.take(10)
                        val sevenDaysAgo = SampleDataSeeder.daysOffset(-7)
                        lastDateStr <= sevenDaysAgo
                    }
                }
                else -> false
            }
            if (needsBackup) {
                saveBackupToLocalFile(context, dao, isAutoBackup = true)
            }
        } catch (_: Exception) {
            // Automatic backup must fail safely and never disrupt app operation
        }
    }

    suspend fun dispatchEmailBackup(
        context: Context,
        dao: DairyDao,
        recipientEmail: String,
        isTest: Boolean = false
    ): Pair<Boolean, String> {
        if (recipientEmail.isBlank() || !recipientEmail.contains("@")) {
            return false to "Please enter a valid backup email address first."
        }
        if (!isInternetAvailable(context)) {
            return false to "Internet connection required for email backup."
        }
        return try {
            val backupFile = saveBackupToLocalFile(context, dao, isAutoBackup = false)
            val cfg = dao.getBusinessConfigOnce() ?: BusinessConfigEntity()
            val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            // If user configured a modular secure cloud webhook/relay endpoint, post JSON over HTTPS
            if (cfg.emailWebhookUrl.startsWith("https://")) {
                try {
                    val url = URL(cfg.emailWebhookUrl.trim())
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 6000
                        readTimeout = 6000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val payload = JSONObject().apply {
                        put("to", recipientEmail.trim())
                        put("subject", "WandaBook Pro Backup ($dateOnly)")
                        put("isTest", isTest)
                        put("backupData", JSONObject(backupFile.readText()))
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..299) {
                        return true to "Backup sent to $recipientEmail (${backupFile.name})"
                    }
                } catch (_: Exception) {
                    // Fallback to secure Android Email Share Sheet with attachment
                }
            }

            // Launch secure Android Email Intent with the JSON backup attached (never asks for Gmail password)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )
            val subject = if (isTest) "WandaBook Pro — Test Backup ($dateOnly)" else "WandaBook Pro — Automated Backup ($dateOnly)"
            val body = buildString {
                appendLine("WandaBook Pro — Dairy Feed Sales & Management")
                appendLine("Backup File: ${backupFile.name}")
                appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
                appendLine("Configured Recipient: $recipientEmail")
                appendLine()
                appendLine("Your complete business database backup JSON is attached to this email.")
            }
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail.trim()))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(emailIntent, "Send Backup to $recipientEmail").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            true to "Backup prepared for $recipientEmail (${backupFile.name})"
        } catch (_: Exception) {
            false to "Something went wrong. Your data has not been deleted. Please try again."
        }
    }

    suspend fun restoreFromBackupJsonWithValidation(
        context: Context,
        dao: DairyDao,
        jsonString: String? = null
    ): Pair<Boolean, String> {
        return try {
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val content = jsonString ?: run {
                val primaryFile = File(backupDir, "wandabook_backup_latest.json")
                val legacyFile = File(backupDir, "dairyfeed_backup_latest.json")
                when {
                    primaryFile.exists() -> primaryFile.readText()
                    legacyFile.exists() -> legacyFile.readText()
                    else -> return false to "No local backup file found yet. Please run 'Backup Now' or import a backup JSON first."
                }
            }

            // 1. Validate backup file before touching any database table
            val (isValid, validationMessage) = validateBackupJson(content)
            if (!isValid) {
                return false to validationMessage
            }

            // 2. Automatically create a pre-restore safety backup of current data
            val preRestoreSafetyJson = createFullBackupJson(dao)
            File(backupDir, "wandabook_pre_restore_safety_backup.json").writeText(preRestoreSafetyJson)

            val root = JSONObject(content)

            // Restore Lookups if present
            root.optJSONArray("lookups")?.let { arr ->
                val list = mutableListOf<LookupItemEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        LookupItemEntity(
                            id = o.optLong("id"),
                            categoryType = o.optString("categoryType"),
                            name = o.optString("name"),
                            description = o.optString("description"),
                            isActive = o.optBoolean("isActive", true),
                            isArchived = o.optBoolean("isArchived", false),
                            sortOrder = o.optInt("sortOrder", i)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    dao.clearAllLookups()
                    dao.insertLookups(list)
                }
            }

            // Restore Bag Sizes if present
            root.optJSONArray("bagSizes")?.let { arr ->
                val list = mutableListOf<BagSizeEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        BagSizeEntity(
                            id = o.optLong("id"),
                            label = o.optString("label"),
                            weightKg = o.optDouble("weightKg", 40.0),
                            isActive = o.optBoolean("isActive", true),
                            isArchived = o.optBoolean("isArchived", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    dao.clearAllBagSizes()
                    dao.insertBagSizes(list)
                }
            }

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
                            defaultDiscount = o.optDouble("defaultDiscount", 0.0),
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
                    summary = "Database restored safely (pre-restore safety snapshot saved)"
                )
            )
            true to "Backup restored successfully. Pre-restore safety snapshot saved."
        } catch (_: Exception) {
            false to "Something went wrong. Your data has not been deleted. Please try again."
        }
    }

    suspend fun restoreFromBackupJson(context: Context, dao: DairyDao, jsonString: String? = null): Boolean {
        return restoreFromBackupJsonWithValidation(context, dao, jsonString).first
    }
}
