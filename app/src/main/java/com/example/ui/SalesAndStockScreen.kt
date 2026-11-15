package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            },
            onAddPayment = {
                showPaymentDialog = true
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
                    onOpenStockIn = { showStockInDialog = true },
                    onEditProduct = {
                        editingProduct = it
                        showProductDialog = true
                    }
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
    var selectedFilterPill by remember { mutableStateOf("All Orders") }
    var cancellingOrder by remember { mutableStateOf<SaleOrderEntity?>(null) }

    val filteredOrders = remember(uiState.ordersWithItems, searchQuery, selectedFilterPill) {
        uiState.ordersWithItems.filter { owi ->
            val o = owi.order
            val pStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)
            val matchStatus = when (selectedFilterPill) {
                "Pending" -> o.status.equals("Pending", true) || pStatus.equals("Unpaid", true)
                "Partial" -> pStatus.equals("Partial", true) || o.status.equals("Partial", true)
                "Delivered" -> o.status.equals("Delivered", true)
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchQuery = q.isEmpty() ||
                o.orderNumber.lowercase().contains(q) ||
                o.invoiceNumber.lowercase().contains(q) ||
                o.dealerName.lowercase().contains(q) ||
                o.date.lowercase().contains(q) ||
                o.location.lowercase().contains(q) ||
                owi.items.any { it.productCode.lowercase().contains(q) || it.productName.lowercase().contains(q) }
            matchStatus && matchQuery
        }.sortedByDescending { it.order.date }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAF9)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar with Filter Icon
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search by Order No., Dealer or Date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF64748B)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color(0xFF15803D),
                            modifier = Modifier.clickable { /* Filter options */ }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF0F5233),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("orders_search_input")
                )
            }

            // 2. Filter Pills (All Orders, Pending, Partial, Delivered)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterPills = listOf("All Orders", "Pending", "Partial", "Delivered")
                    filterPills.forEach { pill ->
                        val isSelected = selectedFilterPill == pill
                        Surface(
                            color = if (isSelected) Color(0xFF0F5233) else Color.White,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF0F5233) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable { selectedFilterPill = pill }
                        ) {
                            Text(
                                text = pill,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Orders List or Empty State
            if (filteredOrders.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF0F5233),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "No orders found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Tap '+ New Order' below to create a new dairy feed order.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(filteredOrders, key = { it.order.id }) { owi ->
                    val o = owi.order
                    val payStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openInvoicePreview(o.id) }
                            .testTag("order_card_${o.orderNumber}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Top Row: Store Icon + Order Number (Left) vs Amount + Chevron (Right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF0FDF4))
                                            .border(
                                                1.dp,
                                                Color(0xFFBBF7D0),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = o.orderNumber,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = BusinessCalculations.formatCurrency(o.netAmount, cur),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "View Details",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Middle Row: Dealer Name
                            Text(
                                text = o.dealerName.ifBlank { "Direct Order" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = Color(0xFF334155),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            // Bottom Row: Date & Bags on left, Badges on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = o.date,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "${o.totalBags} Bags",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CleanStatusBadge(status = payStatus)
                                    CleanStatusBadge(status = o.status)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom "+ New Order" Pill Button
        Button(
            onClick = onNewOrder,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .height(48.dp)
                .testTag("new_sale_order_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Order",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        if (cancellingOrder != null) {
            ConfirmDestructiveDialog(
                title = "Delete / Cancel Order ${cancellingOrder!!.orderNumber}?",
                message = "This will cancel the order, reverse any automatic stock-out deduction, and remove its debit from the dealer's active balance.",
                confirmText = "Confirm Cancel",
                onConfirm = {
                    viewModel.cancelOrder(cancellingOrder!!)
                    cancellingOrder = null
                },
                onDismiss = { cancellingOrder = null }
            )
        }
    }
}

@Composable
private fun CleanStatusBadge(status: String) {
    val (bgColor, fgColor) = when (status.lowercase()) {
        "delivered", "paid" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D)) // Soft green
        "partial", "partially delivered" -> Pair(Color(0xFFFFEDD5), Color(0xFFC2410C)) // Soft peach/orange
        "pending", "in transit" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309)) // Soft amber
        else -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = fgColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    leadingIcon: ImageVector,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            color = Color(0xFFF8FAFC)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(leadingIcon, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = TextStyle(fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold))
                    Text(
                        selected,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "YYYY-MM-DD",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        color = Color(0xFFF8FAFC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = TextStyle(fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(placeholder, style = TextStyle(fontSize = 12.sp, color = Color(0xFF94A3B8)))
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

private fun getConsolidatedBadgeColors(code: String): Pair<Color, Color> {
    return when {
        code.startsWith("26M", ignoreCase = true) -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        code.startsWith("26P", ignoreCase = true) -> Pair(Color(0xFFFEF9C3), Color(0xFFA16207))
        code.startsWith("28P", ignoreCase = true) -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
        code.startsWith("55M", ignoreCase = true) -> Pair(Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF334155))
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

    // Dynamic consolidated display rows - includes active catalog products with 0 counts when no orders match
    val displayRows = remember(summary.lines, uiState.products) {
        val activeProducts = uiState.products.filter { !it.isArchived }
        if (summary.lines.isNotEmpty()) {
            val existingCodes = summary.lines.map { it.productCode }.toSet()
            val emptyLinesForRemaining = activeProducts
                .filter { it.code !in existingCodes }
                .map { p ->
                    ConsolidatedOrderLine(
                        productId = p.id,
                        productCode = p.code,
                        productName = p.name,
                        bagSizeLabel = "${p.defaultBagWeightKg.toInt()} KG",
                        bagWeightKg = p.defaultBagWeightKg,
                        totalBags = 0,
                        totalKg = 0.0,
                        totalMt = 0.0,
                        totalValue = 0.0,
                        orderCount = 0
                    )
                }
            summary.lines + emptyLinesForRemaining
        } else {
            activeProducts.map { p ->
                ConsolidatedOrderLine(
                    productId = p.id,
                    productCode = p.code,
                    productName = p.name,
                    bagSizeLabel = "${p.defaultBagWeightKg.toInt()} KG",
                    bagWeightKg = p.defaultBagWeightKg,
                    totalBags = 0,
                    totalKg = 0.0,
                    totalMt = 0.0,
                    totalValue = 0.0,
                    orderCount = 0
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("consolidated_orders_section"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // CARD 1: Consolidated Dispatch & Loading Sheet
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Decorative green arc in top right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(80.dp)
                            .clip(RoundedCornerShape(bottomStart = 80.dp))
                            .background(Color(0xFFDCFCE7).copy(alpha = 0.5f))
                    )

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F3E22)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Consolidated Dispatch & Loading Sheet",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                )
                                Text(
                                    "Automatically consolidates orders from multiple dealers and locations by Product and Bag Size.",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        lineHeight = 15.sp
                                    )
                                )
                            }
                        }

                        // Filters Row 1: Area Territory & Order Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterDropdown(
                                label = "Area Territory",
                                leadingIcon = Icons.Default.Place,
                                options = areas,
                                selected = areaFilter,
                                onSelect = { areaFilter = it },
                                modifier = Modifier.weight(1f)
                            )
                            FilterDropdown(
                                label = "Order Status",
                                leadingIcon = Icons.Default.Layers,
                                options = listOf("All", "Pending", "Confirmed", "In Transit", "Delivered"),
                                selected = statusFilter,
                                onSelect = { statusFilter = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Filters Row 2: From Date & To Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterDateField(
                                label = "From Date",
                                value = startDate,
                                onValueChange = { startDate = it },
                                modifier = Modifier.weight(1f)
                            )
                            FilterDateField(
                                label = "To Date",
                                value = endDate,
                                onValueChange = { endDate = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Filters Row 3: Action Buttons (Apply Filter & Clear)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { /* Values already dynamically reactive */ },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3E22)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Apply Filter",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    areaFilter = "All"
                                    statusFilter = "All"
                                    startDate = ""
                                    endDate = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF334155),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Clear",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // CARD 2: Consolidated Totals Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with CSV download button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Consolidated Totals (${summary.matchingOrdersCount} Orders)",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    val csv = DocumentAndExportManager.exportCsvReport(
                                        context = context,
                                        fileNamePrefix = "Consolidated_Order",
                                        headers = listOf("Product", "Bag Size", "Total Bags", "Total KG", "Total MT", "Total Value"),
                                        rows = displayRows.map {
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
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                color = Color(0xFFF0FDF4)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        "CSV",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 3 Metric Cards: Total Bags | Total KG | Total MT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Bags Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Total Bags",
                                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "${summary.totalBags} Bags",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF065F46)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Total KG Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF9C3),
                            border = BorderStroke(1.dp, Color(0xFFFDE047))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Total KG",
                                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF854D0E), fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "${summary.totalKg.roundToInt()} KG",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF854D0E)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Total MT Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Scale,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Total MT",
                                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "${BusinessCalculations.formatDecimal(summary.totalMt)} MT",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF1E40AF)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Total Value Dark Green Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F3E22)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Total Value",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(Color(0xFF2E6343))
                                )
                                Text(
                                    BusinessCalculations.formatCurrency(summary.totalValue, cur),
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // CARD 3: Product-wise Consolidated Orders Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Product-wise Consolidated Orders",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }

                    // Table with dark green header row and styled data rows
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        color = Color.White
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Dark Green Table Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F3E22))
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Product",
                                    modifier = Modifier.weight(1.1f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Start)
                                )
                                Text(
                                    "Bag Size",
                                    modifier = Modifier.weight(1.0f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                                )
                                Text(
                                    "Total Bags",
                                    modifier = Modifier.weight(1.0f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                                )
                                Text(
                                    "Total KG",
                                    modifier = Modifier.weight(1.0f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                                )
                                Text(
                                    "Total MT",
                                    modifier = Modifier.weight(1.0f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                                )
                                Text(
                                    "Total Value",
                                    modifier = Modifier.weight(1.3f),
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
                                )
                            }

                            // Table Data Rows
                            displayRows.forEachIndexed { index, line ->
                                val (badgeBg, badgeText) = getConsolidatedBadgeColors(line.productCode)
                                val rowBg = if (index % 2 == 0) Color.White else Color(0xFFF8FAFC)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Product Code Badge
                                    Box(
                                        modifier = Modifier.weight(1.1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = line.productCode,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = TextStyle(
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    color = badgeText
                                                )
                                            )
                                        }
                                    }

                                    // Bag Size
                                    Text(
                                        text = line.bagSizeLabel,
                                        modifier = Modifier.weight(1.0f),
                                        style = TextStyle(fontSize = 10.5.sp, color = Color(0xFF334155), textAlign = TextAlign.Center)
                                    )

                                    // Total Bags
                                    Text(
                                        text = "${line.totalBags}",
                                        modifier = Modifier.weight(1.0f),
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = if (line.totalBags > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (line.totalBags > 0) Color(0xFF0F3E22) else Color(0xFF334155),
                                            textAlign = TextAlign.Center
                                        )
                                    )

                                    // Total KG
                                    Text(
                                        text = "${line.totalKg.roundToInt()}",
                                        modifier = Modifier.weight(1.0f),
                                        style = TextStyle(
                                            fontSize = 10.5.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = Color(0xFF334155),
                                            textAlign = TextAlign.Center
                                        )
                                    )

                                    // Total MT
                                    Text(
                                        text = BusinessCalculations.formatDecimal(line.totalMt),
                                        modifier = Modifier.weight(1.0f),
                                        style = TextStyle(
                                            fontSize = 10.5.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = Color(0xFF334155),
                                            textAlign = TextAlign.Center
                                        )
                                    )

                                    // Total Value
                                    Text(
                                        text = "$cur ${line.totalValue.roundToInt()}",
                                        modifier = Modifier.weight(1.3f),
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (line.totalValue > 0) StatusSuccess else Color(0xFF0F172A),
                                            textAlign = TextAlign.End
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (index < displayRows.size - 1) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
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
    onOpenStockIn: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit = {}
) {
    val cur = uiState.config.currencySymbol
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Summary calculations (Dynamic from existing inventory data)
    val totalProducts = uiState.productStockStatuses.size
    val totalStockMt = uiState.productStockStatuses.sumOf { it.currentMt }
    val totalStockValue = uiState.productStockStatuses.sumOf { it.stockValueAtCost }
    val lowStockCount = uiState.productStockStatuses.count { it.isLowStock || it.currentBags <= it.product.minStockBags }

    // Dynamic filtering
    val filteredStockStatuses = remember(uiState.productStockStatuses, searchQuery, selectedFilter) {
        uiState.productStockStatuses.filter { st ->
            val matchesQuery = searchQuery.isBlank() ||
                st.product.code.contains(searchQuery, ignoreCase = true) ||
                st.product.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "LOW_STOCK" -> st.isLowStock || st.currentBags <= st.product.minStockBags
                "IN_STOCK" -> st.currentBags > 0
                "OUT_OF_STOCK" -> st.currentBags <= 0
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    // Color accents for product code badges & stripes
    val badgeColors = listOf(
        Pair(Color(0xFF16A34A), Color(0xFFDCFCE7)), // Green
        Pair(Color(0xFFEAB308), Color(0xFFFEF9C3)), // Amber / Yellow
        Pair(Color(0xFFDC2626), Color(0xFFFEE2E2)), // Red
        Pair(Color(0xFF0284C7), Color(0xFFE0F2FE)), // Blue
        Pair(Color(0xFF8B5CF6), Color(0xFFEDE9FE))  // Purple
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Stock & Inventory Header + Prominent + Stock In Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F5233)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Stock & Inventory",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Real-time stock, value and availability",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }

                Button(
                    onClick = onOpenStockIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("record_stock_in_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Stock In",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Stock In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        // 2. Summary Cards (4 compact cards: Products, Total Stock, Stock Value, Low Stock)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Card 1: Products
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFFC8E6C9))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$totalProducts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1
                        )
                        Text(
                            text = "Products",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            maxLines = 1
                        )
                    }
                }

                // Card 2: Total Stock
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE0F2FE),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Scale,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${BusinessCalculations.formatDecimal(totalStockMt)} MT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Color(0xFF0369A1),
                            maxLines = 1
                        )
                        Text(
                            text = "Total Stock",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            maxLines = 1
                        )
                    }
                }

                // Card 3: Stock Value
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF9C3),
                    border = BorderStroke(1.dp, Color(0xFFFDE047))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = BusinessCalculations.formatCurrency(totalStockValue, cur),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1
                        )
                        Text(
                            text = "Stock Value",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            maxLines = 1
                        )
                    }
                }

                // Card 4: Low Stock
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$lowStockCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFDC2626),
                            maxLines = 1
                        )
                        Text(
                            text = "Low Stock",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Search & Filter Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search product (e.g. 26P, 28P...)",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF0F5233),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF0F5233),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )

                // Filter Button with Dropdown Menu
                Box {
                    Surface(
                        onClick = { showFilterMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedFilter != "ALL") Color(0xFFE8F5E9) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (selectedFilter != "ALL") Color(0xFF16A34A) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (selectedFilter != "ALL") Color(0xFF16A34A) else Color(0xFF0F5233),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Products", fontWeight = if (selectedFilter == "ALL") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { selectedFilter = "ALL"; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Low Stock Only", fontWeight = if (selectedFilter == "LOW_STOCK") FontWeight.Bold else FontWeight.Normal, color = Color(0xFFDC2626)) },
                            onClick = { selectedFilter = "LOW_STOCK"; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("In Stock Only", fontWeight = if (selectedFilter == "IN_STOCK") FontWeight.Bold else FontWeight.Normal, color = Color(0xFF16A34A)) },
                            onClick = { selectedFilter = "IN_STOCK"; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Out of Stock Only", fontWeight = if (selectedFilter == "OUT_OF_STOCK") FontWeight.Bold else FontWeight.Normal, color = Color(0xFFB91C1C)) },
                            onClick = { selectedFilter = "OUT_OF_STOCK"; showFilterMenu = false }
                        )
                    }
                }
            }
        }

        // 4. Product Inventory Cards
        itemsIndexed(filteredStockStatuses, key = { _, it -> it.product.id }) { index, st ->
            val colorPair = badgeColors[index % badgeColors.size]
            val accentColor = colorPair.first
            val badgeBgColor = colorPair.second

            val isOutOfStock = st.currentBags <= 0 && st.product.minStockBags > 0
            val isLowStock = st.isLowStock || st.currentBags <= st.product.minStockBags
            val statusLabel = when {
                isOutOfStock -> "Out of Stock"
                isLowStock -> "Low Stock"
                else -> "In Stock"
            }
            val statusBgColor = when {
                isOutOfStock || isLowStock -> Color(0xFFFEE2E2)
                else -> Color(0xFFDCFCE7)
            }
            val statusTextColor = when {
                isOutOfStock -> Color(0xFFB91C1C)
                isLowStock -> Color(0xFFDC2626)
                else -> Color(0xFF16A34A)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditProduct(st.product) },
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // Left vertical accent stripe
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(accentColor)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Row: Badge + Info + Status & Chevron
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Product Code Badge (NO IMAGES)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = st.product.code,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = accentColor
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Product Name & Bag Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${st.product.code} ${st.product.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Bag Weight: ${st.product.defaultBagWeightKg.roundToInt()} KG  |  Min Level: ${st.product.minStockBags} Bags",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF64748B)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Status Pill + Chevron
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = statusBgColor
                                ) {
                                    Text(
                                        text = statusLabel,
                                        color = statusTextColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 4-Column Stock Movement Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Opening
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Opening", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(
                                        "${st.openingBags} Bags",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                // Stock In
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Stock In", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(
                                        "+${st.stockInBags}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF16A34A)
                                    )
                                }

                                // Stock Out
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Stock Out", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(
                                        "-${st.stockOutBags}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFFDC2626)
                                    )
                                }

                                // Current Stock with dynamic highlight
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLowStock) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Current Stock",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isLowStock) Color(0xFFDC2626) else Color(0xFF15803D)
                                        )
                                        Text(
                                            "${st.currentBags} Bags",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = if (isLowStock) Color(0xFFDC2626) else Color(0xFF15803D)
                                        )
                                        Text(
                                            "${BusinessCalculations.formatDecimal(st.currentMt)} MT",
                                            fontSize = 9.sp,
                                            color = if (isLowStock) Color(0xFFEF4444) else Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }

                        // Stock Valuation Row
                        Text(
                            text = "Stock Value: ${BusinessCalculations.formatCurrency(st.stockValueAtCost, cur)}  |  Cost: ${BusinessCalculations.formatCurrency(st.product.purchasePrice, cur)}  |  Sale Value: ${BusinessCalculations.formatCurrency(st.product.salePrice, cur)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (filteredStockStatuses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No products match \"$searchQuery\"" else "No inventory items found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Recent Stock Movements Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Stock Movements",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )
                Text(
                    "${uiState.stockMovements.size} logged",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                )
            }
        }

        items(uiState.stockMovements.take(20), key = { it.id }) { mov ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${mov.date} • ${mov.productCode} (${mov.bagSizeLabel})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        )
                        Text(
                            "${mov.supplierName.ifBlank { "Warehouse" }} • Ref: ${mov.invoiceNumber.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF64748B)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${if (mov.type == "STOCK_OUT") "-" else "+"}${mov.quantityBags} Bags",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = if (mov.type == "STOCK_OUT") Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                        Text(
                            "${mov.totalKg.roundToInt()} KG",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFF64748B)
                        )
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var receiptsExpanded by remember { mutableStateOf(false) }

    val activeDealerSummaries = remember(uiState.dealerSummaries) {
        uiState.dealerSummaries.filter { !it.dealer.isArchived }
    }
    val totalSalesAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.totalSales } }
    val totalPaidAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.totalPayments } }
    val totalOutstandingAll = remember(activeDealerSummaries) { activeDealerSummaries.sumOf { it.outstandingBalance.coerceAtLeast(0.0) } }

    val filteredDealerSummaries = remember(activeDealerSummaries, searchQuery, selectedFilter) {
        activeDealerSummaries.filter { ds ->
            val matchesQuery = searchQuery.isBlank() ||
                ds.dealer.businessName.contains(searchQuery, ignoreCase = true) ||
                ds.dealer.name.contains(searchQuery, ignoreCase = true) ||
                ds.dealer.area.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "OUTSTANDING" -> ds.outstandingBalance > 0.01
                "PAID" -> ds.outstandingBalance <= 0.01
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenNewPayment,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "Add Payment",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        text = "+ Add Payment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                },
                containerColor = Color(0xFF0F5233),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("record_payment_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Page Header (Icon + Title + Subtitle)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F5233)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Payments & Ledger",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Dealer payments, sales and outstanding",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }

            // 2. Overall Dealer Ledger Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Green Header Bar with BarChart Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F5233))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Overall Dealer Ledger Summary",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3 Clean Metric Cards in a Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Total Sales
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFFC8E6C9))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Total Sales",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF475569),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = BusinessCalculations.formatCurrency(totalSalesAll, cur),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Total Paid
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE0F2FE),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Total Paid",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF475569),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = BusinessCalculations.formatCurrency(totalPaidAll, cur),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0284C7),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Outstanding Balance
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEE2E2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Scale,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Outstanding Balance",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = BusinessCalculations.formatCurrency(totalOutstandingAll, cur),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Dealer Ledger Balances Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = Color(0xFF0F5233),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Dealer Ledger Balances",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Tap any dealer for complete ledger",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }

            // 4. Search & Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search dealer (e.g. Bilal, Munawar...)",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF0F5233),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF0F5233),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    // Filter Button with Dropdown Menu
                    Box {
                        Surface(
                            onClick = { showFilterMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedFilter != "ALL") Color(0xFFE8F5E9) else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (selectedFilter != "ALL") Color(0xFF16A34A) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (selectedFilter != "ALL") Color(0xFF16A34A) else Color(0xFF0F5233),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Dealers", fontWeight = if (selectedFilter == "ALL") FontWeight.Bold else FontWeight.Normal) },
                                onClick = { selectedFilter = "ALL"; showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Outstanding Only", fontWeight = if (selectedFilter == "OUTSTANDING") FontWeight.Bold else FontWeight.Normal, color = Color(0xFFDC2626)) },
                                onClick = { selectedFilter = "OUTSTANDING"; showFilterMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Fully Paid Only", fontWeight = if (selectedFilter == "PAID") FontWeight.Bold else FontWeight.Normal, color = Color(0xFF16A34A)) },
                                onClick = { selectedFilter = "PAID"; showFilterMenu = false }
                            )
                        }
                    }
                }
            }

            // 5. Dealer Cards
            items(filteredDealerSummaries, key = { "dlr-bal-${it.dealer.id}" }) { ds ->
                val dealerPaymentStatus = BusinessCalculations.getDealerPaymentStatus(
                    totalSales = ds.totalSales,
                    totalPayments = ds.totalPayments,
                    outstandingBalance = ds.outstandingBalance
                )
                val statusBgColor = when (dealerPaymentStatus) {
                    "Paid" -> Color(0xFFDCFCE7)
                    "Partial" -> Color(0xFFFEF3C7)
                    else -> Color(0xFFFEE2E2)
                }
                val statusTextColor = when (dealerPaymentStatus) {
                    "Paid" -> Color(0xFF16A34A)
                    "Partial" -> Color(0xFFB45309)
                    else -> Color(0xFFDC2626)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateToSubModule(SubModule.DEALERS)
                            viewModel.openDealerDetail(ds.dealer.id)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dealer Initial Avatar
                        val initialLetter = ds.dealer.businessName.trim().take(1).uppercase().ifBlank { "D" }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F5233)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Middle Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ds.dealer.businessName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total Sales: ${BusinessCalculations.formatCurrency(ds.totalSales, cur)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF475569))
                            )
                            Text(
                                text = "Total Paid: ${BusinessCalculations.formatCurrency(ds.totalPayments, cur)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF475569))
                            )
                            Text(
                                text = "Opening Balance: ${BusinessCalculations.formatCurrency(ds.dealer.openingBalance, cur)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = Color(0xFF64748B))
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .width(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right Status & Outstanding
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = statusBgColor
                            ) {
                                Text(
                                    text = dealerPaymentStatus,
                                    color = statusTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }

                            Text(
                                text = BusinessCalculations.formatCurrency(ds.outstandingBalance, cur),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = if (ds.outstandingBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "View Ledger",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F5233)
                                    )
                                )
                                Text(
                                    text = "→",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F5233)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredDealerSummaries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No dealers match \"$searchQuery\"" else "No active dealers found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // 6. Payment Receipts History Card (Expandable / Clickable)
            item {
                Surface(
                    onClick = { receiptsExpanded = !receiptsExpanded },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF0F5233),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Payment Receipts History (${uiState.payments.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF0F172A)
                                )
                            )
                        }

                        Icon(
                            imageVector = if (receiptsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ChevronRight,
                            contentDescription = if (receiptsExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded Payment Receipts Items
            if (receiptsExpanded) {
                if (uiState.payments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No payment receipts recorded yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    items(uiState.payments, key = { it.id }) { pay ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pay.paymentNumber} • ${pay.dealerName}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                    )
                                    Text(
                                        text = "${pay.date} • ${pay.paymentMethod}${if (pay.referenceNumber.isNotBlank()) " • Ref: ${pay.referenceNumber}" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    )
                                    if (pay.remarks.isNotBlank()) {
                                        Text(
                                            text = pay.remarks,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "+${BusinessCalculations.formatCurrency(pay.amount, cur)}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color(0xFF16A34A)
                                    )
                                    IconButton(onClick = { deletingPayment = pay }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Payment",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
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
    onEditOrder: () -> Unit,
    onAddPayment: () -> Unit = {}
) {
    val context = LocalContext.current
    val o = orderWithItems.order
    val cur = config.currencySymbol

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("invoice_preview_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Actions Row (Back, Edit Order, Share / PDF)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F172A)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Edit Order Button
                OutlinedButton(
                    onClick = onEditOrder,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F172A)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Order", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Share / PDF Button
                Button(
                    onClick = {
                        val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, config, orderWithItems)
                        DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Share Invoice ${o.invoiceNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share / PDF", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Seller Info & Invoice Header Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // Left Green Accent Stripe
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF0F5233))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Seller Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = config.companyName.ifBlank { "Attique Ur Rehman" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F5233)
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "WandaBook Pro • Dairy Feed Sales & Management",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF0F5233),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = config.companyAddress.ifBlank { "7-KM Sahiwal-Okara GT Road, Industrial Estate, Sahiwal" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Invoice Right Column
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF0F5233),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "INVOICE",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                )
                            }

                            // Invoice Number Pill Badge
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = o.invoiceNumber,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF0F5233),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Date: ${o.date}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Bill To Dealer Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFDCFCE7))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F5233)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "BILL TO DEALER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = o.dealerName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF0F172A)
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = o.dealerAddress.ifBlank { o.location.ifBlank { o.area } },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                )
                            }
                        }
                    }

                    // Order / Delivery Status Badge
                    val isPending = o.status.contains("Pending", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isPending) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (isPending) Icons.Default.Schedule else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isPending) Color(0xFFB45309) else Color(0xFF15803D),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = o.status,
                                color = if (isPending) Color(0xFFB45309) else Color(0xFF15803D),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Products Table Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Column {
                    // Dark Green Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F5233))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Product / Description",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = "Rate",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Amount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Product Item Rows
                    orderWithItems.items.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1: Badge + Name & Bag Spec
                            Row(
                                modifier = Modifier.weight(2f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFDCFCE7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.productCode,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF15803D)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${item.productCode} ${item.productName}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF0F172A)
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.quantityBags} Bags × ${item.bagSizeLabel}\n(${item.totalKg.roundToInt()} KG = ${BusinessCalculations.formatDecimal(item.totalMt)} MT)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 13.sp
                                        )
                                    )
                                }
                            }

                            // Column 2: Rate with Vertical Divider
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(1.dp)
                                        .background(Color(0xFFE2E8F0))
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = BusinessCalculations.formatCurrency(item.pricePerBag, cur),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = Color(0xFF0F172A)
                                        )
                                    )
                                    Text(
                                        text = "/ bag",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Column 3: Amount
                            Column(
                                modifier = Modifier.weight(1.2f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = BusinessCalculations.formatCurrency(item.netAmount, cur),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = Color(0xFF0F172A),
                                        textAlign = TextAlign.End
                                    )
                                )
                                if (item.discountTotal > 0) {
                                    Text(
                                        text = "Disc: -${BusinessCalculations.formatCurrency(item.discountTotal, cur)}",
                                        fontSize = 9.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Total Volume Bar
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFDCFCE7))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Total Volume: ${o.totalBags} Bags (${o.totalKg.roundToInt()} KG / ${BusinessCalculations.formatDecimal(o.totalMt)} MT)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = Color(0xFF15803D)
                        )
                    )
                }
            }
        }

        // 6. Payment Summary Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gross Amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Paid, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Text("Gross Amount:", fontSize = 12.sp, color = Color(0xFF334155))
                        }
                        Text(
                            text = BusinessCalculations.formatCurrency(o.grossAmount, cur),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }

                    // Total Discount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Text("Total Discount:", fontSize = 12.sp, color = Color(0xFF334155))
                        }
                        Text(
                            text = "- ${BusinessCalculations.formatCurrency(o.discountAmount, cur)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = Color(0xFF64748B)
                            )
                        )
                    }

                    // Net Amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0FDF4))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(17.dp))
                            Text("Net Amount:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        }
                        Text(
                            text = BusinessCalculations.formatCurrency(o.netAmount, cur),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color(0xFF15803D)
                            )
                        )
                    }

                    // Paid Amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            Text("Paid Amount:", fontSize = 12.sp, color = Color(0xFF334155))
                        }
                        Text(
                            text = BusinessCalculations.formatCurrency(o.paidAmount, cur),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        )
                    }

                    // Balance Outstanding Box
                    val hasOutstanding = o.outstandingAmount > 0.01
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasOutstanding) Color(0xFFFEE2E2) else Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Scale,
                                contentDescription = null,
                                tint = if (hasOutstanding) Color(0xFFDC2626) else Color(0xFF15803D),
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Balance Outstanding:",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasOutstanding) Color(0xFFDC2626) else Color(0xFF15803D)
                            )
                        }
                        Text(
                            text = BusinessCalculations.formatCurrency(o.outstandingAmount, cur),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = if (hasOutstanding) Color(0xFFDC2626) else Color(0xFF15803D)
                            )
                        )
                    }
                }
            }
        }

        // 7. Bottom Action Buttons (Print Invoice & + Add Payment & Share WhatsApp)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Print Invoice Button
                    OutlinedButton(
                        onClick = {
                            val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, config, orderWithItems)
                            DocumentAndExportManager.printPdfFile(context, pdf, "Invoice_${o.invoiceNumber}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF0F5233)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F5233)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color(0xFF0F5233))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Invoice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // + Add Payment Button
                    Button(
                        onClick = onAddPayment,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Payment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // WhatsApp Sharing Button
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share WhatsApp Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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

    // Color accents for product code badges & stripes
    val badgeColors = listOf(
        Pair(Color(0xFF16A34A), Color(0xFFDCFCE7)), // Green
        Pair(Color(0xFFEAB308), Color(0xFFFEF9C3)), // Amber / Yellow
        Pair(Color(0xFFDC2626), Color(0xFFFEE2E2)), // Red
        Pair(Color(0xFF0284C7), Color(0xFFE0F2FE)), // Blue
        Pair(Color(0xFF8B5CF6), Color(0xFFEDE9FE))  // Purple
    )

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Product",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        text = "+ Add Feed Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                },
                containerColor = Color(0xFF0F5233),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("add_product_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Bag Sizes Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0F5233)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Bag Sizes",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                    )
                                    Text(
                                        text = "Dynamic Bag Sizes Management",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    )
                                }
                            }

                            // Editable by Administrator Pill Button
                            Surface(
                                onClick = { showBagSizeDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                color = Color.White
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Editable by Administrator",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }

                        // Formula Banner Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF9C3),
                            border = BorderStroke(1.dp, Color(0xFFFDE047)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bags × KG = Total KG / 1000 = MT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        // Bag Sizes Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.bagSizes.filter { !it.isArchived }.forEach { bs ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (bs.isActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (bs.isActive) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { viewModel.saveBagSize(bs.copy(isActive = !bs.isActive)) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${bs.label} (${bs.weightKg.roundToInt()} KG)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (bs.isActive) Color(0xFF15803D) else Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Archive Bag Size",
                                            tint = if (bs.isActive) Color(0xFF15803D) else Color(0xFF64748B),
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { viewModel.saveBagSize(bs.copy(isArchived = true)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Feed Products & Price Management Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF0F5233),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Feed Products & Price Management",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Historical sales never change when prices update.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }

            // 3. Product Cards
            itemsIndexed(uiState.products.filter { !it.isArchived }, key = { _, it -> it.id }) { index, prod ->
                val colorPair = badgeColors[index % badgeColors.size]
                val accentColor = colorPair.first
                val badgeBgColor = colorPair.second

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditProduct(prod) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        // Left vertical accent stripe
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(accentColor)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: Code Badge + Title + Active Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Product Code Badge (NO BAG IMAGES)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(badgeBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = prod.code,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = accentColor
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Product Title & Subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prod.code} — ${prod.name}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF0F172A)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${prod.category} • Default Bag: ${prod.defaultBagWeightKg.roundToInt()} KG",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF475569)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Status Badge
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (prod.isActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                                ) {
                                    Text(
                                        text = if (prod.isActive) "Active" else "Inactive",
                                        color = if (prod.isActive) Color(0xFF16A34A) else Color(0xFF64748B),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Description Box
                            if (prod.description.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = prod.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 15.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            // 3-Column Pricing & Spec Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Purchase Cost
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Paid,
                                                contentDescription = null,
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text("Purchase Cost", fontSize = 9.5.sp, color = Color(0xFF475569))
                                        }
                                        Text(
                                            text = BusinessCalculations.formatCurrency(prod.purchasePrice, cur),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = Color(0xFF0F172A)
                                        )
                                    }

                                    // Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .width(1.dp)
                                            .background(Color(0xFFE2E8F0))
                                    )

                                    // Dealer / Sale Price
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalOffer,
                                                contentDescription = null,
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text("Dealer / Sale Price", fontSize = 9.5.sp, color = Color(0xFF475569))
                                        }
                                        Text(
                                            text = BusinessCalculations.formatCurrency(prod.salePrice, cur),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = Color(0xFF0F172A)
                                        )
                                    }

                                    // Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .width(1.dp)
                                            .background(Color(0xFFE2E8F0))
                                    )

                                    // Default Bag
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Scale,
                                                contentDescription = null,
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text("Default Bag", fontSize = 9.5.sp, color = Color(0xFF475569))
                                        }
                                        Text(
                                            text = "${prod.defaultBagWeightKg.roundToInt()} KG",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }

                            // Action Buttons: Archive & Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Archive Button
                                OutlinedButton(
                                    onClick = { archivingProduct = prod },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = "Archive",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Archive", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                // Delete Button
                                Surface(
                                    onClick = { archivingProduct = prod },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEE2E2),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            color = Color(0xFFDC2626),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Price Change Audit Log (if any)
            if (uiState.priceHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Price Change Audit Log (Old orders preserved at original price)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    )
                }
                items(uiState.priceHistory.take(15), key = { "ph-${it.id}" }) { ph ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${ph.productCode} (${ph.productName})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                            Text(
                                "Sale Price: ${ph.oldSalePrice.roundToInt()} -> ${ph.newSalePrice.roundToInt()} | Cost: ${ph.oldPurchasePrice.roundToInt()} -> ${ph.newPurchasePrice.roundToInt()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp),
                                color = Color(0xFF475569)
                            )
                            if (ph.remarks.isNotBlank()) {
                                Text(
                                    ph.remarks,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF64748B)
                                )
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
            title = { Text("Add Bag Size", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Bag Weight in KG (e.g. 25, 50) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBagSizeDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var location by remember { mutableStateOf(existing?.location ?: (dealers.firstOrNull()?.city ?: "Sahiwal")) }
    var status by remember { mutableStateOf(existing?.status ?: "Confirmed") }
    var expectedDeliveryDate by remember { mutableStateOf(existing?.expectedDeliveryDate ?: SampleDataSeeder.todayDate()) }
    var paidAmountStr by remember { mutableStateOf(existing?.paidAmount?.roundToInt()?.toString() ?: "0") }
    var paymentMethod by remember { mutableStateOf(paymentMethods.firstOrNull() ?: "Cash") }
    var remarks by remember { mutableStateOf(existing?.remarks ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Draft line items list
    var lineItems by remember {
        mutableStateOf(
            initialOrder?.items ?: run {
                val firstProd = products.firstOrNull()
                if (firstProd != null) {
                    val bagKg = firstProd.defaultBagWeightKg
                    listOf(
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
                    )
                } else emptyList()
            }
        )
    }

    val totalBags = lineItems.sumOf { it.quantityBags }
    val totalKg = lineItems.sumOf { it.totalKg }
    val totalMt = totalKg / 1000.0
    val grossAmount = lineItems.sumOf { it.grossAmount }
    val discountAmount = lineItems.sumOf { it.discountTotal }
    val netAmount = lineItems.sumOf { it.netAmount }
    val paidAmount = paidAmountStr.toDoubleOrNull() ?: 0.0
    val outstandingAmount = (netAmount - paidAmount).coerceAtLeast(0.0)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAF9)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (existing == null) "New Order" else "Edit Order ${existing.orderNumber}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0F5233)
                        )
                    )
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    "Cancel",
                                    color = Color(0xFF64748B),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Button(
                                onClick = {
                                    if (lineItems.isEmpty()) {
                                        errorMsg = "Please add at least one product."
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
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    "Save Order",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (errorMsg != null) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = Color(0xFFB91C1C),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 1. Select Dealer *
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("Select Dealer", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                            Text(" *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                        }
                        DropdownSelectorField(
                            label = "Choose Dealer",
                            options = dealers.map { it.businessName }.ifEmpty { listOf("Walk-in Dealer") },
                            selectedOption = selectedDealerName.ifEmpty { "Choose Dealer" },
                            onOptionSelected = { dName ->
                                selectedDealerName = dName
                                dealers.firstOrNull { it.businessName == dName }?.let {
                                    location = "${it.city} (${it.area})"
                                }
                            }
                        )
                    }

                    // 2. Order Date & Order Status *
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text("Order Date", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                                Text(" *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                            }
                            OutlinedTextField(
                                value = date,
                                onValueChange = { date = it },
                                placeholder = { Text("YYYY-MM-DD") },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF0F5233), modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Color(0xFF0F5233),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text("Order Status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                                Text(" *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                            }
                            DropdownSelectorField(
                                label = "Order Status",
                                options = listOf("Confirmed", "Pending", "In Transit", "Delivered"),
                                selectedOption = status,
                                onOptionSelected = { status = it }
                            )
                        }
                    }

                    // 3. Add Products Section & Table
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Add Products",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF0F172A)
                        )

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Table Header
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Feed Code", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF475569), modifier = Modifier.weight(1.1f))
                                        Text("Bag (Qty)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF475569), modifier = Modifier.weight(1f))
                                        Text("Price/Bag", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF475569), modifier = Modifier.weight(1.1f))
                                        Text("Amount", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF475569), modifier = Modifier.weight(1.2f))
                                        Spacer(modifier = Modifier.width(36.dp))
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFE2E8F0))

                                // Table Rows
                                if (lineItems.isEmpty()) {
                                    Text(
                                        "No products added yet. Tap '+ Add Another Product' below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    lineItems.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // 1. Feed Code Dropdown
                                            Box(modifier = Modifier.weight(1.1f)) {
                                                var codeExpanded by remember { mutableStateOf(false) }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(40.dp)
                                                        .clickable { codeExpanded = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(item.productCode, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = codeExpanded,
                                                    onDismissRequest = { codeExpanded = false }
                                                ) {
                                                    products.forEach { prod ->
                                                        DropdownMenuItem(
                                                            text = { Text("${prod.code} - ${prod.name}", fontSize = 12.sp) },
                                                            onClick = {
                                                                val updated = lineItems.toMutableList()
                                                                val bagW = prod.defaultBagWeightKg
                                                                val gross = item.quantityBags * prod.salePrice
                                                                updated[index] = item.copy(
                                                                    productId = prod.id,
                                                                    productCode = prod.code,
                                                                    productName = prod.name,
                                                                    bagWeightKg = bagW,
                                                                    bagSizeLabel = "${bagW.roundToInt()} KG",
                                                                    pricePerBag = prod.salePrice,
                                                                    purchasePriceSnapshot = prod.purchasePrice,
                                                                    grossAmount = gross,
                                                                    netAmount = gross
                                                                )
                                                                lineItems = updated
                                                                codeExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // 2. Qty Input
                                            var qtyText by remember(item.quantityBags) { mutableStateOf(item.quantityBags.toString()) }
                                            OutlinedTextField(
                                                value = qtyText,
                                                onValueChange = { newText ->
                                                    qtyText = newText
                                                    val q = newText.toIntOrNull() ?: 0
                                                    val updated = lineItems.toMutableList()
                                                    val kg = q * item.bagWeightKg
                                                    val gross = q * item.pricePerBag
                                                    updated[index] = item.copy(
                                                        quantityBags = q,
                                                        totalKg = kg,
                                                        totalMt = kg / 1000.0,
                                                        grossAmount = gross,
                                                        netAmount = gross
                                                    )
                                                    lineItems = updated
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f).height(48.dp)
                                            )

                                            // 3. Price/Bag Input
                                            var priceText by remember(item.pricePerBag) { mutableStateOf(item.pricePerBag.roundToInt().toString()) }
                                            OutlinedTextField(
                                                value = priceText,
                                                onValueChange = { newText ->
                                                    priceText = newText
                                                    val p = newText.toDoubleOrNull() ?: item.pricePerBag
                                                    val updated = lineItems.toMutableList()
                                                    val gross = item.quantityBags * p
                                                    updated[index] = item.copy(
                                                        pricePerBag = p,
                                                        grossAmount = gross,
                                                        netAmount = gross
                                                    )
                                                    lineItems = updated
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.1f).height(48.dp)
                                            )

                                            // 4. Row Amount
                                            Text(
                                                text = BusinessCalculations.formatDecimal(item.netAmount),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier.weight(1.2f)
                                            )

                                            // 5. Delete Action
                                            IconButton(
                                                onClick = {
                                                    val updated = lineItems.toMutableList()
                                                    updated.removeAt(index)
                                                    lineItems = updated
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        if (index < lineItems.size - 1) {
                                            HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // "+ Add Another Product" Button
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val nextProd = products.firstOrNull { p -> lineItems.none { it.productId == p.id } } ?: products.firstOrNull()
                                            if (nextProd != null) {
                                                val bagW = nextProd.defaultBagWeightKg
                                                lineItems = lineItems + OrderItemEntity(
                                                    orderId = 0,
                                                    productId = nextProd.id,
                                                    productCode = nextProd.code,
                                                    productName = nextProd.name,
                                                    bagSizeLabel = "${bagW.roundToInt()} KG",
                                                    bagWeightKg = bagW,
                                                    quantityBags = 25,
                                                    totalKg = 25 * bagW,
                                                    totalMt = (25 * bagW) / 1000.0,
                                                    purchasePriceSnapshot = nextProd.purchasePrice,
                                                    pricePerBag = nextProd.salePrice,
                                                    discountPerBag = 0.0,
                                                    grossAmount = 25 * nextProd.salePrice,
                                                    discountTotal = 0.0,
                                                    netAmount = 25 * nextProd.salePrice
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0F5233)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F5233))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ Add Another Product", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 4. Billing & Settlement Box
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Billing & Payment Settlement",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Volume", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                                Text("$totalBags Bags (${BusinessCalculations.formatDecimal(totalMt)} MT)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F172A))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Net Bill", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F172A))
                                Text(
                                    text = BusinessCalculations.formatCurrency(netAmount, currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color(0xFF0F5233)
                                )
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Paid Amount Field (Editable - automatically subtracted from bill and recorded into payments)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Paid Amount (Received / Advance)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        "Auto-added to Payments",
                                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                OutlinedTextField(
                                    value = paidAmountStr,
                                    onValueChange = { paidAmountStr = it },
                                    prefix = { Text("$currencySymbol ", fontWeight = FontWeight.Bold, color = Color(0xFF0F5233)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF0FDF4),
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = Color(0xFF16A34A),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Payment Method Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Payment Method",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF1E293B)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Cash", "Bank Transfer", "Cheque", "Online").forEach { method ->
                                        val isSelected = paymentMethod.equals(method, ignoreCase = true)
                                        Surface(
                                            onClick = { paymentMethod = method },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF0F5233) else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F5233) else Color(0xFFCBD5E1)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = method,
                                                    style = TextStyle(
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Remaining Outstanding Balance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Remaining Balance",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = BusinessCalculations.formatCurrency(outstandingAmount, currencySymbol),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = if (outstandingAmount > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
