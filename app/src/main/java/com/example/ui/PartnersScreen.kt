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

@Composable
fun PartnersScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    triggerNewDealer: Boolean = false,
    triggerNewFarmer: Boolean = false,
    onConsumeTrigger: () -> Unit = {}
) {
    var showDealerDialog by remember { mutableStateOf(false) }
    var editingDealer by remember { mutableStateOf<DealerEntity?>(null) }

    var showFarmerDialog by remember { mutableStateOf(false) }
    var editingFarmer by remember { mutableStateOf<FarmerEntity?>(null) }

    LaunchedEffect(triggerNewDealer, triggerNewFarmer) {
        if (triggerNewDealer) {
            editingDealer = null
            showDealerDialog = true
            onConsumeTrigger()
        }
        if (triggerNewFarmer) {
            editingFarmer = null
            showFarmerDialog = true
            onConsumeTrigger()
        }
    }

    // If a specific Dealer is selected, show full Dealer Profile + Complete Ledger
    val selectedDealerSummary = uiState.dealerSummaries.firstOrNull { it.dealer.id == uiState.selectedDealerForDetail }
    if (selectedDealerSummary != null) {
        DealerProfileDetailView(
            summary = selectedDealerSummary,
            uiState = uiState,
            viewModel = viewModel,
            onEdit = {
                editingDealer = selectedDealerSummary.dealer
                showDealerDialog = true
            }
        )
    } else {
        // If a specific Farmer is selected, show full Farmer Profile
        val selectedFarmer = uiState.farmers.firstOrNull { it.id == uiState.selectedFarmerForDetail }
        if (selectedFarmer != null) {
            FarmerProfileDetailView(
                farmer = selectedFarmer,
                uiState = uiState,
                viewModel = viewModel,
                onEdit = {
                    editingFarmer = selectedFarmer
                    showFarmerDialog = true
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SubModulePillBar(
                    modules = listOf(SubModule.DEALERS, SubModule.FARMERS),
                    selected = uiState.activeSubModule,
                    onSelect = { viewModel.navigateToSubModule(it) }
                )

                when (uiState.activeSubModule) {
                    SubModule.FARMERS -> FarmersListSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        onAddFarmer = {
                            editingFarmer = null
                            showFarmerDialog = true
                        },
                        onEditFarmer = {
                            editingFarmer = it
                            showFarmerDialog = true
                        }
                    )
                    else -> DealersListSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        onAddDealer = {
                            editingDealer = null
                            showDealerDialog = true
                        },
                        onEditDealer = {
                            editingDealer = it
                            showDealerDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDealerDialog) {
        DealerFormDialog(
            initialDealer = editingDealer,
            existingCount = uiState.dealers.size,
            areas = uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name },
            dealerTypes = uiState.lookups.filter { it.categoryType == "DEALER_TYPE" && !it.isArchived }.map { it.name },
            customFieldDefs = uiState.customFieldDefs.filter { it.entityType == "DEALER" },
            onDismiss = { showDealerDialog = false },
            onSave = { saved ->
                viewModel.saveDealer(saved)
                showDealerDialog = false
            }
        )
    }

    if (showFarmerDialog) {
        FarmerFormDialog(
            initialFarmer = editingFarmer,
            existingCount = uiState.farmers.size,
            areas = uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name },
            dealers = uiState.dealers.filter { !it.isArchived },
            products = uiState.products.filter { !it.isArchived },
            customFieldDefs = uiState.customFieldDefs.filter { it.entityType == "FARMER" },
            onDismiss = { showFarmerDialog = false },
            onSave = { saved ->
                viewModel.saveFarmer(saved)
                showFarmerDialog = false
            }
        )
    }
}

@Composable
private fun DealersListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onAddDealer: () -> Unit,
    onEditDealer: (DealerEntity) -> Unit
) {
    val context = LocalContext.current
    val cur = uiState.config.currencySymbol
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var areaFilter by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Outstanding") }

    val areas = remember(uiState.lookups) {
        listOf("All") + uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name }
    }

    val filteredDealers = remember(uiState.dealerSummaries, searchQuery, statusFilter, areaFilter, sortBy) {
        uiState.dealerSummaries.filter { ds ->
            val d = ds.dealer
            val matchArchived = if (statusFilter == "Archived") d.isArchived else !d.isArchived
            val matchStatus = statusFilter == "All" || statusFilter == "Archived" || d.status.equals(statusFilter, true)
            val matchArea = areaFilter == "All" || d.area.equals(areaFilter, true)
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                d.name.lowercase().contains(q) ||
                d.businessName.lowercase().contains(q) ||
                d.code.lowercase().contains(q) ||
                d.mobile.contains(q) ||
                d.city.lowercase().contains(q) ||
                d.village.lowercase().contains(q)
            matchArchived && matchStatus && matchArea && matchSearch
        }.let { list ->
            when (sortBy) {
                "Outstanding" -> list.sortedByDescending { it.outstandingBalance }
                "Sales" -> list.sortedByDescending { it.totalSales }
                "Bags" -> list.sortedByDescending { it.totalBags }
                else -> list.sortedBy { it.dealer.businessName }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDealer,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Dealer") },
                text = { Text("Add Dealer") },
                modifier = Modifier.testTag("add_dealer_fab")
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
                    placeholder = { Text("Search dealer name, business, mobile, city, ID...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dealer_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Active", "Potential", "Inactive", "Blocked", "Archived").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DropdownSelectorField(
                        label = "Area Territory",
                        options = areas,
                        selectedOption = areaFilter,
                        onOptionSelected = { areaFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Sort By",
                        options = listOf("Outstanding", "Sales", "Bags", "Name"),
                        selectedOption = sortBy,
                        onOptionSelected = { sortBy = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            items(filteredDealers, key = { it.dealer.id }) { summary ->
                val d = summary.dealer
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openDealerDetail(d.id) }
                        .testTag("dealer_card_${d.code}"),
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
                                    text = "${d.businessName} (${d.code})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${d.name} • ${d.area} (${d.city})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = if (d.isArchived) "Archived" else d.status)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Bags / MT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${summary.totalBags} bags (${BusinessCalculations.formatDecimal(summary.totalMt)} MT)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Column {
                                Text("Total Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    BusinessCalculations.formatCurrency(summary.totalSales, cur),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = StatusSuccess
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    BusinessCalculations.formatCurrency(summary.outstandingBalance, cur),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = if (summary.outstandingBalance > 0) StatusDanger else StatusSuccess
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { DocumentAndExportManager.dialPhoneNumber(context, d.mobile) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call Dealer", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = {
                                        DocumentAndExportManager.openWhatsAppChat(
                                            context,
                                            d.whatsapp.ifBlank { d.mobile },
                                            "Assalam-o-Alaikum ${d.name} (${d.businessName}), current balance is ${BusinessCalculations.formatCurrency(summary.outstandingBalance, cur)}."
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp Dealer", tint = StatusSuccess)
                                }
                                IconButton(
                                    onClick = { DocumentAndExportManager.openMapLocation(context, "${d.address}, ${d.city}") },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "View Map Location", tint = StatusInfo)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onEditDealer(d) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Edit")
                                }
                                Button(
                                    onClick = { viewModel.openDealerDetail(d.id) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Ledger & Profile")
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
private fun DealerProfileDetailView(
    summary: DealerFinancialSummary,
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val d = summary.dealer
    val cur = uiState.config.currencySymbol
    var activeSection by remember { mutableStateOf("LEDGER") }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showQuickPaymentDialog by remember { mutableStateOf(false) }

    val customFields = remember(d.customFieldsJson) { BusinessCalculations.parseCustomFields(d.customFieldsJson) }
    val dealerOrders = remember(uiState.ordersWithItems, d.id) {
        uiState.ordersWithItems.filter { it.order.dealerId == d.id }
    }
    val dealerVisits = remember(uiState.visits, d.id) {
        uiState.visits.filter { it.partnerType == "Dealer" && it.partnerId == d.id }
    }
    val dealerTrials = remember(uiState.trialsWithMilk, d.id) {
        uiState.trialsWithMilk.filter { it.trial.dealerId == d.id }
    }
    val dealerComplaints = remember(uiState.complaints, d.id) {
        uiState.complaints.filter { it.partnerType == "Dealer" && it.partnerId == d.id }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dealer_profile_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.openDealerDetail(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back to Dealers")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = { showArchiveConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Archive")
                    }
                }
            }
        }

        // Dealer Header Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.businessName, style = MaterialTheme.typography.headlineSmall)
                            Text("${d.name} • ${d.code} • ${d.dealerType}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(status = if (d.isArchived) "Archived" else d.status)
                    }
                    Text("Address: ${d.address}, ${d.village}, ${d.city} (${d.area})", style = MaterialTheme.typography.bodySmall)
                    Text("Sales Officer: ${d.assignedSalesPerson} • Added: ${d.dateAdded}", style = MaterialTheme.typography.bodySmall)
                    Text("Products Purchased: ${summary.productsPurchasedSummary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (customFields.isNotEmpty()) {
                        Text(
                            text = customFields.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (d.notes.isNotBlank()) {
                        Text("Notes: ${d.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { DocumentAndExportManager.dialPhoneNumber(context, d.mobile) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call")
                        }
                        Button(
                            onClick = {
                                DocumentAndExportManager.openWhatsAppChat(
                                    context,
                                    d.whatsapp.ifBlank { d.mobile },
                                    "Ledger Statement for ${d.businessName}: Total Sales ${BusinessCalculations.formatCurrency(summary.totalSales, cur)}, Paid ${BusinessCalculations.formatCurrency(summary.totalPayments, cur)}, Balance Due ${BusinessCalculations.formatCurrency(summary.outstandingBalance, cur)}."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                        OutlinedButton(
                            onClick = { showQuickPaymentDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Payment")
                        }
                    }
                }
            }
        }

        // KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatCard(
                        title = "Total Orders / Volume",
                        value = "${summary.totalOrders} (${summary.totalBags} Bags)",
                        subtitle = "${BusinessCalculations.formatDecimal(summary.totalMt)} MT • Last: ${summary.lastOrderDate}",
                        icon = Icons.Default.ShoppingBag,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Outstanding Balance",
                        value = BusinessCalculations.formatCurrency(summary.outstandingBalance, cur),
                        subtitle = "Limit: ${BusinessCalculations.formatCurrency(d.creditLimit, cur)}",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = if (summary.outstandingBalance > 0) StatusDanger else StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatCard(
                        title = "Total Sales (Debit)",
                        value = BusinessCalculations.formatCurrency(summary.totalSales, cur),
                        subtitle = "Opening: ${BusinessCalculations.formatCurrency(d.openingBalance, cur)}",
                        icon = Icons.Default.TrendingUp,
                        accentColor = StatusInfo,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Total Payments (Credit)",
                        value = BusinessCalculations.formatCurrency(summary.totalPayments, cur),
                        subtitle = "Last Payment: ${summary.lastPaymentDate}",
                        icon = Icons.Default.DoneAll,
                        accentColor = StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "LEDGER" to "Complete Ledger (${summary.ledgerEntries.size})",
                    "ORDERS" to "Orders (${dealerOrders.size})",
                    "FIELD" to "Visits/Trials/Complaints"
                ).forEach { (key, title) ->
                    FilterChip(
                        selected = activeSection == key,
                        onClick = { activeSection = key },
                        label = { Text(title) }
                    )
                }
            }
        }

        if (activeSection == "LEDGER") {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chronological Dealer Ledger", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = {
                            val csv = DocumentAndExportManager.exportCsvReport(
                                context = context,
                                fileNamePrefix = "Ledger_${d.code}",
                                headers = listOf("Date", "Ref", "Description", "Debit", "Credit", "Balance"),
                                rows = summary.ledgerEntries.map {
                                    listOf(it.date, it.referenceCode, it.description, "${it.debit}", "${it.credit}", "${it.runningBalance}")
                                }
                            )
                            DocumentAndExportManager.shareFile(context, csv, "text/csv", "Share Dealer Ledger CSV")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV")
                    }
                }
            }

            items(summary.ledgerEntries, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${row.date} • ${row.referenceCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(row.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (row.debit > 0) {
                                Text(
                                    "Dr +${BusinessCalculations.formatCurrency(row.debit, cur)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = StatusDanger
                                )
                            }
                            if (row.credit > 0) {
                                Text(
                                    "Cr -${BusinessCalculations.formatCurrency(row.credit, cur)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = StatusSuccess
                                )
                            }
                            Text(
                                "Bal: ${BusinessCalculations.formatCurrency(row.runningBalance, cur)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        } else if (activeSection == "ORDERS") {
            items(dealerOrders, key = { it.order.id }) { owi ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openInvoicePreview(owi.order.id) },
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
                            Text("${owi.order.orderNumber} (${owi.order.date})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                owi.items.joinToString(", ") { "${it.quantityBags}x ${it.productCode} @ ${it.pricePerBag.toInt()}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusBadge(owi.order.status)
                            Text(
                                BusinessCalculations.formatCurrency(owi.order.netAmount, cur),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text("Visits (${dealerVisits.size}) • Feed Trials (${dealerTrials.size}) • Complaints (${dealerComplaints.size})", style = MaterialTheme.typography.titleSmall)
            }
            items(dealerVisits, key = { "vst-${it.id}" }) { v ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${v.date} • ${v.visitType} (${v.purpose})", style = MaterialTheme.typography.titleSmall)
                        Text(v.discussion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(dealerTrials, key = { "trl-${it.trial.id}" }) { t ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Trial ${t.trial.trialCode}: ${t.trial.trialFeedCode} at ${t.trial.farmName}", style = MaterialTheme.typography.titleSmall)
                        Text("Status: ${t.trial.status} • ${t.trial.remarks}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(dealerComplaints, key = { "cmp-${it.id}" }) { c ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Complaint ${c.complaintCode}: ${c.complaintType} (${c.productName})", style = MaterialTheme.typography.titleSmall)
                        Text("Status: ${c.status} • ${c.description}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showArchiveConfirm) {
        ConfirmDestructiveDialog(
            title = "Archive Dealer ${d.businessName}?",
            message = "Archiving preserves all historical orders, payments, and ledger entries while hiding this dealer from active order selection.",
            confirmText = "Archive Dealer",
            onConfirm = { viewModel.archiveOrDeleteDealer(d, archiveOnly = true) },
            onDismiss = { showArchiveConfirm = false }
        )
    }

    if (showQuickPaymentDialog) {
        var amountStr by remember { mutableStateOf("") }
        var method by remember {
            mutableStateOf(
                uiState.lookups.firstOrNull { it.categoryType == "PAYMENT_METHOD" && !it.isArchived }?.name ?: "Cash"
            )
        }
        var refStr by remember { mutableStateOf("") }
        var remarksStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickPaymentDialog = false },
            title = { Text("Record Payment — ${d.businessName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount ($cur) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownSelectorField(
                        label = "Payment Method",
                        options = uiState.lookups.filter { it.categoryType == "PAYMENT_METHOD" && !it.isArchived }.map { it.name }.ifEmpty { listOf("Cash", "Bank Transfer", "Online", "Cheque") },
                        selectedOption = method,
                        onOptionSelected = { method = it }
                    )
                    OutlinedTextField(
                        value = refStr,
                        onValueChange = { refStr = it },
                        label = { Text("Reference / Receipt / Cheque #") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remarksStr,
                        onValueChange = { remarksStr = it },
                        label = { Text("Remarks") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.savePayment(
                                PaymentEntity(
                                    paymentNumber = "PAY-2026-${uiState.payments.size + 501}",
                                    date = SampleDataSeeder.todayDate(),
                                    dealerId = d.id,
                                    dealerName = d.businessName,
                                    amount = amt,
                                    paymentMethod = method,
                                    referenceNumber = refStr.trim(),
                                    remarks = remarksStr.trim()
                                )
                            )
                            showQuickPaymentDialog = false
                        }
                    }
                ) {
                    Text("Save Payment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showQuickPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FarmersListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onAddFarmer: () -> Unit,
    onEditFarmer: (FarmerEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var areaFilter by remember { mutableStateOf("All") }

    val areas = remember(uiState.lookups) {
        listOf("All") + uiState.lookups.filter { it.categoryType == "AREA" && !it.isArchived }.map { it.name }
    }

    val filteredFarmers = remember(uiState.farmers, searchQuery, areaFilter) {
        uiState.farmers.filter { f ->
            !f.isArchived &&
                (areaFilter == "All" || f.area.equals(areaFilter, true)) &&
                (searchQuery.isBlank() ||
                    f.name.contains(searchQuery, true) ||
                    f.farmName.contains(searchQuery, true) ||
                    f.code.contains(searchQuery, true) ||
                    f.mobile.contains(searchQuery, true))
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFarmer,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Farmer") },
                text = { Text("Add Farmer") },
                modifier = Modifier.testTag("add_farmer_fab")
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
                    placeholder = { Text("Search farmer name, farm name, mobile, ID...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                DropdownSelectorField(
                    label = "Filter by Area",
                    options = areas,
                    selectedOption = areaFilter,
                    onOptionSelected = { areaFilter = it }
                )
            }
            items(filteredFarmers, key = { it.id }) { f ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openFarmerDetail(f.id) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${f.farmName} (${f.code})", style = MaterialTheme.typography.titleMedium)
                                Text("${f.name} • ${f.village}, ${f.city} (${f.area})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(f.status)
                        }
                        Text(
                            "Herd: ${f.animalCount} Animals • Current Feed: ${f.currentFeed} • Monthly: ${f.monthlyConsumptionBags} bags",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (f.dealerName.isNotBlank()) {
                            Text("Linked Dealer: ${f.dealerName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                IconButton(onClick = { DocumentAndExportManager.dialPhoneNumber(context, f.mobile) }) {
                                    Icon(Icons.Default.Call, contentDescription = "Call Farmer", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { DocumentAndExportManager.openWhatsAppChat(context, f.whatsapp.ifBlank { f.mobile }, "Assalam-o-Alaikum ${f.name} (${f.farmName})") }) {
                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp Farmer", tint = StatusSuccess)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEditFarmer(f) }) { Text("Edit") }
                                Button(onClick = { viewModel.openFarmerDetail(f.id) }) { Text("Profile & Trials") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmerProfileDetailView(
    farmer: FarmerEntity,
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onEdit: () -> Unit
) {
    val customFields = remember(farmer.customFieldsJson) { BusinessCalculations.parseCustomFields(farmer.customFieldsJson) }
    val farmerTrials = remember(uiState.trialsWithMilk, farmer.id) {
        uiState.trialsWithMilk.filter { it.trial.farmerId == farmer.id }
    }
    val farmerVisits = remember(uiState.visits, farmer.id) {
        uiState.visits.filter { it.partnerType == "Farmer" && it.partnerId == farmer.id }
    }
    val farmerOrders = remember(uiState.ordersWithItems, farmer.id) {
        uiState.ordersWithItems.filter { it.order.farmerId == farmer.id }
    }

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
                TextButton(onClick = { viewModel.openFarmerDetail(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back to Farmers")
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Farmer")
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(farmer.farmName, style = MaterialTheme.typography.headlineSmall)
                            Text("${farmer.name} • ${farmer.code}", style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusBadge(farmer.status)
                    }
                    Text("Location: ${farmer.address}, ${farmer.village}, ${farmer.city} (${farmer.area})", style = MaterialTheme.typography.bodySmall)
                    Text("Animals: ${farmer.animalCount} • Current Feed: ${farmer.currentFeed} • Monthly Consumption: ${farmer.monthlyConsumptionBags} bags", style = MaterialTheme.typography.bodySmall)
                    if (farmer.dealerName.isNotBlank()) {
                        Text("Supplied by Dealer: ${farmer.dealerName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (customFields.isNotEmpty()) {
                        Text(customFields.entries.joinToString(" • ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (farmer.notes.isNotBlank()) {
                        Text("Notes: ${farmer.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Feed Trials & Milk Production Records (${farmerTrials.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(farmerTrials, key = { it.trial.id }) { twm ->
            val analytics = BusinessCalculations.computeTrialAnalytics(twm)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.openFarmerDetail(null)
                        viewModel.navigateToSubModule(SubModule.TRIALS)
                        viewModel.openTrialDetail(twm.trial.id)
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${twm.trial.trialCode} • ${twm.trial.trialFeedCode}", style = MaterialTheme.typography.titleSmall)
                        StatusBadge(twm.trial.status)
                    }
                    Text(
                        "Start Avg: ${BusinessCalculations.formatDecimal(analytics.startingAvgMilk, 1)} L -> Current Avg: ${BusinessCalculations.formatDecimal(analytics.currentAvgMilk, 1)} L (${if (analytics.changeLiters >= 0) "+" else ""}${BusinessCalculations.formatDecimal(analytics.changeLiters, 1)} L)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Milk Records Logged: ${twm.milkRecords.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Text("Visits (${farmerVisits.size}) & Direct Orders (${farmerOrders.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(farmerVisits, key = { "fv-${it.id}" }) { v ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${v.date} • ${v.visitType}: ${v.purpose}", style = MaterialTheme.typography.titleSmall)
                    Text(v.discussion, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DealerFormDialog(
    initialDealer: DealerEntity?,
    existingCount: Int,
    areas: List<String>,
    dealerTypes: List<String>,
    customFieldDefs: List<CustomFieldDefEntity>,
    onDismiss: () -> Unit,
    onSave: (DealerEntity) -> Unit
) {
    var code by remember { mutableStateOf(initialDealer?.code ?: "DLR-00${existingCount + 1}") }
    var name by remember { mutableStateOf(initialDealer?.name ?: "") }
    var businessName by remember { mutableStateOf(initialDealer?.businessName ?: "") }
    var mobile by remember { mutableStateOf(initialDealer?.mobile ?: "") }
    var whatsapp by remember { mutableStateOf(initialDealer?.whatsapp ?: "") }
    var address by remember { mutableStateOf(initialDealer?.address ?: "") }
    var village by remember { mutableStateOf(initialDealer?.village ?: "") }
    var city by remember { mutableStateOf(initialDealer?.city ?: "") }
    var area by remember { mutableStateOf(initialDealer?.area ?: areas.firstOrNull() ?: "Sahiwal North") }
    var dealerType by remember { mutableStateOf(initialDealer?.dealerType ?: dealerTypes.firstOrNull() ?: "Wholesale Distributor") }
    var openingBalance by remember { mutableStateOf(initialDealer?.openingBalance?.toInt()?.toString() ?: "0") }
    var creditLimit by remember { mutableStateOf(initialDealer?.creditLimit?.toInt()?.toString() ?: "500000") }
    var salesPerson by remember { mutableStateOf(initialDealer?.assignedSalesPerson ?: "Regional Sales Manager") }
    var status by remember { mutableStateOf(initialDealer?.status ?: "Active") }
    var notes by remember { mutableStateOf(initialDealer?.notes ?: "") }
    var customMap by remember { mutableStateOf(BusinessCalculations.parseCustomFields(initialDealer?.customFieldsJson ?: "{}")) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDealer == null) "Add New Dealer" else "Edit Dealer ${initialDealer.code}") },
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
                        label = { Text("Dealer ID *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Status",
                        options = listOf("Active", "Potential", "Inactive", "Blocked"),
                        selectedOption = status,
                        onOptionSelected = { status = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Agency Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Owner / Dealer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownSelectorField(
                    label = "Area / Territory",
                    options = areas.ifEmpty { listOf("Sahiwal North", "Okara Central", "Kasur East") },
                    selectedOption = area,
                    onOptionSelected = { area = it }
                )
                DropdownSelectorField(
                    label = "Dealer Type",
                    options = dealerTypes.ifEmpty { listOf("Wholesale Distributor", "Sub-Dealer", "Direct Farm Agency") },
                    selectedOption = dealerType,
                    onOptionSelected = { dealerType = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("Village / Adda") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop / Godown Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = openingBalance,
                        onValueChange = { openingBalance = it },
                        label = { Text("Opening Bal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text("Credit Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = salesPerson,
                    onValueChange = { salesPerson = it },
                    label = { Text("Assigned Sales Person") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                CustomFieldsFormSection(
                    definitions = customFieldDefs,
                    values = customMap,
                    onValueChange = { k, v ->
                        customMap = customMap.toMutableMap().apply { put(k, v) }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (businessName.isBlank() || name.isBlank() || mobile.length < 7) {
                        errorMsg = "Business Name, Dealer Name, and valid Mobile Number are required."
                        return@Button
                    }
                    onSave(
                        DealerEntity(
                            id = initialDealer?.id ?: 0,
                            code = code.trim(),
                            name = name.trim(),
                            businessName = businessName.trim(),
                            mobile = mobile.trim(),
                            whatsapp = whatsapp.trim().ifBlank { mobile.trim() },
                            address = address.trim(),
                            village = village.trim(),
                            city = city.trim().ifBlank { area },
                            area = area,
                            dealerType = dealerType,
                            openingBalance = openingBalance.toDoubleOrNull() ?: 0.0,
                            creditLimit = (creditLimit.toDoubleOrNull() ?: 500000.0).coerceAtLeast(0.0),
                            assignedSalesPerson = salesPerson.trim(),
                            dateAdded = initialDealer?.dateAdded ?: SampleDataSeeder.todayDate(),
                            status = status,
                            notes = notes.trim(),
                            customFieldsJson = BusinessCalculations.serializeCustomFields(customMap)
                        )
                    )
                }
            ) {
                Text("Save Dealer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FarmerFormDialog(
    initialFarmer: FarmerEntity?,
    existingCount: Int,
    areas: List<String>,
    dealers: List<DealerEntity>,
    products: List<ProductEntity>,
    customFieldDefs: List<CustomFieldDefEntity>,
    onDismiss: () -> Unit,
    onSave: (FarmerEntity) -> Unit
) {
    var code by remember { mutableStateOf(initialFarmer?.code ?: "FRM-00${existingCount + 1}") }
    var name by remember { mutableStateOf(initialFarmer?.name ?: "") }
    var farmName by remember { mutableStateOf(initialFarmer?.farmName ?: "") }
    var mobile by remember { mutableStateOf(initialFarmer?.mobile ?: "") }
    var whatsapp by remember { mutableStateOf(initialFarmer?.whatsapp ?: "") }
    var address by remember { mutableStateOf(initialFarmer?.address ?: "") }
    var village by remember { mutableStateOf(initialFarmer?.village ?: "") }
    var city by remember { mutableStateOf(initialFarmer?.city ?: "") }
    var area by remember { mutableStateOf(initialFarmer?.area ?: areas.firstOrNull() ?: "Sahiwal North") }
    var animalCount by remember { mutableStateOf(initialFarmer?.animalCount?.toString() ?: "30") }
    var currentFeed by remember { mutableStateOf(initialFarmer?.currentFeed ?: (products.firstOrNull()?.code ?: "26P")) }
    var monthlyBags by remember { mutableStateOf(initialFarmer?.monthlyConsumptionBags?.toString() ?: "45") }
    var selectedDealerName by remember { mutableStateOf(initialFarmer?.dealerName ?: (dealers.firstOrNull()?.businessName ?: "")) }
    var status by remember { mutableStateOf(initialFarmer?.status ?: "Active") }
    var notes by remember { mutableStateOf(initialFarmer?.notes ?: "") }
    var customMap by remember { mutableStateOf(BusinessCalculations.parseCustomFields(initialFarmer?.customFieldsJson ?: "{}")) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialFarmer == null) "Add New Farmer" else "Edit Farmer ${initialFarmer.code}") },
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
                        label = { Text("Farmer ID *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Status",
                        options = listOf("Active", "Potential", "Inactive"),
                        selectedOption = status,
                        onOptionSelected = { status = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = farmName,
                    onValueChange = { farmName = it },
                    label = { Text("Dairy Farm Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Farmer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = animalCount,
                        onValueChange = { animalCount = it },
                        label = { Text("Animals *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthlyBags,
                        onValueChange = { monthlyBags = it },
                        label = { Text("Monthly Bags") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = currentFeed,
                    onValueChange = { currentFeed = it },
                    label = { Text("Current Feed Used") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Linked Dealer",
                    options = dealers.map { it.businessName }.ifEmpty { listOf("Direct Company Supply") },
                    selectedOption = selectedDealerName,
                    onOptionSelected = { selectedDealerName = it }
                )
                DropdownSelectorField(
                    label = "Area Territory",
                    options = areas.ifEmpty { listOf("Sahiwal North", "Okara Central") },
                    selectedOption = area,
                    onOptionSelected = { area = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("Village / Chak") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Farm Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                CustomFieldsFormSection(
                    definitions = customFieldDefs,
                    values = customMap,
                    onValueChange = { k, v ->
                        customMap = customMap.toMutableMap().apply { put(k, v) }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val animals = animalCount.toIntOrNull() ?: 0
                    if (name.isBlank() || farmName.isBlank() || animals <= 0) {
                        errorMsg = "Farmer Name, Farm Name, and positive Animal Count are required."
                        return@Button
                    }
                    val dealerMatch = dealers.firstOrNull { it.businessName == selectedDealerName }
                    onSave(
                        FarmerEntity(
                            id = initialFarmer?.id ?: 0,
                            code = code.trim(),
                            name = name.trim(),
                            mobile = mobile.trim(),
                            whatsapp = whatsapp.trim().ifBlank { mobile.trim() },
                            farmName = farmName.trim(),
                            address = address.trim(),
                            village = village.trim(),
                            city = city.trim().ifBlank { area },
                            area = area,
                            animalCount = animals,
                            currentFeed = currentFeed.trim(),
                            monthlyConsumptionBags = (monthlyBags.toIntOrNull() ?: 0).coerceAtLeast(0),
                            dealerId = dealerMatch?.id,
                            dealerName = selectedDealerName,
                            dateAdded = initialFarmer?.dateAdded ?: SampleDataSeeder.todayDate(),
                            status = status,
                            notes = notes.trim(),
                            customFieldsJson = BusinessCalculations.serializeCustomFields(customMap)
                        )
                    )
                }
            ) {
                Text("Save Farmer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
