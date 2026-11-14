package com.example.ui

import androidx.compose.foundation.background
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
import kotlin.math.roundToInt

@Composable
fun SalesAndStockScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    triggerNewSale: Boolean = false,
    triggerNewPayment: Boolean = false,
    triggerStockIn: Boolean = false,
    onConsumeTrigger: () -> Unit = {}
) {
    var showOrderDialog by remember { mutableStateOf(false) }
    var editingOrderWithItems by remember { mutableStateOf<OrderWithItems?>(null) }

    var showStockInDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    LaunchedEffect(triggerNewSale, triggerNewPayment, triggerStockIn) {
        if (triggerNewSale) {
            editingOrderWithItems = null
            showOrderDialog = true
            onConsumeTrigger()
        }
        if (triggerNewPayment) {
            showPaymentDialog = true
            onConsumeTrigger()
        }
        if (triggerStockIn) {
            showStockInDialog = true
            onConsumeTrigger()
        }
    }

    // If Invoice Preview is open, render full Invoice Preview Screen
    val invoiceOrder = uiState.ordersWithItems.firstOrNull { it.order.id == uiState.selectedOrderForInvoice }
    if (invoiceOrder != null) {
        InvoicePreviewDetailScreen(
            orderWithItems = invoiceOrder,
            config = uiState.config,
            onBack = { viewModel.openInvoicePreview(null) },
            onEditOrder = {
                viewModel.openInvoicePreview(null)
                editingOrderWithItems = invoiceOrder
                showOrderDialog = true
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SubModulePillBar(
                modules = listOf(
                    SubModule.ORDERS,
                    SubModule.CONSOLIDATED,
                    SubModule.STOCK,
                    SubModule.PAYMENTS,
                    SubModule.INVOICES,
                    SubModule.PRODUCTS
                ),
                selected = uiState.activeSubModule,
                onSelect = { viewModel.navigateToSubModule(it) }
            )

            when (uiState.activeSubModule) {
                SubModule.CONSOLIDATED -> ConsolidatedOrdersSection(uiState)
                SubModule.STOCK -> StockInventorySection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenStockIn = { showStockInDialog = true }
                )
                SubModule.PAYMENTS -> PaymentsAndLedgerSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenNewPayment = { showPaymentDialog = true }
                )
                SubModule.INVOICES -> InvoicesListSection(
                    uiState = uiState,
                    viewModel = viewModel
                )
                SubModule.PRODUCTS -> ProductsAndPricingSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onAddProduct = {
                        editingProduct = null
                        showProductDialog = true
                    },
                    onEditProduct = {
                        editingProduct = it
                        showProductDialog = true
                    }
                )
                else -> OrdersListSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNewOrder = {
                        editingOrderWithItems = null
                        showOrderDialog = true
                    },
                    onEditOrder = {
                        editingOrderWithItems = it
                        showOrderDialog = true
                    }
                )
            }
        }
    }

    if (showOrderDialog) {
        MultiProductOrderDialog(
            initialOrder = editingOrderWithItems,
            existingOrdersCount = uiState.ordersWithItems.size,
            invoicePrefix = uiState.config.invoicePrefix,
            currencySymbol = uiState.config.currencySymbol,
            dealers = uiState.dealers.filter { !it.isArchived },
            farmers = uiState.farmers.filter { !it.isArchived },
            products = uiState.products.filter { !it.isArchived && it.isActive },
            bagSizes = uiState.bagSizes.filter { !it.isArchived && it.isActive },
            paymentMethods = uiState.lookups.filter { it.categoryType == "PAYMENT_METHOD" && !it.isArchived }.map { it.name },
            onDismiss = { showOrderDialog = false },
            onSave = { order, items, payMethod ->
                viewModel.saveOrder(order, items, payMethod)
                showOrderDialog = false
            }
        )
    }

    if (showStockInDialog) {
        StockInFormDialog(
            products = uiState.products.filter { !it.isArchived },
            bagSizes = uiState.bagSizes.filter { !it.isArchived && it.isActive },
            onDismiss = { showStockInDialog = false },
            onSave = { movement ->
                viewModel.saveStockMovement(movement)
                showStockInDialog = false
            }
        )
    }

    if (showPaymentDialog) {
        PaymentEntryFormDialog(
            existingCount = uiState.payments.size,
            currencySymbol = uiState.config.currencySymbol,
            dealers = uiState.dealers.filter { !it.isArchived },
            paymentMethods = uiState.lookups.filter { it.categoryType == "PAYMENT_METHOD" && !it.isArchived }.map { it.name },
            onDismiss = { showPaymentDialog = false },
            onSave = { payment ->
                viewModel.savePayment(payment)
                showPaymentDialog = false
            }
        )
    }

    if (showProductDialog) {
        ProductFormDialog(
            initialProduct = editingProduct,
            categories = uiState.lookups.filter { it.categoryType == "CATEGORY" && !it.isArchived }.map { it.name },
            bagSizes = uiState.bagSizes.filter { !it.isArchived },
            onDismiss = { showProductDialog = false },
            onSave = { prod, remarks ->
                viewModel.saveProduct(prod, remarks)
                showProductDialog = false
            }
        )
    }
}

@Composable
private fun OrdersListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onNewOrder: () -> Unit,
    onEditOrder: (OrderWithItems) -> Unit
) {
    val context = LocalContext.current
    val cur = uiState.config.currencySymbol
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var paymentFilter by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Newest") }
    var cancellingOrder by remember { mutableStateOf<SaleOrderEntity?>(null) }

    val filteredOrders = remember(uiState.ordersWithItems, searchQuery, statusFilter, paymentFilter, sortBy) {
        val base = uiState.ordersWithItems.filter { owi ->
            val o = owi.order
            val pStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)
            val matchStatus = statusFilter == "All" || o.status.equals(statusFilter, true)
            val matchPayment = paymentFilter == "All" || pStatus.equals(paymentFilter, true)
            val q = searchQuery.trim().lowercase()
            val matchQuery = q.isEmpty() ||
                o.orderNumber.lowercase().contains(q) ||
                o.invoiceNumber.lowercase().contains(q) ||
                o.dealerName.lowercase().contains(q) ||
                o.date.lowercase().contains(q) ||
                o.location.lowercase().contains(q) ||
                owi.items.any { it.productCode.lowercase().contains(q) || it.productName.lowercase().contains(q) }
            matchStatus && matchPayment && matchQuery
        }
        when (sortBy) {
            "Oldest" -> base.sortedBy { it.order.date }
            "Highest Amount" -> base.sortedByDescending { it.order.netAmount }
            "Highest Bags" -> base.sortedByDescending { it.order.totalBags }
            else -> base.sortedByDescending { it.order.date }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOrder,
                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = "New Order") },
                text = { Text("+ New Order") },
                modifier = Modifier.testTag("new_sale_order_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search order #, date (YYYY-MM-DD), dealer, product (26P, 55M)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DropdownSelectorField(
                        label = "Payment Status",
                        options = listOf("All", "Paid", "Partial", "Unpaid"),
                        selectedOption = paymentFilter,
                        onOptionSelected = { paymentFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Sort Orders",
                        options = listOf("Newest", "Oldest", "Highest Amount", "Highest Bags"),
                        selectedOption = sortBy,
                        onOptionSelected = { sortBy = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Delivered", "Pending", "Confirmed", "In Transit", "Partially Delivered", "Draft", "Cancelled").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) }
                        )
                    }
                }
            }

            if (filteredOrders.isEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("No orders match your search or filter", style = MaterialTheme.typography.titleSmall)
                            Text("Tap '+ New Order' to create a new dairy feed sale order.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(filteredOrders, key = { it.order.id }) { owi ->
                val o = owi.order
                val payStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openInvoicePreview(o.id) }
                        .testTag("order_card_${o.orderNumber}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${o.orderNumber} • ${o.dealerName}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Date: ${o.date} • ${o.location} (${o.area}) • Inv: ${o.invoiceNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(payStatus)
                                StatusBadge(o.status)
                            }
                        }

                        // Multi-product line breakdown showing Product, Quantity, Bag size, Weight, Price, Discount & Amount
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                owi.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${item.productCode} — ${item.quantityBags} bags × ${item.bagSizeLabel} (${item.totalKg.roundToInt()} KG)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            "@ ${item.pricePerBag.roundToInt()}${if (item.discountTotal > 0) " (-${item.discountTotal.roundToInt()})" else ""} = ${BusinessCalculations.formatCurrency(item.netAmount, cur)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Bags & Weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${o.totalBags} Bags (${o.totalKg.roundToInt()} KG = ${BusinessCalculations.formatDecimal(o.totalMt)} MT)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                )
                                if (o.discountAmount > 0) {
                                    Text(
                                        "Discount: ${BusinessCalculations.formatCurrency(o.discountAmount, cur)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusWarning
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Amount / Due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${BusinessCalculations.formatCurrency(o.netAmount, cur)} (Due: ${BusinessCalculations.formatCurrency(o.outstandingAmount, cur)})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = if (o.outstandingAmount > 0) StatusDanger else StatusSuccess
                                )
                                Text(
                                    "Paid: ${BusinessCalculations.formatCurrency(o.paidAmount, cur)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusSuccess
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (o.status != "Cancelled") {
                                TextButton(
                                    onClick = { cancellingOrder = o },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
                                ) {
                                    Text("Delete / Cancel")
                                }
                            }
                            IconButton(
                                onClick = {
                                    val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, uiState.config, owi)
                                    DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Share Order ${o.orderNumber}")
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share Order PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                            OutlinedButton(
                                onClick = { onEditOrder(owi) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("Edit")
                            }
                            Button(onClick = { viewModel.openInvoicePreview(o.id) }) {
                                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Details")
                            }
                        }
                    }
                }
            }
        }
    }

    if (cancellingOrder != null) {
        ConfirmDestructiveDialog(
            title = "Delete / Cancel Order ${cancellingOrder!!.orderNumber}?",
            message = "This will cancel the order, reverse any automatic stock-out deduction, and remove its debit from the dealer's active balance.",
            confirmText = "Confirm Cancel",
            onConfirm = { viewModel.cancelOrder(cancellingOrder!!) },
            onDismiss = { cancellingOrder = null }
        )
    }
}

@Composable
private fun ConsolidatedOrdersSection(uiState: AppUiState) {
    val context = LocalContext.current
    val cur = uiState.config.currencySymbol
    var areaFilter by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf("All") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    val areas = remember(uiState.lookups) {
        listOf("All") + uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name }
    }

    val summary = remember(uiState.ordersWithItems, areaFilter, statusFilter, startDate, endDate) {
        BusinessCalculations.computeConsolidatedOrders(
            orders = uiState.ordersWithItems,
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            areaFilter = areaFilter,
            statusFilter = statusFilter
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("consolidated_orders_section"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Consolidated Dispatch & Loading Sheet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically consolidates orders from multiple dealers and locations by Product and Bag Size.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownSelectorField(
                            label = "Area Territory",
                            options = areas,
                            selectedOption = areaFilter,
                            onOptionSelected = { areaFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownSelectorField(
                            label = "Order Status",
                            options = listOf("All", "Pending", "Confirmed", "In Transit", "Delivered"),
                            selectedOption = statusFilter,
                            onOptionSelected = { statusFilter = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("From (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("To (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Grand Totals Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Consolidated Totals (${summary.matchingOrdersCount} Orders)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total Bags", style = MaterialTheme.typography.labelSmall)
                            Text("${summary.totalBags} Bags", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("Total KG", style = MaterialTheme.typography.labelSmall)
                            Text("${summary.totalKg.roundToInt()} KG", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total MT", style = MaterialTheme.typography.labelSmall)
                            Text("${BusinessCalculations.formatDecimal(summary.totalMt)} MT", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total Value: ${BusinessCalculations.formatCurrency(summary.totalValue, cur)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val csv = DocumentAndExportManager.exportCsvReport(
                                        context = context,
                                        fileNamePrefix = "Consolidated_Order",
                                        headers = listOf("Product", "Bag Size", "Total Bags", "Total KG", "Total MT", "Total Value"),
                                        rows = summary.lines.map {
                                            listOf(
                                                "${it.productCode} - ${it.productName}",
                                                it.bagSizeLabel,
                                                "${it.totalBags}",
                                                "${it.totalKg.roundToInt()}",
                                                BusinessCalculations.formatDecimal(it.totalMt),
                                                "${it.totalValue.roundToInt()}"
                                            )
                                        }
                                    )
                                    DocumentAndExportManager.shareFile(context, csv, "text/csv", "Share Consolidated Order CSV")
                                }
                            ) {
                                Text("CSV")
                            }
                            Button(
                                onClick = {
                                    val pdf = DocumentAndExportManager.generateSummaryReportPdf(
                                        context = context,
                                        config = uiState.config,
                                        reportTitle = "Consolidated Mill Dispatch Order",
                                        periodSubtitle = "Area: $areaFilter | Status: $statusFilter | Orders: ${summary.matchingOrdersCount}",
                                        summaryMetrics = listOf(
                                            "Total Bags" to "${summary.totalBags} Bags",
                                            "Total Weight (KG)" to "${summary.totalKg.roundToInt()} KG",
                                            "Total Metric Tons" to "${BusinessCalculations.formatDecimal(summary.totalMt)} MT",
                                            "Total Order Value" to BusinessCalculations.formatCurrency(summary.totalValue, cur)
                                        ),
                                        tableHeaders = listOf("Product", "Bag Size", "Total Bags", "Total Weight", "Value"),
                                        tableRows = summary.lines.map {
                                            listOf(
                                                it.productCode,
                                                it.bagSizeLabel,
                                                "${it.totalBags} Bags",
                                                "${it.totalKg.roundToInt()} KG (${BusinessCalculations.formatDecimal(it.totalMt)} MT)",
                                                BusinessCalculations.formatCurrency(it.totalValue, cur)
                                            )
                                        }
                                    )
                                    DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Share Consolidated Order PDF")
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF")
                            }
                        }
                    }
                }
            }
        }

        // Table Rows: Product | Bag Size | Total Bags | Total Weight
        items(summary.lines, key = { "${it.productId}-${it.bagSizeLabel}" }) { line ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${line.productCode} — ${line.productName}", style = MaterialTheme.typography.titleSmall)
                        Text("Bag Size: ${line.bagSizeLabel} • Across ${line.orderCount} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${line.totalBags} Bags",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${line.totalKg.roundToInt()} KG (${BusinessCalculations.formatDecimal(line.totalMt)} MT)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        Text(
                            BusinessCalculations.formatCurrency(line.totalValue, cur),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = StatusSuccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockInventorySection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onOpenStockIn: () -> Unit
) {
    val cur = uiState.config.currencySymbol
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenStockIn,
                icon = { Icon(Icons.Default.AddBox, contentDescription = "Stock In") },
                text = { Text("Record Stock In") },
                modifier = Modifier.testTag("record_stock_in_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Warehouse Inventory (Opening + Stock In - Stock Out = Current Stock)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(uiState.productStockStatuses, key = { it.product.id }) { st ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${st.product.code} — ${st.product.name}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Bag Weight: ${st.product.defaultBagWeightKg.roundToInt()} KG • Min Stock Level: ${st.product.minStockBags} Bags",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (st.isLowStock) {
                                StatusBadge("Low Stock")
                            } else {
                                StatusBadge("Active")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Opening", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${st.openingBags}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily))
                            }
                            Column {
                                Text("+ Stock In", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                                Text("+${st.stockInBags}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily), color = StatusSuccess)
                            }
                            Column {
                                Text("- Stock Out", style = MaterialTheme.typography.labelSmall, color = StatusDanger)
                                Text("-${st.stockOutBags}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily), color = StatusDanger)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Current Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "${st.currentBags} Bags (${BusinessCalculations.formatDecimal(st.currentMt)} MT)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = if (st.isLowStock) StatusDanger else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            "Stock Valuation — Cost: ${BusinessCalculations.formatCurrency(st.stockValueAtCost, cur)} • Sale Value: ${BusinessCalculations.formatCurrency(st.stockValueAtSale, cur)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Recent Stock Movements (Stock In & Auto Stock Out)", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.stockMovements.take(30), key = { it.id }) { mov ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${mov.date} • ${mov.productCode} (${mov.bagSizeLabel})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${mov.supplierName} • Ref: ${mov.invoiceNumber.ifBlank { "N/A" }} • ${mov.remarks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${if (mov.type == "STOCK_OUT") "-" else "+"}${mov.quantityBags} Bags",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = if (mov.type == "STOCK_OUT") StatusDanger else StatusSuccess
                            )
                            Text("${mov.totalKg.roundToInt()} KG", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsAndLedgerSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onOpenNewPayment: () -> Unit
) {
    val cur = uiState.config.currencySymbol
    var deletingPayment by remember { mutableStateOf<PaymentEntity?>(null) }

    val activeDealerSummaries = remember(uiState.dealerSummaries) {
        uiState.dealerSummaries.filter { !it.dealer.isArchived }
    }
    val totalSalesAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.totalSales } }
    val totalPaidAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.totalPayments } }
    val totalOutstandingAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.outstandingBalance.coerceAtLeast(0.0) } }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenNewPayment,
                icon = { Icon(Icons.Default.Payments, contentDescription = "Add Payment") },
                text = { Text("+ Add Payment") },
                modifier = Modifier.testTag("record_payment_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Overall Dealer Ledger Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Sales", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    BusinessCalculations.formatCurrency(totalSalesAll, cur),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                )
                            }
                            Column {
                                Text("Total Paid", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    BusinessCalculations.formatCurrency(totalPaidAll, cur),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = StatusSuccess
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    BusinessCalculations.formatCurrency(totalOutstandingAll, cur),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = if (totalOutstandingAll > 0) StatusDanger else StatusSuccess
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Dealer Ledger Balances (Tap any dealer for complete ledger)", style = MaterialTheme.typography.titleMedium)
            }
            items(activeDealerSummaries, key = { "dlr-bal-${it.dealer.id}" }) { ds ->
                val dealerPaymentStatus = BusinessCalculations.getDealerPaymentStatus(
                    totalSales = ds.totalSales,
                    totalPayments = ds.totalPayments,
                    outstandingBalance = ds.outstandingBalance
                )
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.DEALERS)
                            viewModel.openDealerDetail(ds.dealer.id)
                        },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ds.dealer.businessName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            StatusBadge(dealerPaymentStatus)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Total Sales: ${BusinessCalculations.formatCurrency(ds.totalSales, cur)} • Total Paid: ${BusinessCalculations.formatCurrency(ds.totalPayments, cur)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Opening Balance: ${BusinessCalculations.formatCurrency(ds.dealer.openingBalance, cur)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    BusinessCalculations.formatCurrency(ds.outstandingBalance, cur),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = if (ds.outstandingBalance > 0) StatusDanger else StatusSuccess
                                )
                                Text("View Ledger ->", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Payment Receipts History (${uiState.payments.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.payments, key = { it.id }) { pay ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${pay.paymentNumber} • ${pay.dealerName}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${pay.date} • ${pay.paymentMethod} ${if (pay.referenceNumber.isNotBlank()) "• Ref: ${pay.referenceNumber}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pay.remarks.isNotBlank()) {
                                Text(pay.remarks, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                BusinessCalculations.formatCurrency(pay.amount, cur),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = StatusSuccess
                            )
                            IconButton(onClick = { deletingPayment = pay }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Payment", tint = StatusDanger)
                            }
                        }
                    }
                }
            }
        }
    }

    if (deletingPayment != null) {
        ConfirmDestructiveDialog(
            title = "Delete Payment ${deletingPayment!!.paymentNumber}?",
            message = "Deleting this payment receipt will recalculate ${deletingPayment!!.dealerName}'s ledger balance. Please confirm.",
            confirmText = "Delete Payment",
            onConfirm = { viewModel.deletePayment(deletingPayment!!) },
            onDismiss = { deletingPayment = null }
        )
    }
}

@Composable
private fun InvoicesListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val context = LocalContext.current
    val cur = uiState.config.currencySymbol

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Generated Invoices (Tap to Preview, Print, Save PDF or Share)", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.ordersWithItems.filter { it.order.status != "Cancelled" }, key = { "inv-${it.order.id}" }) { owi ->
            val o = owi.order
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openInvoicePreview(o.id) },
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${o.invoiceNumber} (${o.date})", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(o.status)
                    }
                    Text("${o.dealerName} • ${o.location}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bags: ${o.totalBags} (${BusinessCalculations.formatDecimal(o.totalMt)} MT) • Net: ${BusinessCalculations.formatCurrency(o.netAmount, cur)} • Balance: ${BusinessCalculations.formatCurrency(o.outstandingAmount, cur)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, uiState.config, owi)
                                DocumentAndExportManager.printPdfFile(context, pdf, "Invoice_${o.invoiceNumber}")
                            }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { viewModel.openInvoicePreview(o.id) }) {
                            Text("Preview & PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoicePreviewDetailScreen(
    orderWithItems: OrderWithItems,
    config: BusinessConfigEntity,
    onBack: () -> Unit,
    onEditOrder: () -> Unit
) {
    val context = LocalContext.current
    val o = orderWithItems.order
    val cur = config.currencySymbol

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("invoice_preview_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditOrder) {
                        Text("Edit Order")
                    }
                    Button(
                        onClick = {
                            val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, config, orderWithItems)
                            DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Share Invoice ${o.invoiceNumber}")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save / Share PDF")
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(config.companyName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("WandaBook Pro • Dairy Feed Sales & Management", style = MaterialTheme.typography.bodySmall)
                            Text(config.companyAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("INVOICE", style = MaterialTheme.typography.titleLarge)
                            Text(o.invoiceNumber, style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily))
                            Text("Date: ${o.date}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BILL TO DEALER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(o.dealerName, style = MaterialTheme.typography.titleMedium)
                            Text(o.dealerAddress.ifBlank { "${o.location} (${o.area})" }, style = MaterialTheme.typography.bodySmall)
                            if (o.farmerName.isNotBlank()) {
                                Text("Farmer: ${o.farmerName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StatusBadge(o.status)
                    }

                    HorizontalDivider()

                    orderWithItems.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.productCode} — ${item.productName}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${item.quantityBags} Bags × ${item.bagSizeLabel} (${item.totalKg.roundToInt()} KG = ${BusinessCalculations.formatDecimal(item.totalMt)} MT)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${BusinessCalculations.formatCurrency(item.pricePerBag, cur)} / bag",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                )
                                if (item.discountTotal > 0) {
                                    Text("Disc: -${BusinessCalculations.formatCurrency(item.discountTotal, cur)}", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                                }
                                Text(
                                    BusinessCalculations.formatCurrency(item.netAmount, cur),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Volume:", style = MaterialTheme.typography.bodyMedium)
                        Text("${o.totalBags} Bags (${o.totalKg.roundToInt()} KG / ${BusinessCalculations.formatDecimal(o.totalMt)} MT)", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gross Amount:", style = MaterialTheme.typography.bodyMedium)
                        Text(BusinessCalculations.formatCurrency(o.grossAmount, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Discount:", style = MaterialTheme.typography.bodyMedium)
                        Text("- ${BusinessCalculations.formatCurrency(o.discountAmount, cur)}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Amount:", style = MaterialTheme.typography.titleMedium)
                        Text(BusinessCalculations.formatCurrency(o.netAmount, cur), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid Amount:", style = MaterialTheme.typography.bodyMedium)
                        Text(BusinessCalculations.formatCurrency(o.paidAmount, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily), color = StatusSuccess)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Balance Outstanding:", style = MaterialTheme.typography.titleMedium)
                        Text(BusinessCalculations.formatCurrency(o.outstandingAmount, cur), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = if (o.outstandingAmount > 0) StatusDanger else StatusSuccess)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, config, orderWithItems)
                                DocumentAndExportManager.printPdfFile(context, pdf, "Invoice_${o.invoiceNumber}")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Invoice")
                        }
                        Button(
                            onClick = {
                                val textSummary = buildString {
                                    appendLine("*${config.companyName} — INVOICE ${o.invoiceNumber}*")
                                    appendLine("Date: ${o.date} | Dealer: ${o.dealerName}")
                                    orderWithItems.items.forEach {
                                        appendLine("• ${it.productCode} (${it.bagSizeLabel}): ${it.quantityBags} bags @ ${it.pricePerBag.roundToInt()} = ${BusinessCalculations.formatCurrency(it.netAmount, cur)}")
                                    }
                                    appendLine("Total Bags: ${o.totalBags} (${BusinessCalculations.formatDecimal(o.totalMt)} MT)")
                                    appendLine("Net Payable: ${BusinessCalculations.formatCurrency(o.netAmount, cur)}")
                                    appendLine("Paid: ${BusinessCalculations.formatCurrency(o.paidAmount, cur)} | Balance: ${BusinessCalculations.formatCurrency(o.outstandingAmount, cur)}")
                                }
                                DocumentAndExportManager.shareTextMessage(context, "Invoice ${o.invoiceNumber}", textSummary, preferWhatsApp = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share WhatsApp")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsAndPricingSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onAddProduct: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit
) {
    val cur = uiState.config.currencySymbol
    var showBagSizeDialog by remember { mutableStateOf(false) }
    var archivingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Product") },
                text = { Text("Add Feed Product") },
                modifier = Modifier.testTag("add_product_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bag Sizes Management Card
            item {
                ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Dynamic Bag Sizes (Bags × KG = Total KG / 1000 = MT)", style = MaterialTheme.typography.titleSmall)
                                Text("Initial: 20 KG, 40 KG • Editable by Administrator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { showBagSizeDialog = true }) {
                                Text("+ Bag Size")
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.bagSizes.filter { !it.isArchived }.forEach { bs ->
                                InputChip(
                                    selected = bs.isActive,
                                    onClick = { viewModel.saveBagSize(bs.copy(isActive = !bs.isActive)) },
                                    label = { Text("${bs.label} (${bs.weightKg.roundToInt()} KG)") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Archive Bag Size",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.saveBagSize(bs.copy(isArchived = true)) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Feed Products & Price Management (Historical sales never change when prices update)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(uiState.products.filter { !it.isArchived }, key = { it.id }) { prod ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${prod.code} — ${prod.name}", style = MaterialTheme.typography.titleMedium)
                                Text("${prod.category} • Default Bag: ${prod.defaultBagWeightKg.roundToInt()} KG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(if (prod.isActive) "Active" else "Inactive")
                        }
                        if (prod.description.isNotBlank()) {
                            Text(prod.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Purchase Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(BusinessCalculations.formatCurrency(prod.purchasePrice, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily))
                            }
                            Column {
                                Text("Dealer / Sale Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(BusinessCalculations.formatCurrency(prod.salePrice, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Retail Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(BusinessCalculations.formatCurrency(prod.retailPrice, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Min Stock Alert: ${prod.minStockBags} Bags", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { archivingProduct = prod },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
                                ) {
                                    Text("Archive / Delete")
                                }
                                Button(onClick = { onEditProduct(prod) }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit / Price")
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.priceHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Price Change Audit Log (Old orders preserved at original price)", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.priceHistory.take(15), key = { "ph-${it.id}" }) { ph ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${ph.productCode} (${ph.productName})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Sale Price: ${ph.oldSalePrice.roundToInt()} -> ${ph.newSalePrice.roundToInt()} | Cost: ${ph.oldPurchasePrice.roundToInt()} -> ${ph.newPurchasePrice.roundToInt()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                            if (ph.remarks.isNotBlank()) {
                                Text(ph.remarks, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBagSizeDialog) {
        var weightStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBagSizeDialog = false },
            title = { Text("Add Bag Size") },
            text = {
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Bag Weight in KG (e.g. 25, 50) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = weightStr.toDoubleOrNull() ?: 0.0
                        if (w > 0.0) {
                            viewModel.saveBagSize(
                                BagSizeEntity(
                                    label = "${w.roundToInt()} KG",
                                    weightKg = w,
                                    isActive = true
                                )
                            )
                            showBagSizeDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBagSizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (archivingProduct != null) {
        ConfirmDestructiveDialog(
            title = "Archive / Remove ${archivingProduct!!.code}?",
            message = "If this product was sold in any historical order, it will be safely archived so old sales and invoices remain intact.",
            confirmText = "Confirm",
            onConfirm = { viewModel.deleteOrArchiveProduct(archivingProduct!!) },
            onDismiss = { archivingProduct = null }
        )
    }
}

@Composable
private fun MultiProductOrderDialog(
    initialOrder: OrderWithItems?,
    existingOrdersCount: Int,
    invoicePrefix: String,
    currencySymbol: String,
    dealers: List<DealerEntity>,
    farmers: List<FarmerEntity>,
    products: List<ProductEntity>,
    bagSizes: List<BagSizeEntity>,
    paymentMethods: List<String>,
    onDismiss: () -> Unit,
    onSave: (SaleOrderEntity, List<OrderItemEntity>, String) -> Unit
) {
    val existing = initialOrder?.order
    var orderNumber by remember { mutableStateOf(existing?.orderNumber ?: "ORD-2026-${existingOrdersCount + 101}") }
    var invoiceNumber by remember { mutableStateOf(existing?.invoiceNumber ?: "${invoicePrefix}${existingOrdersCount + 101}") }
    var date by remember { mutableStateOf(existing?.date ?: SampleDataSeeder.todayDate()) }
    var selectedDealerName by remember { mutableStateOf(existing?.dealerName ?: (dealers.firstOrNull()?.businessName ?: "")) }
    var selectedFarmerName by remember { mutableStateOf(existing?.farmerName ?: "None") }
    var location by remember {
        mutableStateOf(
            existing?.location ?: (dealers.firstOrNull()?.city ?: "Sahiwal")
        )
    }
    var status by remember { mutableStateOf(existing?.status ?: "Delivered") }
    var expectedDeliveryDate by remember { mutableStateOf(existing?.expectedDeliveryDate ?: SampleDataSeeder.todayDate()) }
    var paidAmountStr by remember { mutableStateOf(existing?.paidAmount?.roundToInt()?.toString() ?: "0") }
    var paymentMethod by remember { mutableStateOf(paymentMethods.firstOrNull() ?: "Cash") }
    var remarks by remember { mutableStateOf(existing?.remarks ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Draft line items list
    var lineItems by remember {
        mutableStateOf(
            initialOrder?.items ?: listOfNotNull(
                products.firstOrNull()?.let { firstProd ->
                    val bagKg = firstProd.defaultBagWeightKg
                    OrderItemEntity(
                        orderId = 0,
                        productId = firstProd.id,
                        productCode = firstProd.code,
                        productName = firstProd.name,
                        bagSizeLabel = "${bagKg.roundToInt()} KG",
                        bagWeightKg = bagKg,
                        quantityBags = 50,
                        totalKg = 50 * bagKg,
                        totalMt = (50 * bagKg) / 1000.0,
                        purchasePriceSnapshot = firstProd.purchasePrice,
                        pricePerBag = firstProd.salePrice,
                        discountPerBag = 0.0,
                        grossAmount = 50 * firstProd.salePrice,
                        discountTotal = 0.0,
                        netAmount = 50 * firstProd.salePrice
                    )
                }
            )
        )
    }

    // Add Line Item inputs
    var selProdCode by remember { mutableStateOf(products.firstOrNull()?.code ?: "26P") }
    var selBagLabel by remember { mutableStateOf(bagSizes.lastOrNull()?.label ?: "40 KG") }
    var qtyBagsStr by remember { mutableStateOf("25") }
    var customPriceStr by remember(selProdCode) {
        val p = products.firstOrNull { it.code == selProdCode }
        mutableStateOf(p?.salePrice?.roundToInt()?.toString() ?: "3500")
    }
    var discountPerBagStr by remember { mutableStateOf("0") }

    val totalBags = lineItems.sumOf { it.quantityBags }
    val totalKg = lineItems.sumOf { it.totalKg }
    val totalMt = totalKg / 1000.0
    val grossAmount = lineItems.sumOf { it.grossAmount }
    val discountAmount = lineItems.sumOf { it.discountTotal }
    val netAmount = lineItems.sumOf { it.netAmount }
    val paidAmount = paidAmountStr.toDoubleOrNull() ?: 0.0
    val outstandingAmount = (netAmount - paidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Multi-Product Sale Order" else "Edit Order ${existing.orderNumber}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg != null) {
                    Text(errorMsg!!, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = orderNumber,
                        onValueChange = { orderNumber = it },
                        label = { Text("Order ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownSelectorField(
                    label = "Dealer *",
                    options = dealers.map { it.businessName }.ifEmpty { listOf("Walk-in Dealer") },
                    selectedOption = selectedDealerName,
                    onOptionSelected = { dName ->
                        selectedDealerName = dName
                        dealers.firstOrNull { it.businessName == dName }?.let {
                            location = "${it.city} (${it.area})"
                        }
                    }
                )
                DropdownSelectorField(
                    label = "Farmer (Optional)",
                    options = listOf("None") + farmers.map { "${it.name} (${it.farmName})" },
                    selectedOption = selectedFarmerName,
                    onOptionSelected = { selectedFarmerName = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Delivery Location") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Order Status",
                        options = listOf("Delivered", "Pending", "Confirmed", "In Transit", "Partially Delivered", "Draft"),
                        selectedOption = status,
                        onOptionSelected = { status = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()
                Text("Order Products (${lineItems.size} items)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                lineItems.forEachIndexed { idx, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.productCode} — ${item.quantityBags} bags × ${item.bagSizeLabel}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${item.totalKg.roundToInt()} KG (${BusinessCalculations.formatDecimal(item.totalMt)} MT) @ ${item.pricePerBag.roundToInt()} = ${BusinessCalculations.formatCurrency(item.netAmount, currencySymbol)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(
                                onClick = {
                                    lineItems = lineItems.toMutableList().also { it.removeAt(idx) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Line", tint = StatusDanger)
                            }
                        }
                    }
                }

                // Add Product Line Builder Box
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Product to Order", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DropdownSelectorField(
                                label = "Product",
                                options = products.map { it.code },
                                selectedOption = selProdCode,
                                onOptionSelected = { code ->
                                    selProdCode = code
                                    products.firstOrNull { it.code == code }?.let { p ->
                                        customPriceStr = p.salePrice.roundToInt().toString()
                                        selBagLabel = "${p.defaultBagWeightKg.roundToInt()} KG"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DropdownSelectorField(
                                label = "Bag Size",
                                options = bagSizes.map { it.label }.ifEmpty { listOf("20 KG", "40 KG") },
                                selectedOption = selBagLabel,
                                onOptionSelected = { selBagLabel = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = qtyBagsStr,
                                onValueChange = { qtyBagsStr = it },
                                label = { Text("Bags") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customPriceStr,
                                onValueChange = { customPriceStr = it },
                                label = { Text("Price/Bag") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = discountPerBagStr,
                                onValueChange = { discountPerBagStr = it },
                                label = { Text("Disc/Bag") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                val p = products.firstOrNull { it.code == selProdCode } ?: return@Button
                                val bagW = bagSizes.firstOrNull { it.label == selBagLabel }?.weightKg ?: p.defaultBagWeightKg
                                val bags = qtyBagsStr.toIntOrNull() ?: 0
                                val rate = customPriceStr.toDoubleOrNull() ?: p.salePrice
                                val disc = (discountPerBagStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                if (bags <= 0 || rate <= 0.0) {
                                    errorMsg = "Bags and Price per bag must be positive."
                                    return@Button
                                }
                                val kg = bags * bagW
                                val mt = kg / 1000.0
                                val gross = bags * rate
                                val discTot = bags * disc
                                val net = (gross - discTot).coerceAtLeast(0.0)
                                lineItems = lineItems + OrderItemEntity(
                                    orderId = existing?.id ?: 0,
                                    productId = p.id,
                                    productCode = p.code,
                                    productName = p.name,
                                    bagSizeLabel = selBagLabel,
                                    bagWeightKg = bagW,
                                    quantityBags = bags,
                                    totalKg = kg,
                                    totalMt = mt,
                                    purchasePriceSnapshot = p.purchasePrice,
                                    pricePerBag = rate,
                                    discountPerBag = disc,
                                    grossAmount = gross,
                                    discountTotal = discTot,
                                    netAmount = net
                                )
                                errorMsg = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Product Line to Order")
                        }
                    }
                }

                // Automatic Order Summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Total Volume: $totalBags Bags × Bag Weight = ${totalKg.roundToInt()} KG = ${BusinessCalculations.formatDecimal(totalMt)} MT",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        Text(
                            "Gross: ${BusinessCalculations.formatCurrency(grossAmount, currencySymbol)} | Discount: ${BusinessCalculations.formatCurrency(discountAmount, currencySymbol)} | Net: ${BusinessCalculations.formatCurrency(netAmount, currencySymbol)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Outstanding Balance on Order: ${BusinessCalculations.formatCurrency(outstandingAmount, currencySymbol)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (outstandingAmount > 0) StatusDanger else StatusSuccess
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = paidAmountStr,
                        onValueChange = { paidAmountStr = it },
                        label = { Text("Payment Received") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Pay Method",
                        options = paymentMethods.ifEmpty { listOf("Cash", "Bank Transfer", "Online") },
                        selectedOption = paymentMethod,
                        onOptionSelected = { paymentMethod = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = expectedDeliveryDate,
                    onValueChange = { expectedDeliveryDate = it },
                    label = { Text("Expected Delivery Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Order / Dispatch Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (lineItems.isEmpty()) {
                        errorMsg = "Please add at least one product line item to the order."
                        return@Button
                    }
                    val dealerObj = dealers.firstOrNull { it.businessName == selectedDealerName } ?: dealers.firstOrNull()
                    if (dealerObj == null) {
                        errorMsg = "Please select a valid Dealer."
                        return@Button
                    }
                    val farmerObj = farmers.firstOrNull { "${it.name} (${it.farmName})" == selectedFarmerName }
                    val estProfit = lineItems.sumOf {
                        (it.pricePerBag - it.discountPerBag - it.purchasePriceSnapshot) * it.quantityBags
                    }
                    onSave(
                        SaleOrderEntity(
                            id = existing?.id ?: 0,
                            orderNumber = orderNumber.trim(),
                            invoiceNumber = invoiceNumber.trim(),
                            date = date.trim(),
                            dealerId = dealerObj.id,
                            dealerName = dealerObj.businessName,
                            dealerAddress = "${dealerObj.address}, ${dealerObj.city}",
                            farmerId = farmerObj?.id,
                            farmerName = farmerObj?.name ?: "",
                            location = location.trim(),
                            area = dealerObj.area,
                            totalBags = totalBags,
                            totalKg = totalKg,
                            totalMt = totalMt,
                            grossAmount = grossAmount,
                            discountAmount = discountAmount,
                            netAmount = netAmount,
                            paidAmount = paidAmount.coerceAtLeast(0.0),
                            outstandingAmount = outstandingAmount,
                            estimatedProfit = estProfit,
                            status = status,
                            expectedDeliveryDate = expectedDeliveryDate.trim(),
                            remarks = remarks.trim()
                        ),
                        lineItems,
                        paymentMethod
                    )
                }
            ) {
                Text("Save Order")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StockInFormDialog(
    products: List<ProductEntity>,
    bagSizes: List<BagSizeEntity>,
    onDismiss: () -> Unit,
    onSave: (StockMovementEntity) -> Unit
) {
    var date by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
    var selCode by remember { mutableStateOf(products.firstOrNull()?.code ?: "26P") }
    var selBagLabel by remember { mutableStateOf(bagSizes.lastOrNull()?.label ?: "40 KG") }
    var qtyStr by remember { mutableStateOf("100") }
    var costStr by remember(selCode) {
        val p = products.firstOrNull { it.code == selCode }
        mutableStateOf(p?.purchasePrice?.roundToInt()?.toString() ?: "3150")
    }
    var supplier by remember { mutableStateOf("Central Feed Mill Plant #1") }
    var invoiceNo by remember { mutableStateOf("MILL-IN-8901") }
    var remarks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Stock In (Mill / Purchase)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Product",
                    options = products.map { it.code },
                    selectedOption = selCode,
                    onOptionSelected = { selCode = it }
                )
                DropdownSelectorField(
                    label = "Bag Size",
                    options = bagSizes.map { it.label }.ifEmpty { listOf("20 KG", "40 KG") },
                    selectedOption = selBagLabel,
                    onOptionSelected = { selBagLabel = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Quantity (Bags) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Purchase Price/Bag") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier / Feed Mill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Mill Invoice / Gate Pass #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = products.firstOrNull { it.code == selCode } ?: return@Button
                    val qty = qtyStr.toIntOrNull() ?: 0
                    if (qty <= 0) return@Button
                    val bagW = bagSizes.firstOrNull { it.label == selBagLabel }?.weightKg ?: prod.defaultBagWeightKg
                    val kg = qty * bagW
                    onSave(
                        StockMovementEntity(
                            date = date.trim(),
                            type = "STOCK_IN",
                            productId = prod.id,
                            productCode = prod.code,
                            productName = prod.name,
                            bagSizeLabel = selBagLabel,
                            bagWeightKg = bagW,
                            quantityBags = qty,
                            totalKg = kg,
                            totalMt = kg / 1000.0,
                            purchasePricePerBag = costStr.toDoubleOrNull() ?: prod.purchasePrice,
                            supplierName = supplier.trim(),
                            invoiceNumber = invoiceNo.trim(),
                            remarks = remarks.trim()
                        )
                    )
                }
            ) {
                Text("Save Stock In")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PaymentEntryFormDialog(
    existingCount: Int,
    currencySymbol: String,
    dealers: List<DealerEntity>,
    paymentMethods: List<String>,
    onDismiss: () -> Unit,
    onSave: (PaymentEntity) -> Unit
) {
    var payNumber by remember { mutableStateOf("PAY-2026-${existingCount + 501}") }
    var date by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
    var selDealerName by remember { mutableStateOf(dealers.firstOrNull()?.businessName ?: "") }
    var amountStr by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(paymentMethods.firstOrNull() ?: "Cash") }
    var refNo by remember { mutableStateOf("") }
    var bankAccount by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Dealer Payment") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg != null) {
                    Text(errorMsg!!, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = payNumber,
                        onValueChange = { payNumber = it },
                        label = { Text("Payment ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownSelectorField(
                    label = "Dealer *",
                    options = dealers.map { it.businessName },
                    selectedOption = selDealerName,
                    onOptionSelected = { selDealerName = it }
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount ($currencySymbol) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Payment Method",
                    options = paymentMethods.ifEmpty { listOf("Cash", "Bank Transfer", "Online", "Cheque") },
                    selectedOption = method,
                    onOptionSelected = { method = it }
                )
                OutlinedTextField(
                    value = refNo,
                    onValueChange = { refNo = it },
                    label = { Text("Reference / Slip / Cheque Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bankAccount,
                    onValueChange = { bankAccount = it },
                    label = { Text("Bank / Account (if applicable)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    val dealer = dealers.firstOrNull { it.businessName == selDealerName }
                    if (amt <= 0.0 || dealer == null) {
                        errorMsg = "Please select a dealer and enter a positive payment amount."
                        return@Button
                    }
                    onSave(
                        PaymentEntity(
                            paymentNumber = payNumber.trim(),
                            date = date.trim(),
                            dealerId = dealer.id,
                            dealerName = dealer.businessName,
                            amount = amt,
                            paymentMethod = method,
                            referenceNumber = refNo.trim(),
                            bankAccount = bankAccount.trim(),
                            remarks = remarks.trim()
                        )
                    )
                }
            ) {
                Text("Save Payment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProductFormDialog(
    initialProduct: ProductEntity?,
    categories: List<String>,
    bagSizes: List<BagSizeEntity>,
    onDismiss: () -> Unit,
    onSave: (ProductEntity, String) -> Unit
) {
    var code by remember { mutableStateOf(initialProduct?.code ?: "") }
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: (categories.firstOrNull() ?: "High Yield Pelleted Feed")) }
    var description by remember { mutableStateOf(initialProduct?.description ?: "") }
    var bagWeightStr by remember { mutableStateOf(initialProduct?.defaultBagWeightKg?.roundToInt()?.toString() ?: "40") }
    var purchasePriceStr by remember { mutableStateOf(initialProduct?.purchasePrice?.roundToInt()?.toString() ?: "3200") }
    var dealerPriceStr by remember { mutableStateOf(initialProduct?.dealerPrice?.roundToInt()?.toString() ?: "3550") }
    var salePriceStr by remember { mutableStateOf(initialProduct?.salePrice?.roundToInt()?.toString() ?: "3550") }
    var retailPriceStr by remember { mutableStateOf(initialProduct?.retailPrice?.roundToInt()?.toString() ?: "3750") }
    var minStockStr by remember { mutableStateOf(initialProduct?.minStockBags?.toString() ?: "80") }
    var openingStockStr by remember { mutableStateOf(initialProduct?.openingStockBags?.toString() ?: "100") }
    var isActive by remember { mutableStateOf(initialProduct?.isActive ?: true) }
    var priceRemarks by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProduct == null) "Add Feed Product" else "Edit Product ${initialProduct.code}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg != null) {
                    Text(errorMsg!!, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Product Code *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bagWeightStr,
                        onValueChange = { bagWeightStr = it },
                        label = { Text("Bag Weight (KG)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Feed Category",
                    options = categories.ifEmpty { listOf("High Yield Pelleted Feed", "Standard Milking Concentrate", "Calf & Heifer Starter") },
                    selectedOption = category,
                    onOptionSelected = { category = it }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Product Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePriceStr,
                        onValueChange = { purchasePriceStr = it },
                        label = { Text("Purchase Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dealerPriceStr,
                        onValueChange = { dealerPriceStr = it },
                        label = { Text("Dealer Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salePriceStr,
                        onValueChange = { salePriceStr = it },
                        label = { Text("Sale Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = retailPriceStr,
                        onValueChange = { retailPriceStr = it },
                        label = { Text("Retail Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minStockStr,
                        onValueChange = { minStockStr = it },
                        label = { Text("Min Stock (Bags)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = openingStockStr,
                        onValueChange = { openingStockStr = it },
                        label = { Text("Opening Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (initialProduct != null) {
                    OutlinedTextField(
                        value = priceRemarks,
                        onValueChange = { priceRemarks = it },
                        label = { Text("Price Change Reason (Logged in Price History)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isActive) "Active for New Sales" else "Deactivated")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bagW = bagWeightStr.toDoubleOrNull() ?: 0.0
                    val saleP = salePriceStr.toDoubleOrNull() ?: 0.0
                    val costP = purchasePriceStr.toDoubleOrNull() ?: 0.0
                    if (code.isBlank() || name.isBlank() || bagW <= 0.0 || saleP <= 0.0) {
                        errorMsg = "Code, Name, positive Bag Weight, and positive Sale Price are required."
                        return@Button
                    }
                    onSave(
                        ProductEntity(
                            id = initialProduct?.id ?: 0,
                            code = code.trim(),
                            name = name.trim(),
                            category = category,
                            description = description.trim(),
                            defaultBagWeightKg = bagW,
                            purchasePrice = costP,
                            dealerPrice = dealerPriceStr.toDoubleOrNull() ?: saleP,
                            salePrice = saleP,
                            retailPrice = retailPriceStr.toDoubleOrNull() ?: saleP,
                            minStockBags = (minStockStr.toIntOrNull() ?: 50).coerceAtLeast(0),
                            openingStockBags = (openingStockStr.toIntOrNull() ?: 0).coerceAtLeast(0),
                            isActive = isActive
                        ),
                        priceRemarks.trim()
                    )
                }
            ) {
                Text("Save Product")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
