package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MoreHubScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubModulePillBar(
            modules = listOf(
                SubModule.REPORTS,
                SubModule.TARGETS,
                SubModule.REMINDERS,
                SubModule.SETTINGS,
                SubModule.BACKUP,
                SubModule.SEARCH
            ),
            selected = uiState.activeSubModule,
            onSelect = { viewModel.navigateToSubModule(it) }
        )

        when (uiState.activeSubModule) {
            SubModule.TARGETS -> TargetsSection(uiState, viewModel)
            SubModule.REMINDERS -> NotificationsAndRemindersSection(uiState, viewModel)
            SubModule.SETTINGS -> SettingsAndAdminSection(uiState, viewModel)
            SubModule.BACKUP -> BackupAndDemoDataSection(uiState, viewModel)
            SubModule.SEARCH -> GlobalSearchSection(uiState, viewModel)
            else -> ComprehensiveReportsSection(uiState)
        }
    }
}

@Composable
private fun ComprehensiveReportsSection(uiState: AppUiState) {
    val context = LocalContext.current
    val cur = uiState.config.currencySymbol

    val reportTypes = listOf(
        "Daily Sales",
        "Weekly Sales",
        "Monthly Sales",
        "Dealer-wise Sales",
        "Product-wise Sales",
        "Area-wise Sales",
        "Bags Report",
        "MT Report",
        "Payment Collection",
        "Outstanding Report",
        "Profit Report",
        "Stock Report",
        "Feed Trial Report",
        "Visit Report",
        "Complaint Report",
        "Expense Report",
        "Travel Report",
        "Target vs Achievement"
    )

    var selectedReport by remember { mutableStateOf(reportTypes.first()) }
    var areaFilter by remember { mutableStateOf("All") }
    var dealerFilter by remember { mutableStateOf("All") }
    var productFilter by remember { mutableStateOf("All") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    val areas = remember(uiState.lookups) {
        listOf("All") + uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name }
    }
    val dealerNames = remember(uiState.dealers) {
        listOf("All") + uiState.dealers.filter { !it.isArchived }.map { it.businessName }
    }
    val productCodes = remember(uiState.products) {
        listOf("All") + uiState.products.filter { !it.isArchived }.map { it.code }
    }

    // Filtered orders for sales-based reports
    val filteredOrders = remember(uiState.ordersWithItems, areaFilter, dealerFilter, productFilter, startDate, endDate, selectedReport) {
        val today = SampleDataSeeder.todayDate()
        val sevenDaysAgo = SampleDataSeeder.daysOffset(-7)
        val monthPrefix = SampleDataSeeder.currentMonthPrefix()

        uiState.ordersWithItems.filter { owi ->
            val o = owi.order
            if (o.status == "Cancelled" || o.status == "Draft") return@filter false
            val matchReportPeriod = when (selectedReport) {
                "Daily Sales" -> o.date == today
                "Weekly Sales" -> o.date >= sevenDaysAgo
                "Monthly Sales" -> o.date.startsWith(monthPrefix)
                else -> true
            }
            val matchArea = areaFilter == "All" || o.area.equals(areaFilter, true)
            val matchDealer = dealerFilter == "All" || o.dealerName.equals(dealerFilter, true)
            val matchProd = productFilter == "All" || owi.items.any { it.productCode.equals(productFilter, true) }
            val matchStart = startDate.isBlank() || o.date >= startDate.trim()
            val matchEnd = endDate.isBlank() || o.date <= endDate.trim()
            matchReportPeriod && matchArea && matchDealer && matchProd && matchStart && matchEnd
        }
    }

    // Generate dynamic report headers & rows based on selectedReport
    val (headers, rows, summaryMetrics) = remember(
        selectedReport,
        filteredOrders,
        uiState
    ) {
        when (selectedReport) {
            "Dealer-wise Sales", "Outstanding Report" -> {
                val h = listOf("Dealer", "Area", "Bags", "MT", "Sales", "Outstanding")
                val r = uiState.dealerSummaries.filter { !it.dealer.isArchived }.map { ds ->
                    listOf(
                        ds.dealer.businessName,
                        ds.dealer.area,
                        "${ds.totalBags}",
                        BusinessCalculations.formatDecimal(ds.totalMt),
                        BusinessCalculations.formatCurrency(ds.totalSales, cur),
                        BusinessCalculations.formatCurrency(ds.outstandingBalance, cur)
                    )
                }
                val m = listOf(
                    "Active Dealers" to "${r.size}",
                    "Total Sales" to BusinessCalculations.formatCurrency(uiState.dealerSummaries.sumOf { it.totalSales }, cur),
                    "Total Outstanding" to BusinessCalculations.formatCurrency(uiState.dealerSummaries.sumOf { it.outstandingBalance.coerceAtLeast(0.0) }, cur)
                )
                Triple(h, r, m)
            }
            "Product-wise Sales", "Bags Report", "MT Report" -> {
                val consolidated = BusinessCalculations.computeConsolidatedOrders(filteredOrders)
                val h = listOf("Product", "Bag Size", "Total Bags", "Total KG", "Total MT", "Sales Value")
                val r = consolidated.lines.map {
                    listOf(
                        "${it.productCode} - ${it.productName}",
                        it.bagSizeLabel,
                        "${it.totalBags}",
                        "${it.totalKg.roundToInt()} KG",
                        "${BusinessCalculations.formatDecimal(it.totalMt)} MT",
                        BusinessCalculations.formatCurrency(it.totalValue, cur)
                    )
                }
                val m = listOf(
                    "Total Bags Sold" to "${consolidated.totalBags} Bags",
                    "Total Weight (KG)" to "${consolidated.totalKg.roundToInt()} KG",
                    "Total Metric Tons" to "${BusinessCalculations.formatDecimal(consolidated.totalMt)} MT",
                    "Net Value" to BusinessCalculations.formatCurrency(consolidated.totalValue, cur)
                )
                Triple(h, r, m)
            }
            "Area-wise Sales" -> {
                val byArea = filteredOrders.groupBy { it.order.area }
                val h = listOf("Area / Territory", "Orders", "Total Bags", "Total MT", "Net Sales")
                val r = byArea.entries.map { (area, list) ->
                    listOf(
                        area,
                        "${list.size}",
                        "${list.sumOf { it.order.totalBags }}",
                        BusinessCalculations.formatDecimal(list.sumOf { it.order.totalMt }),
                        BusinessCalculations.formatCurrency(list.sumOf { it.order.netAmount }, cur)
                    )
                }
                val m = listOf(
                    "Territories Covered" to "${byArea.size}",
                    "Total Bags" to "${filteredOrders.sumOf { it.order.totalBags }} Bags",
                    "Total Net Sales" to BusinessCalculations.formatCurrency(filteredOrders.sumOf { it.order.netAmount }, cur)
                )
                Triple(h, r, m)
            }
            "Payment Collection" -> {
                val h = listOf("Receipt #", "Date", "Dealer", "Method", "Reference", "Amount")
                val r = uiState.payments.map {
                    listOf(it.paymentNumber, it.date, it.dealerName, it.paymentMethod, it.referenceNumber, BusinessCalculations.formatCurrency(it.amount, cur))
                }
                val m = listOf(
                    "Receipts Count" to "${uiState.payments.size}",
                    "Total Collected" to BusinessCalculations.formatCurrency(uiState.payments.sumOf { it.amount }, cur)
                )
                Triple(h, r, m)
            }
            "Profit Report" -> {
                val h = listOf("Order #", "Date", "Dealer", "Bags", "Net Sale", "Est. Profit")
                val r = filteredOrders.map {
                    val o = it.order
                    listOf(
                        o.orderNumber,
                        o.date,
                        o.dealerName,
                        "${o.totalBags}",
                        BusinessCalculations.formatCurrency(o.netAmount, cur),
                        BusinessCalculations.formatCurrency(o.estimatedProfit, cur)
                    )
                }
                val m = listOf(
                    "Total Revenue" to BusinessCalculations.formatCurrency(filteredOrders.sumOf { it.order.netAmount }, cur),
                    "Gross Profit" to BusinessCalculations.formatCurrency(filteredOrders.sumOf { it.order.estimatedProfit }, cur)
                )
                Triple(h, r, m)
            }
            "Stock Report" -> {
                val h = listOf("Product", "Opening", "Stock In", "Stock Out", "Current Bags", "Current MT")
                val r = uiState.productStockStatuses.map {
                    listOf(
                        it.product.code,
                        "${it.openingBags}",
                        "+${it.stockInBags}",
                        "-${it.stockOutBags}",
                        "${it.currentBags} Bags",
                        "${BusinessCalculations.formatDecimal(it.currentMt)} MT"
                    )
                }
                val m = listOf(
                    "Total Current Stock" to "${uiState.productStockStatuses.sumOf { it.currentBags }} Bags",
                    "Low Stock Products" to "${uiState.productStockStatuses.count { it.isLowStock }}"
                )
                Triple(h, r, m)
            }
            "Feed Trial Report" -> {
                val h = listOf("Trial #", "Farm / Farmer", "Feed", "Start Avg", "Current Avg", "Gain %")
                val r = uiState.trialsWithMilk.map { twm ->
                    val a = BusinessCalculations.computeTrialAnalytics(twm)
                    listOf(
                        twm.trial.trialCode,
                        twm.trial.farmName,
                        twm.trial.trialFeedCode,
                        "${BusinessCalculations.formatDecimal(a.startingAvgMilk, 1)}L",
                        "${BusinessCalculations.formatDecimal(a.currentAvgMilk, 1)}L",
                        "${BusinessCalculations.formatDecimal(a.percentageChange, 1)}%"
                    )
                }
                val m = listOf("Total Trials" to "${uiState.trialsWithMilk.size}")
                Triple(h, r, m)
            }
            "Visit Report" -> {
                val h = listOf("Visit #", "Date", "Partner", "Type", "Potential", "Booked")
                val r = uiState.visits.map {
                    listOf(it.visitCode, it.date, it.partnerName, it.visitType, "${it.potentialOrderBags} Bags", "${it.orderReceivedBags} Bags")
                }
                val m = listOf(
                    "Total Visits" to "${uiState.visits.size}",
                    "Orders Booked on Visits" to "${uiState.visits.sumOf { it.orderReceivedBags }} Bags"
                )
                Triple(h, r, m)
            }
            "Complaint Report" -> {
                val h = listOf("ID", "Date", "Partner", "Product", "Type", "Status")
                val r = uiState.complaints.map {
                    listOf(it.complaintCode, it.date, it.partnerName, it.productName, it.complaintType, it.status)
                }
                val m = listOf("Total Complaints" to "${uiState.complaints.size}")
                Triple(h, r, m)
            }
            "Expense Report" -> {
                val h = listOf("Date", "Category", "Location", "Method", "Description", "Amount")
                val r = uiState.expenses.map {
                    listOf(it.date, it.category, it.location, it.paymentMethod, it.description, BusinessCalculations.formatCurrency(it.amount, cur))
                }
                val m = listOf("Total Expenses" to BusinessCalculations.formatCurrency(uiState.expenses.sumOf { it.amount }, cur))
                Triple(h, r, m)
            }
            "Travel Report" -> {
                val h = listOf("Date", "Route", "Start Odo", "End Odo", "Total KM", "Working KM")
                val r = uiState.travelLogs.map {
                    listOf(
                        it.date,
                        "${it.startingLocation} -> ${it.destination}",
                        "${it.startingOdometer.roundToInt()}",
                        "${it.endingOdometer.roundToInt()}",
                        "${it.totalKm.roundToInt()} KM",
                        "${it.workingKm.roundToInt()} KM"
                    )
                }
                val m = listOf("Total Distance" to "${uiState.travelLogs.sumOf { it.totalKm }.roundToInt()} KM")
                Triple(h, r, m)
            }
            "Target vs Achievement" -> {
                val d = uiState.dashboard
                val h = listOf("Period", "Target Bags", "Actual Bags", "Remaining", "Achievement %")
                val r = listOf(
                    listOf(
                        SampleDataSeeder.currentMonthPrefix(),
                        "${d.monthlyTargetBags} Bags",
                        "${d.monthlyBags} Bags",
                        "${d.remainingTargetBags} Bags",
                        "${BusinessCalculations.formatDecimal(d.achievementPercentBags, 1)}%"
                    )
                )
                val m = listOf(
                    "Target Bags" to "${d.monthlyTargetBags} Bags",
                    "Actual Bags" to "${d.monthlyBags} Bags",
                    "Achievement %" to "${BusinessCalculations.formatDecimal(d.achievementPercentBags, 1)}%"
                )
                Triple(h, r, m)
            }
            else -> {
                // Daily / Weekly / Monthly Sales
                val h = listOf("Order #", "Date", "Dealer", "Bags", "MT", "Net Amount")
                val r = filteredOrders.map { owi ->
                    val o = owi.order
                    listOf(
                        o.orderNumber,
                        o.date,
                        o.dealerName,
                        "${o.totalBags}",
                        BusinessCalculations.formatDecimal(o.totalMt),
                        BusinessCalculations.formatCurrency(o.netAmount, cur)
                    )
                }
                val m = listOf(
                    "Orders Count" to "${filteredOrders.size}",
                    "Total Bags" to "${filteredOrders.sumOf { it.order.totalBags }} Bags",
                    "Total MT" to "${BusinessCalculations.formatDecimal(filteredOrders.sumOf { it.order.totalMt })} MT",
                    "Total Net Sales" to BusinessCalculations.formatCurrency(filteredOrders.sumOf { it.order.netAmount }, cur)
                )
                Triple(h, r, m)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_section"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("18 Business & Field Reports", style = MaterialTheme.typography.titleMedium)
                    DropdownSelectorField(
                        label = "Select Report Type",
                        options = reportTypes,
                        selectedOption = selectedReport,
                        onOptionSelected = { selectedReport = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownSelectorField(
                            label = "Area Filter",
                            options = areas,
                            selectedOption = areaFilter,
                            onOptionSelected = { areaFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownSelectorField(
                            label = "Product Filter",
                            options = productCodes,
                            selectedOption = productFilter,
                            onOptionSelected = { productFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownSelectorField(
                        label = "Dealer Filter",
                        options = dealerNames,
                        selectedOption = dealerFilter,
                        onOptionSelected = { dealerFilter = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("Start Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("End Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val csv = DocumentAndExportManager.exportCsvReport(
                                    context = context,
                                    fileNamePrefix = selectedReport.replace(" ", "_"),
                                    headers = headers,
                                    rows = rows
                                )
                                DocumentAndExportManager.shareFile(context, csv, "text/csv", "Export $selectedReport CSV")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export CSV")
                        }
                        Button(
                            onClick = {
                                val pdf = DocumentAndExportManager.generateSummaryReportPdf(
                                    context = context,
                                    config = uiState.config,
                                    reportTitle = selectedReport,
                                    periodSubtitle = "Area: $areaFilter | Dealer: $dealerFilter | Product: $productFilter",
                                    summaryMetrics = summaryMetrics,
                                    tableHeaders = headers,
                                    tableRows = rows
                                )
                                DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Share $selectedReport PDF")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF & Share")
                        }
                    }
                }
            }
        }

        // Summary KPI Box
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$selectedReport Summary", style = MaterialTheme.typography.titleSmall)
                    summaryMetrics.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodyMedium)
                            Text(v, style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Report Rows
        items(rows) { row ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.getOrNull(0) ?: "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            row.lastOrNull() ?: "",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (row.size > 2) {
                        Text(
                            row.subList(1, row.size - 1).joinToString(" • ") {
                                it
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetsSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val dash = uiState.dashboard
    val cur = uiState.config.currencySymbol
    var showTargetDialog by remember { mutableStateOf(false) }

    val salesAchPct = if (dash.monthlyTargetSales > 0) (dash.monthlySales / dash.monthlyTargetSales) * 100.0 else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly & Weekly Sales Targets", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showTargetDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set Target")
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current Month Target vs Achievement", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bags Target: ${dash.monthlyTargetBags} Bags (${BusinessCalculations.formatDecimal(dash.monthlyTargetBags * 40.0 / 1000.0)} MT) • Actual: ${dash.monthlyBags} Bags",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { (dash.achievementPercentBags.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Achievement: ${BusinessCalculations.formatDecimal(dash.achievementPercentBags, 1)}%", style = MaterialTheme.typography.titleSmall, color = StatusSuccess)
                        Text("Remaining: ${dash.remainingTargetBags} Bags", style = MaterialTheme.typography.titleSmall, color = StatusWarning)
                    }
                    HorizontalDivider()
                    Text(
                        "Sales Value Target: ${BusinessCalculations.formatCurrency(dash.monthlyTargetSales, cur)} • Actual: ${BusinessCalculations.formatCurrency(dash.monthlySales, cur)} (${BusinessCalculations.formatDecimal(salesAchPct, 1)}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(uiState.targets, key = { it.id }) { tgt ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${tgt.periodLabel} (${tgt.periodType})", style = MaterialTheme.typography.titleSmall)
                        Text("${tgt.targetBags} Bags (${tgt.targetMt} MT)", style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily))
                    }
                    Text(
                        "Target Sales: ${BusinessCalculations.formatCurrency(tgt.targetSalesValue, cur)} • Target Collection: ${BusinessCalculations.formatCurrency(tgt.targetCollection, cur)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (tgt.notes.isNotBlank()) {
                        Text(tgt.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showTargetDialog) {
        val current = uiState.targets.firstOrNull()
        var periodLabel by remember { mutableStateOf(current?.periodLabel ?: SampleDataSeeder.currentMonthPrefix()) }
        var periodType by remember { mutableStateOf(current?.periodType ?: "Monthly") }
        var bagsStr by remember { mutableStateOf((current?.targetBags ?: 1000).toString()) }
        var salesValStr by remember { mutableStateOf((current?.targetSalesValue?.roundToInt() ?: 3600000).toString()) }
        var collValStr by remember { mutableStateOf((current?.targetCollection?.roundToInt() ?: 3000000).toString()) }
        var notes by remember { mutableStateOf(current?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("Configure Sales Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = periodLabel,
                            onValueChange = { periodLabel = it },
                            label = { Text("Period (e.g. 2026-11)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        DropdownSelectorField(
                            label = "Type",
                            options = listOf("Monthly", "Weekly"),
                            selectedOption = periodType,
                            onOptionSelected = { periodType = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = bagsStr,
                        onValueChange = { bagsStr = it },
                        label = { Text("Target Bags *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salesValStr,
                        onValueChange = { salesValStr = it },
                        label = { Text("Target Sales Value ($cur)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = collValStr,
                        onValueChange = { collValStr = it },
                        label = { Text("Target Collection ($cur)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b = (bagsStr.toIntOrNull() ?: 1000).coerceAtLeast(1)
                        viewModel.saveTarget(
                            SalesTargetEntity(
                                id = current?.id ?: 0,
                                periodLabel = periodLabel.trim(),
                                periodType = periodType,
                                targetBags = b,
                                targetMt = (b * 40.0) / 1000.0,
                                targetSalesValue = salesValStr.toDoubleOrNull() ?: 3600000.0,
                                targetCollection = collValStr.toDoubleOrNull() ?: 3000000.0,
                                notes = notes.trim()
                            )
                        )
                        showTargetDialog = false
                    }
                ) {
                    Text("Save Target")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTargetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotificationsAndRemindersSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val cfg = uiState.config
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Smart Field Reminders", style = MaterialTheme.typography.titleMedium)
                            Text("Customize which alerts appear on your dashboard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = cfg.notificationsEnabled,
                            onCheckedChange = { viewModel.saveBusinessConfig(cfg.copy(notificationsEnabled = it)) }
                        )
                    }
                    if (cfg.notificationsEnabled) {
                        HorizontalDivider()
                        ReminderToggleRow("Payment Due & Credit Limit Alerts", cfg.notifyPaymentDue) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyPaymentDue = it))
                        }
                        ReminderToggleRow("Visit & Complaint Follow-up Due", cfg.notifyFollowUpDue) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyFollowUpDue = it))
                        }
                        ReminderToggleRow("Feed Trial Observation Reminders", cfg.notifyTrialFollowUp) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyTrialFollowUp = it))
                        }
                        ReminderToggleRow("Pending Order Deliveries", cfg.notifyPendingDelivery) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyPendingDelivery = it))
                        }
                        ReminderToggleRow("Low Warehouse Stock Warnings", cfg.notifyLowStock) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyLowStock = it))
                        }
                        ReminderToggleRow("Monthly Target Progress Updates", cfg.notifyTargetProgress) {
                            viewModel.saveBusinessConfig(cfg.copy(notifyTargetProgress = it))
                        }
                    }
                }
            }
        }

        item {
            Text("Active Reminders (${uiState.smartReminders.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.smartReminders, key = { it.id }) { rem ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (rem.severity == "HIGH") StatusDanger else StatusWarning
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rem.title, style = MaterialTheme.typography.titleSmall)
                        Text(rem.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingsAndAdminSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val cfg = uiState.config
    var adminTab by remember { mutableStateOf("BUSINESS") }

    // Business Profile state
    var companyName by remember(cfg) { mutableStateOf(cfg.companyName) }
    var businessTitle by remember(cfg) { mutableStateOf(cfg.businessTitle) }
    var contactPhone by remember(cfg) { mutableStateOf(cfg.contactPhone) }
    var companyAddress by remember(cfg) { mutableStateOf(cfg.companyAddress) }
    var currencySymbol by remember(cfg) { mutableStateOf(cfg.currencySymbol) }
    var taxNumber by remember(cfg) { mutableStateOf(cfg.taxRegistrationNumber) }
    var invoicePrefix by remember(cfg) { mutableStateOf(cfg.invoicePrefix) }

    // Dynamic Lookups state
    var selectedLookupCategory by remember { mutableStateOf("AREA") }
    var newLookupName by remember { mutableStateOf("") }

    // Custom Field Builder state
    var customEntityTarget by remember { mutableStateOf("DEALER") }
    var newFieldName by remember { mutableStateOf("") }
    var newFieldType by remember { mutableStateOf("Text") }
    var newFieldDropdownOpts by remember { mutableStateOf("") }
    var newFieldRequired by remember { mutableStateOf(false) }

    // Security state
    var newPinStr by remember(cfg) { mutableStateOf(cfg.securityPin) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_admin_section"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "BUSINESS" to "Business & Appearance",
                    "LOOKUPS" to "Dynamic Lists (Areas/Methods/Types)",
                    "CUSTOM_FIELDS" to "Custom Fields Builder",
                    "SECURITY" to "Security & Roles",
                    "AUDIT" to "Audit History Log"
                ).forEach { (k, label) ->
                    FilterChip(
                        selected = adminTab == k,
                        onClick = { adminTab = k },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (adminTab) {
            "BUSINESS" -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Business Settings & Invoice Branding", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = companyName,
                                onValueChange = { companyName = it },
                                label = { Text("Company Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = businessTitle,
                                onValueChange = { businessTitle = it },
                                label = { Text("Business Tagline / Division") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = contactPhone,
                                    onValueChange = { contactPhone = it },
                                    label = { Text("Contact Phone") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = currencySymbol,
                                    onValueChange = { currencySymbol = it },
                                    label = { Text("Currency Symbol") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = companyAddress,
                                onValueChange = { companyAddress = it },
                                label = { Text("Company Address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = taxNumber,
                                    onValueChange = { taxNumber = it },
                                    label = { Text("Tax / NTN #") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = invoicePrefix,
                                    onValueChange = { invoicePrefix = it },
                                    label = { Text("Invoice Prefix") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.saveBusinessConfig(
                                        cfg.copy(
                                            companyName = companyName.trim(),
                                            businessTitle = businessTitle.trim(),
                                            contactPhone = contactPhone.trim(),
                                            companyAddress = companyAddress.trim(),
                                            currencySymbol = currencySymbol.trim().ifBlank { "Rs." },
                                            taxRegistrationNumber = taxNumber.trim(),
                                            invoicePrefix = invoicePrefix.trim()
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Business Settings")
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Appearance (Light / Dark Mode)", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("SYSTEM" to "System", "LIGHT" to "Light Mode", "DARK" to "Dark Mode").forEach { (mode, label) ->
                                    FilterChip(
                                        selected = cfg.themeMode == mode,
                                        onClick = { viewModel.saveBusinessConfig(cfg.copy(themeMode = mode)) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "LOOKUPS" -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Dynamic Configurable Business Lists", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Manage Areas, Payment Methods, Visit Types, Complaint Types, Expense Categories, Dealer Types, and Feed Categories without hard-coding.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DropdownSelectorField(
                                label = "Select Configurable List",
                                options = listOf(
                                    "AREA",
                                    "PAYMENT_METHOD",
                                    "VISIT_TYPE",
                                    "COMPLAINT_TYPE",
                                    "EXPENSE_CATEGORY",
                                    "DEALER_TYPE",
                                    "CATEGORY"
                                ),
                                selectedOption = selectedLookupCategory,
                                onOptionSelected = { selectedLookupCategory = it }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newLookupName,
                                    onValueChange = { newLookupName = it },
                                    label = { Text("Add New $selectedLookupCategory Option") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newLookupName.isNotBlank()) {
                                            viewModel.saveLookup(
                                                LookupItemEntity(
                                                    categoryType = selectedLookupCategory,
                                                    name = newLookupName.trim()
                                                )
                                            )
                                            newLookupName = ""
                                        }
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                val currentItems = uiState.lookups.filter { it.categoryType == selectedLookupCategory && !it.isArchived }
                items(currentItems, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { viewModel.saveLookup(item.copy(isArchived = true)) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Archive Option", tint = StatusDanger)
                            }
                        }
                    }
                }
            }

            "CUSTOM_FIELDS" -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Custom Fields Builder (Section 25)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Create custom fields for Dealers (e.g. Monthly Potential Bags), Farmers (e.g. Breed), or Feed Trials (e.g. Special Observation).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropdownSelectorField(
                                    label = "Module",
                                    options = listOf("DEALER", "FARMER", "TRIAL"),
                                    selectedOption = customEntityTarget,
                                    onOptionSelected = { customEntityTarget = it },
                                    modifier = Modifier.weight(1f)
                                )
                                DropdownSelectorField(
                                    label = "Field Type",
                                    options = listOf("Text", "Number", "Decimal", "Date", "Dropdown", "Checkbox", "Phone", "Long Text"),
                                    selectedOption = newFieldType,
                                    onOptionSelected = { newFieldType = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = newFieldName,
                                onValueChange = { newFieldName = it },
                                label = { Text("Custom Field Name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (newFieldType == "Dropdown") {
                                OutlinedTextField(
                                    value = newFieldDropdownOpts,
                                    onValueChange = { newFieldDropdownOpts = it },
                                    label = { Text("Dropdown Options (comma-separated)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = newFieldRequired, onCheckedChange = { newFieldRequired = it })
                                Text("Required Field")
                            }
                            Button(
                                onClick = {
                                    if (newFieldName.isNotBlank()) {
                                        viewModel.saveCustomFieldDef(
                                            CustomFieldDefEntity(
                                                entityType = customEntityTarget,
                                                fieldName = newFieldName.trim(),
                                                fieldType = newFieldType,
                                                dropdownOptions = newFieldDropdownOpts.trim(),
                                                isRequired = newFieldRequired,
                                                isVisible = true
                                            )
                                        )
                                        newFieldName = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create Custom Field")
                            }
                        }
                    }
                }

                items(uiState.customFieldDefs.filter { !it.isArchived }, key = { "cf-${it.id}" }) { def ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${def.entityType} • ${def.fieldName}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Type: ${def.fieldType} • ${if (def.isRequired) "Required" else "Optional"} • ${if (def.isVisible) "Visible" else "Hidden"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { viewModel.saveCustomFieldDef(def.copy(isVisible = !def.isVisible)) }) {
                                    Text(if (def.isVisible) "Hide" else "Show")
                                }
                                IconButton(onClick = { viewModel.saveCustomFieldDef(def.copy(isArchived = true)) }) {
                                    Icon(Icons.Default.Archive, contentDescription = "Archive Field", tint = StatusDanger)
                                }
                            }
                        }
                    }
                }
            }

            "SECURITY" -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Security, PIN Protection & Role Access", style = MaterialTheme.typography.titleMedium)
                            DropdownSelectorField(
                                label = "Active User Role",
                                options = listOf("Administrator", "Regional Sales Manager", "Field Sales Officer"),
                                selectedOption = cfg.activeRole,
                                onOptionSelected = { viewModel.saveBusinessConfig(cfg.copy(activeRole = it)) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Require Security PIN on Startup", style = MaterialTheme.typography.titleSmall)
                                    Text("Protects sensitive ledgers, prices, and admin settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = cfg.pinLockEnabled,
                                    onCheckedChange = { viewModel.saveBusinessConfig(cfg.copy(pinLockEnabled = it)) }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newPinStr,
                                    onValueChange = { newPinStr = it },
                                    label = { Text("Security PIN Code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newPinStr.length >= 4) {
                                            viewModel.saveBusinessConfig(cfg.copy(securityPin = newPinStr.trim()))
                                        }
                                    }
                                ) {
                                    Text("Update PIN")
                                }
                            }
                            if (cfg.pinLockEnabled) {
                                OutlinedButton(
                                    onClick = { viewModel.lockAppSession() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lock App Session Now")
                                }
                            }
                        }
                    }
                }
            }

            "AUDIT" -> {
                item {
                    Text("Financial & Admin Audit Trail (${uiState.auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.auditLogs, key = { "aud-${it.id}" }) { log ->
                    val dt = remember(log.timestamp) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(log.timestamp))
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${log.module} • ${log.actionType} (${log.recordCode})", style = MaterialTheme.typography.titleSmall)
                                Text(dt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.summary, style = MaterialTheme.typography.bodySmall)
                            Text("Actor: ${log.actorRole}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupAndDemoDataSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val context = LocalContext.current
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showClearSampleConfirm by remember { mutableStateOf(false) }
    var showReloadSampleConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("backup_restore_section"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data Backup & Restore (Section 27)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Last Manual Backup: ${uiState.config.lastBackupAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Creates a complete JSON snapshot of all Products, Bag Sizes, Dealers, Farmers, Orders, Order Items, Stock Movements, and Payments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.performManualBackup(context, shareAfterSave = false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Backup")
                        }
                        Button(
                            onClick = { viewModel.performManualBackup(context, shareAfterSave = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusInfo),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Data")
                        }
                    }
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Latest Backup (Requires Confirmation)")
                    }
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sample Data / Demo Mode Control (Section 42)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Status: ${if (uiState.config.isSampleDataLoaded) "Sample Field Data Active" else "Clean Fresh Workspace"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (uiState.config.isSampleDataLoaded) StatusSuccess else StatusInfo
                    )
                    Text(
                        "Clearly separate demo/sample data from real user records. You can clear all sample transactions to start fresh while keeping your configurable feed products (26P, 28P, 26M, 55M, CP Mamni) and bag sizes (20 KG, 40 KG).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showReloadSampleConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load Sample Data")
                        }
                        Button(
                            onClick = { showClearSampleConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Fresh (Clear)")
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        ConfirmDestructiveDialog(
            title = "Restore Database from Backup?",
            message = "Restoring from backup will replace current dealers, farmers, orders, stock, and payments with the saved backup records. Are you sure you want to proceed?",
            confirmText = "Restore Backup",
            onConfirm = { viewModel.restoreBackup(context) },
            onDismiss = { showRestoreConfirm = false }
        )
    }

    if (showClearSampleConfirm) {
        ConfirmDestructiveDialog(
            title = "Clear Sample Data & Start Fresh?",
            message = "This will remove all sample orders, payments, dealers, farmers, visits, trials, and expenses so you can enter real business records from scratch.",
            confirmText = "Clear & Start Fresh",
            onConfirm = { viewModel.clearSampleDataStartFresh() },
            onDismiss = { showClearSampleConfirm = false }
        )
    }

    if (showReloadSampleConfirm) {
        ConfirmDestructiveDialog(
            title = "Reload Realistic Sample Data?",
            message = "This will reset transactional records and load the complete sample dataset for testing all 18 modules.",
            confirmText = "Load Sample Data",
            onConfirm = { viewModel.reloadSampleData() },
            onDismiss = { showReloadSampleConfirm = false }
        )
    }
}

@Composable
private fun GlobalSearchSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val cur = uiState.config.currencySymbol

    val matchedDealers = remember(uiState.dealers, q) {
        if (q.isEmpty()) emptyList()
        else uiState.dealers.filter {
            it.name.lowercase().contains(q) ||
                it.businessName.lowercase().contains(q) ||
                it.mobile.contains(q) ||
                it.code.lowercase().contains(q) ||
                it.city.lowercase().contains(q)
        }
    }

    val matchedFarmers = remember(uiState.farmers, q) {
        if (q.isEmpty()) emptyList()
        else uiState.farmers.filter {
            it.name.lowercase().contains(q) ||
                it.farmName.lowercase().contains(q) ||
                it.mobile.contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    val matchedOrders = remember(uiState.ordersWithItems, q) {
        if (q.isEmpty()) emptyList()
        else uiState.ordersWithItems.filter { owi ->
            val o = owi.order
            o.orderNumber.lowercase().contains(q) ||
                o.invoiceNumber.lowercase().contains(q) ||
                o.dealerName.lowercase().contains(q) ||
                o.date.contains(q) ||
                owi.items.any { it.productCode.lowercase().contains(q) || it.productName.lowercase().contains(q) }
        }
    }

    val matchedTrials = remember(uiState.trialsWithMilk, q) {
        if (q.isEmpty()) emptyList()
        else uiState.trialsWithMilk.filter {
            it.trial.trialCode.lowercase().contains(q) ||
                it.trial.farmName.lowercase().contains(q) ||
                it.trial.farmerName.lowercase().contains(q) ||
                it.trial.trialFeedCode.lowercase().contains(q) ||
                it.trial.startDate.contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_search_section"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Global Search: Dealer, Farmer, Mobile, Product (26P), Order #, Invoice #, Trial ID, Date...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_search_input")
            )
        }

        if (q.isEmpty()) {
            item {
                Text(
                    "Type above to search across Dealers, Farmers, Mobile Numbers, Products, Order IDs, Invoice Numbers, Trial IDs, and Dates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Text(
                    "Found: ${matchedDealers.size} Dealers • ${matchedFarmers.size} Farmers • ${matchedOrders.size} Orders/Invoices • ${matchedTrials.size} Feed Trials",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(matchedDealers, key = { "gs-d-${it.id}" }) { d ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.DEALERS)
                            viewModel.openDealerDetail(d.id)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Dealer: ${d.businessName} (${d.code})", style = MaterialTheme.typography.titleSmall)
                        Text("${d.name} • ${d.mobile} • ${d.city} (${d.area})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(matchedFarmers, key = { "gs-f-${it.id}" }) { f ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.FARMERS)
                            viewModel.openFarmerDetail(f.id)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Farmer: ${f.farmName} (${f.code})", style = MaterialTheme.typography.titleSmall)
                        Text("${f.name} • ${f.mobile} • ${f.animalCount} Animals", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(matchedOrders, key = { "gs-o-${it.order.id}" }) { owi ->
                val o = owi.order
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.ORDERS)
                            viewModel.openInvoicePreview(o.id)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Order ${o.orderNumber} / Invoice ${o.invoiceNumber} (${o.date})", style = MaterialTheme.typography.titleSmall)
                        Text("${o.dealerName} • ${o.totalBags} Bags • ${BusinessCalculations.formatCurrency(o.netAmount, cur)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(matchedTrials, key = { "gs-t-${it.trial.id}" }) { twm ->
                val t = twm.trial
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.TRIALS)
                            viewModel.openTrialDetail(t.id)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Feed Trial ${t.trialCode} (${t.trialFeedCode})", style = MaterialTheme.typography.titleSmall)
                        Text("${t.farmName} (${t.farmerName}) • Status: ${t.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
