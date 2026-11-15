package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var selectedFilterPill by remember { mutableStateOf("All Dealers") }

    val filteredDealers = remember(uiState.dealerSummaries, searchQuery, selectedFilterPill) {
        uiState.dealerSummaries.filter { ds ->
            val d = ds.dealer
            val matchFilter = when (selectedFilterPill) {
                "Active" -> !d.isArchived && d.status.equals("Active", true)
                "With Balance" -> ds.outstandingBalance > 0.0
                "Archived" -> d.isArchived
                else -> !d.isArchived
            }
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                d.name.lowercase().contains(q) ||
                d.businessName.lowercase().contains(q) ||
                d.code.lowercase().contains(q) ||
                d.mobile.contains(q) ||
                d.city.lowercase().contains(q) ||
                d.area.lowercase().contains(q)
            matchFilter && matchSearch
        }.sortedByDescending { it.totalBags }
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
                            "Search Dealer (Name / Area)",
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
                        .testTag("dealer_search_input")
                )
            }

            // 2. Filter Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterPills = listOf("All Dealers", "Active", "With Balance", "Archived")
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

            // 3. Dealer Cards or Empty State
            if (filteredDealers.isEmpty()) {
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
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color(0xFF0F5233),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "No dealers found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Tap '+ Add Dealer' below to register a new agency or feed dealer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(filteredDealers, key = { it.dealer.id }) { summary ->
                    val d = summary.dealer
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openDealerDetail(d.id) }
                            .testTag("dealer_card_${d.code}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Circular Store Emblem
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Middle Info Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = d.businessName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${d.city}${if (d.area.isNotBlank()) ", " + d.area else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = d.mobile,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Right Column (MTD Sales)
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "MTD Sales",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "${summary.totalBags} Bags",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom "+ Add Dealer" Pill Button
        Button(
            onClick = onAddDealer,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .height(48.dp)
                .testTag("add_dealer_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Dealer",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var activeTab by remember { mutableStateOf("Overview") }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showQuickPaymentDialog by remember { mutableStateOf(false) }
    var showFullReportExportDialog by remember { mutableStateOf(false) }

    val dealerOrders = remember(uiState.ordersWithItems, d.id) {
        uiState.ordersWithItems.filter { it.order.dealerId == d.id }.sortedByDescending { it.order.date }
    }
    val dealerPayments = remember(uiState.payments, d.id) {
        uiState.payments.filter { it.dealerId == d.id }
            .distinctBy { it.paymentNumber.ifBlank { "${it.id}" } }
            .sortedByDescending { it.date }
    }
    val dealerTrials = remember(uiState.trialsWithMilk, d.id) {
        uiState.trialsWithMilk.filter { it.trial.dealerId == d.id }
    }
    val dealerComplaints = remember(uiState.complaints, d.id) {
        uiState.complaints.filter { it.partnerType == "Dealer" && it.partnerId == d.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Dealer Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.openDealerDetail(null) }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFullReportExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Report",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Dealer",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F5233)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAF9))
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Dealer Header Card (Matching Concept Screen 5)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Circular Store Emblem
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Agency Name & Details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = d.businessName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${d.address.ifBlank { d.city }}, ${d.city}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = d.mobile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    // Quick Call & WhatsApp circular buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F5233))
                                                .clickable { DocumentAndExportManager.dialPhoneNumber(context, d.mobile) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Call",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF25D366))
                                                .clickable {
                                                    DocumentAndExportManager.openWhatsAppChat(
                                                        context,
                                                        d.whatsapp.ifBlank { d.mobile },
                                                        "Assalam-o-Alaikum ${d.name} (${d.businessName}), WandaBook statement update: Current Balance is ${BusinessCalculations.formatCurrency(summary.outstandingBalance, cur)}."
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = "WhatsApp",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Capsule Tab Bar (Overview, Statement & Report, Orders, Payments, Visits)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Overview", "Statement & Report", "Orders", "Payments", "Visits")
                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            color = if (isSelected) Color(0xFF0F5233) else Color.White,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF0F5233) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable { activeTab = tab }
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Tab Contents
            when (activeTab) {
                "Overview" -> {
                    // 2x2 Stats Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Total Orders
                                ProfileStatCard(
                                    title = "Total Orders",
                                    value = "${summary.totalOrders}",
                                    icon = Icons.Default.ReceiptLong,
                                    iconBgColor = Color(0xFFF1F5F9),
                                    iconColor = Color(0xFF475569),
                                    modifier = Modifier.weight(1f)
                                )

                                // 2. Total Purchase
                                ProfileStatCard(
                                    title = "Total Purchase",
                                    value = "${summary.totalBags} Bags",
                                    subtitle = "(${BusinessCalculations.formatDecimal(summary.totalMt)} MT)",
                                    icon = Icons.Default.ShoppingBag,
                                    iconBgColor = Color(0xFFF0FDF4),
                                    iconColor = Color(0xFF15803D),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 3. Total Value
                                ProfileStatCard(
                                    title = "Total Value",
                                    value = BusinessCalculations.formatCurrency(summary.totalSales, cur),
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconBgColor = Color(0xFFEFF6FF),
                                    iconColor = Color(0xFF2563EB),
                                    modifier = Modifier.weight(1f)
                                )

                                // 4. Outstanding
                                ProfileStatCard(
                                    title = "Outstanding",
                                    value = BusinessCalculations.formatCurrency(summary.outstandingBalance, cur),
                                    valueColor = if (summary.outstandingBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D),
                                    icon = Icons.Default.WarningAmber,
                                    iconBgColor = if (summary.outstandingBalance > 0) Color(0xFFFEE2E2) else Color(0xFFF0FDF4),
                                    iconColor = if (summary.outstandingBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Recent Orders Card (Matching Screen 5)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Orders",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "View All →",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF0F5233),
                                        modifier = Modifier.clickable { activeTab = "Orders" }
                                    )
                                }

                                if (dealerOrders.isEmpty()) {
                                    Text(
                                        text = "No orders recorded for this dealer yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    dealerOrders.take(5).forEachIndexed { index, owi ->
                                        val o = owi.order
                                        val payStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.openInvoicePreview(o.id) }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = o.orderNumber,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp
                                                ),
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${o.totalBags} Bags",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color(0xFF64748B)
                                            )
                                            CleanStatusBadge(status = o.status)
                                        }
                                        if (index < dealerOrders.take(5).size - 1) {
                                            HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons (Record Payment, Download Report, Archive)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showQuickPaymentDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Record Payment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showFullReportExportDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F5233)),
                                border = BorderStroke(1.dp, Color(0xFF0F5233)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Report", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showArchiveConfirm = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Archive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                "Statement & Report" -> {
                    item {
                        PartnerStatementReportContent(
                            partnerName = d.businessName,
                            partnerCode = d.code,
                            partnerType = "Dealer",
                            partnerPhone = d.mobile,
                            partnerArea = d.area,
                            openingBalance = d.openingBalance,
                            orders = dealerOrders,
                            payments = dealerPayments,
                            config = uiState.config,
                            onOpenInvoice = { viewModel.openInvoicePreview(it) }
                        )
                    }
                }

                "Orders" -> {
                    if (dealerOrders.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Orders",
                                message = "There are no orders recorded for this dealer yet."
                            )
                        }
                    } else {
                        items(dealerOrders, key = { it.order.id }) { owi ->
                            val o = owi.order
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openInvoicePreview(o.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(o.orderNumber, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text("${o.date} • ${o.totalBags} Bags (${BusinessCalculations.formatDecimal(o.totalMt)} MT)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = BusinessCalculations.formatCurrency(o.netAmount, cur),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0F172A)
                                        )
                                        CleanStatusBadge(status = o.status)
                                    }
                                }
                            }
                        }
                    }
                }

                "Payments" -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ledger History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export CSV")
                            }
                        }
                    }

                    if (summary.ledgerEntries.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Transactions",
                                message = "No debit or credit ledger records found for this dealer."
                            )
                        }
                    } else {
                        items(summary.ledgerEntries, key = { it.id }) { row ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${row.date} • ${row.referenceCode}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                        Text(row.description, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF0F172A))
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (row.debit > 0) {
                                            Text(
                                                "Dr +${BusinessCalculations.formatCurrency(row.debit, cur)}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                                color = Color(0xFFDC2626)
                                            )
                                        }
                                        if (row.credit > 0) {
                                            Text(
                                                "Cr -${BusinessCalculations.formatCurrency(row.credit, cur)}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                        Text(
                                            "Bal: ${BusinessCalculations.formatCurrency(row.runningBalance, cur)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                            color = Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "Visits" -> {
                    item {
                        Text(
                            text = "Field Operations & Trials",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                    }

                    if (dealerTrials.isEmpty() && dealerComplaints.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Field Operations",
                                message = "No feed trials or feedback complaints registered for this dealer."
                            )
                        }
                    } else {
                        items(dealerTrials, key = { "trl-${it.trial.id}" }) { t ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Trial: ${t.trial.trialFeedCode}", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        CleanStatusBadge(status = t.trial.status)
                                    }
                                    Text("Farm: ${t.trial.farmName} • Date: ${t.trial.startDate}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                    if (t.trial.remarks.isNotBlank()) {
                                        Text(t.trial.remarks, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                                    }
                                }
                            }
                        }

                        items(dealerComplaints, key = { "cmp-${it.id}" }) { c ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Complaint: ${c.complaintCode}", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        CleanStatusBadge(status = c.status)
                                    }
                                    Text("${c.complaintType} • ${c.productName}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                    Text(c.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                                }
                            }
                        }
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
            onConfirm = {
                viewModel.archiveOrDeleteDealer(d, archiveOnly = true)
                showArchiveConfirm = false
                viewModel.openDealerDetail(null)
            },
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
            title = { Text("Record Payment — ${d.businessName}", fontWeight = FontWeight.Bold) },
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233))
                ) {
                    Text("Save Payment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showQuickPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFullReportExportDialog) {
        PartnerReportExportDialog(
            partnerName = d.businessName,
            partnerCode = d.code,
            partnerType = "Dealer",
            partnerPhone = d.mobile,
            partnerArea = d.area,
            openingBalance = d.openingBalance,
            orders = dealerOrders,
            payments = dealerPayments,
            config = uiState.config,
            onDismiss = { showFullReportExportDialog = false }
        )
    }
}

@Composable
private fun ProfileStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = Color(0xFF0F172A),
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                ),
                color = valueColor
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1E293B)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun CleanStatusBadge(status: String) {
    val (bgColor, fgColor) = when (status.lowercase()) {
        "delivered", "paid", "active", "completed" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        "partial", "partially delivered" -> Pair(Color(0xFFFFEDD5), Color(0xFFC2410C))
        "pending", "in transit", "planned" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
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
    val farmerOrders = remember(uiState.ordersWithItems, farmer.id) {
        uiState.ordersWithItems.filter { it.order.farmerId == farmer.id || it.order.farmerName.equals(farmer.name, true) }
    }
    var showFarmerReportDialog by remember { mutableStateOf(false) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showFarmerReportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Report", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Farmer")
                    }
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

        // --- FULL DOWNLOADABLE STATEMENT & REPORT ---
        item {
            Text(
                "Full Statement & Downloadable Report",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A)
            )
        }
        item {
            PartnerStatementReportContent(
                partnerName = "${farmer.name} (${farmer.farmName})",
                partnerCode = farmer.code,
                partnerType = "Farmer",
                partnerPhone = farmer.mobile,
                partnerArea = farmer.area,
                openingBalance = 0.0,
                orders = farmerOrders,
                payments = emptyList(),
                config = uiState.config,
                onOpenInvoice = { viewModel.openInvoicePreview(it) }
            )
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
    }

    if (showFarmerReportDialog) {
        PartnerReportExportDialog(
            partnerName = "${farmer.name} (${farmer.farmName})",
            partnerCode = farmer.code,
            partnerType = "Farmer",
            partnerPhone = farmer.mobile,
            partnerArea = farmer.area,
            openingBalance = 0.0,
            orders = farmerOrders,
            payments = emptyList(),
            config = uiState.config,
            onDismiss = { showFarmerReportDialog = false }
        )
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
                // Area / Territory (Fully Editable with suggestions & dropdown)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var areaDropdownExpanded by remember { mutableStateOf(false) }
                    val availableAreas = remember(areas) {
                        (areas + listOf(
                            "Sahiwal North",
                            "Sahiwal South",
                            "Okara Central",
                            "Kasur East",
                            "Pakpattan",
                            "Faisalabad West",
                            "Chichawatni"
                        )).distinct()
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = { Text("Area / Territory * (Editable)") },
                            placeholder = { Text("e.g. Sahiwal North, Okara...") },
                            trailingIcon = {
                                IconButton(onClick = { areaDropdownExpanded = !areaDropdownExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select or Type Area")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = areaDropdownExpanded,
                            onDismissRequest = { areaDropdownExpanded = false }
                        ) {
                            availableAreas.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a) },
                                    onClick = {
                                        area = a
                                        areaDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quick area suggestion chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableAreas.take(5).forEach { a ->
                            Surface(
                                onClick = { area = a },
                                shape = RoundedCornerShape(8.dp),
                                color = if (area == a) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (area == a) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = a,
                                    fontSize = 11.sp,
                                    fontWeight = if (area == a) FontWeight.Bold else FontWeight.Normal,
                                    color = if (area == a) Color(0xFF15803D) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
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
                // Area Territory (Fully Editable with suggestions & dropdown)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var farmerAreaDropdownExpanded by remember { mutableStateOf(false) }
                    val availableAreas = remember(areas) {
                        (areas + listOf(
                            "Sahiwal North",
                            "Sahiwal South",
                            "Okara Central",
                            "Kasur East",
                            "Pakpattan",
                            "Faisalabad West",
                            "Chichawatni"
                        )).distinct()
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = { Text("Area / Territory * (Editable)") },
                            placeholder = { Text("e.g. Sahiwal North, Okara...") },
                            trailingIcon = {
                                IconButton(onClick = { farmerAreaDropdownExpanded = !farmerAreaDropdownExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select or Type Area")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = farmerAreaDropdownExpanded,
                            onDismissRequest = { farmerAreaDropdownExpanded = false }
                        ) {
                            availableAreas.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a) },
                                    onClick = {
                                        area = a
                                        farmerAreaDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quick area suggestion chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableAreas.take(5).forEach { a ->
                            Surface(
                                onClick = { area = a },
                                shape = RoundedCornerShape(8.dp),
                                color = if (area == a) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (area == a) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = a,
                                    fontSize = 11.sp,
                                    fontWeight = if (area == a) FontWeight.Bold else FontWeight.Normal,
                                    color = if (area == a) Color(0xFF15803D) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun PartnerStatementReportContent(
    partnerName: String,
    partnerCode: String,
    partnerType: String,
    partnerPhone: String,
    partnerArea: String,
    openingBalance: Double,
    orders: List<OrderWithItems>,
    payments: List<PaymentEntity>,
    config: BusinessConfigEntity,
    onOpenInvoice: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("Overall") }

    val statementRows = remember(selectedPeriod, orders, payments, openingBalance) {
        BusinessCalculations.buildPartnerStatementRows(
            orders = orders,
            payments = payments,
            openingBalance = openingBalance,
            period = selectedPeriod
        )
    }

    val totalPurchased = remember(statementRows) {
        statementRows.filter { it.type == "PURCHASE" }.sumOf { it.billAmount }
    }
    val totalReceived = remember(statementRows) {
        statementRows.sumOf { it.amountReceived }
    }
    val totalBags = remember(statementRows) {
        statementRows.filter { it.type == "PURCHASE" }.sumOf { it.bags }
    }
    val totalMt = remember(statementRows) {
        statementRows.filter { it.type == "PURCHASE" }.sumOf { it.totalMt }
    }
    val netBalance = statementRows.lastOrNull()?.runningBalance ?: openingBalance
    val cur = config.currencySymbol

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Period Selector Chips (Overall, Monthly, Weekly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Pair("Overall", "Overall (All-Time)"),
                Pair("Monthly", "Monthly (This Month)"),
                Pair("Weekly", "Weekly (Last 7 Days)")
            ).forEach { (key, label) ->
                val isSelected = selectedPeriod == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedPeriod = key },
                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0F5233),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // 2. Action Buttons (Download PDF, Export CSV, WhatsApp, Print)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val pdfFile = DocumentAndExportManager.generatePartnerStatementPdfFile(
                        context = context,
                        config = config,
                        partnerName = partnerName,
                        partnerCode = partnerCode,
                        partnerType = partnerType,
                        partnerPhone = partnerPhone,
                        partnerArea = partnerArea,
                        periodLabel = when (selectedPeriod) {
                            "Monthly" -> "Monthly Statement"
                            "Weekly" -> "Weekly Statement"
                            else -> "Overall Complete Statement"
                        },
                        totalPurchasedAmount = totalPurchased,
                        totalPaidAmount = totalReceived,
                        netBalance = netBalance,
                        totalBags = totalBags,
                        totalMt = totalMt,
                        rows = statementRows
                    )
                    DocumentAndExportManager.shareFile(context, pdfFile, "application/pdf", "Share $partnerType Statement PDF")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download PDF", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val csvFile = DocumentAndExportManager.exportPartnerStatementCsv(
                        context = context,
                        partnerType = partnerType,
                        partnerCode = partnerCode,
                        partnerName = partnerName,
                        periodLabel = selectedPeriod,
                        rows = statementRows
                    )
                    DocumentAndExportManager.shareFile(context, csvFile, "text/csv", "Share $partnerType Statement CSV")
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export CSV")
            }

            OutlinedButton(
                onClick = {
                    val msg = buildString {
                        appendLine("Assalam-o-Alaikum $partnerName ($partnerCode),")
                        appendLine("WandaBook Pro Statement Summary (${if (selectedPeriod == "Monthly") "Monthly" else if (selectedPeriod == "Weekly") "Weekly" else "Overall"}):")
                        appendLine("• Total Feed Purchased: $totalBags Bags (${BusinessCalculations.formatDecimal(totalMt)} MT) = $cur ${BusinessCalculations.formatCurrency(totalPurchased, "")}")
                        appendLine("• Total Amount Received: $cur ${BusinessCalculations.formatCurrency(totalReceived, "")}")
                        appendLine("• Outstanding Net Balance: $cur ${BusinessCalculations.formatCurrency(netBalance, "")}")
                    }
                    DocumentAndExportManager.openWhatsAppChat(context, partnerPhone, msg)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("WhatsApp")
            }

            OutlinedButton(
                onClick = {
                    val pdfFile = DocumentAndExportManager.generatePartnerStatementPdfFile(
                        context = context,
                        config = config,
                        partnerName = partnerName,
                        partnerCode = partnerCode,
                        partnerType = partnerType,
                        partnerPhone = partnerPhone,
                        partnerArea = partnerArea,
                        periodLabel = "$selectedPeriod Statement",
                        totalPurchasedAmount = totalPurchased,
                        totalPaidAmount = totalReceived,
                        netBalance = netBalance,
                        totalBags = totalBags,
                        totalMt = totalMt,
                        rows = statementRows
                    )
                    DocumentAndExportManager.printPdfFile(context, pdfFile, "$partnerType Statement $partnerCode")
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print")
            }
        }

        // 3. Summary Metric Cards (2x2 Grid)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Purchased", style = MaterialTheme.typography.labelSmall, color = Color(0xFF15803D))
                        Text(
                            BusinessCalculations.formatCurrency(totalPurchased, cur),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                            color = Color(0xFF0F172A)
                        )
                        Text("$totalBags Bags (${BusinessCalculations.formatDecimal(totalMt)} MT)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Amount Received", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1D4ED8))
                        Text(
                            BusinessCalculations.formatCurrency(totalReceived, cur),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                            color = Color(0xFF0F172A)
                        )
                        Text("Collections & Receipts", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (netBalance > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (netBalance > 0) Color(0xFFFECACA) else Color(0xFFBBF7D0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Net Balance", style = MaterialTheme.typography.labelSmall, color = if (netBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D))
                        Text(
                            BusinessCalculations.formatCurrency(netBalance, cur),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                            color = if (netBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                        )
                        Text(if (netBalance > 0) "Payable to Mill" else "Account Settled", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Transactions", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text(
                            "${statementRows.size} Entries",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Text("Full ledger audit trail", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    }
                }
            }
        }

        // 4. Itemized Records List
        Text(
            text = "Detailed Transaction Records (${statementRows.size})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF0F172A)
        )

        if (statementRows.isEmpty()) {
            EmptyStateCard(
                title = "No Transactions Found",
                message = "There are no purchases or payments in this $selectedPeriod period."
            )
        } else {
            statementRows.forEach { row ->
                PartnerReportItemCard(
                    row = row,
                    cur = cur,
                    onOpenInvoice = onOpenInvoice
                )
            }
        }
    }
}

@Composable
private fun PartnerReportItemCard(
    row: PartnerStatementRow,
    cur: String,
    onOpenInvoice: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: WHEN (Date) • Ref Code • Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "📅 ${row.date}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "• ${row.refCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
                CleanStatusBadge(status = if (row.type == "PURCHASE") "Order: " + row.dispatchStatus else row.type)
            }

            // Middle: WHAT PURCHASED (or Payment info)
            if (row.type == "PURCHASE") {
                Text(
                    text = "📦 What Purchased: ${row.productsSummary}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF0F5233)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🚚 Feed Send: ${row.feedSendDate} (${row.dispatchStatus})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "${row.bags} Bags (${BusinessCalculations.formatDecimal(row.totalMt)} MT)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF334155)
                    )
                }
            } else {
                Text(
                    text = "💳 ${row.productsSummary}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1D4ED8)
                )
                Text(
                    text = "When Received: ${row.paymentReceivedDate} • Mode: ${row.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Bottom: Financial figures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.billAmount > 0) {
                    Column {
                        Text("Bill Amount", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text(
                            BusinessCalculations.formatCurrency(row.billAmount, cur),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                            color = Color(0xFFDC2626)
                        )
                    }
                }
                if (row.amountReceived > 0) {
                    Column {
                        Text("Amount Received", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                        Text(
                            BusinessCalculations.formatCurrency(row.amountReceived, cur),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                            color = Color(0xFF15803D)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Balance", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    Text(
                        BusinessCalculations.formatCurrency(row.runningBalance, cur),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily),
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerReportExportDialog(
    partnerName: String,
    partnerCode: String,
    partnerType: String,
    partnerPhone: String,
    partnerArea: String,
    openingBalance: Double,
    orders: List<OrderWithItems>,
    payments: List<PaymentEntity>,
    config: BusinessConfigEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("Overall") }

    val rows = remember(selectedPeriod, orders, payments, openingBalance) {
        BusinessCalculations.buildPartnerStatementRows(
            orders = orders,
            payments = payments,
            openingBalance = openingBalance,
            period = selectedPeriod
        )
    }

    val totalPurchased = remember(rows) { rows.filter { it.type == "PURCHASE" }.sumOf { it.billAmount } }
    val totalReceived = remember(rows) { rows.sumOf { it.amountReceived } }
    val totalBags = remember(rows) { rows.filter { it.type == "PURCHASE" }.sumOf { it.bags } }
    val totalMt = remember(rows) { rows.filter { it.type == "PURCHASE" }.sumOf { it.totalMt } }
    val netBalance = rows.lastOrNull()?.runningBalance ?: openingBalance
    val cur = config.currencySymbol

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF0F5233))
                Text("Download $partnerType Report", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select period to generate and download comprehensive statement with purchase, dispatch, payment & balance records for $partnerName ($partnerCode):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )

                // Period pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair("Overall", "Overall"),
                        Pair("Monthly", "Monthly"),
                        Pair("Weekly", "Weekly")
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedPeriod == key,
                            onClick = { selectedPeriod = key },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0F5233),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Summary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF9)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Feed:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text("$totalBags Bags (${BusinessCalculations.formatDecimal(totalMt)} MT)", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Purchased Value:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text(BusinessCalculations.formatCurrency(totalPurchased, cur), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount Received:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text(BusinessCalculations.formatCurrency(totalReceived, cur), fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        }
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Balance:", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text(BusinessCalculations.formatCurrency(netBalance, cur), fontWeight = FontWeight.Bold, color = if (netBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D))
                        }
                    }
                }

                // Export Options
                Button(
                    onClick = {
                        val pdfFile = DocumentAndExportManager.generatePartnerStatementPdfFile(
                            context = context,
                            config = config,
                            partnerName = partnerName,
                            partnerCode = partnerCode,
                            partnerType = partnerType,
                            partnerPhone = partnerPhone,
                            partnerArea = partnerArea,
                            periodLabel = "$selectedPeriod Statement",
                            totalPurchasedAmount = totalPurchased,
                            totalPaidAmount = totalReceived,
                            netBalance = netBalance,
                            totalBags = totalBags,
                            totalMt = totalMt,
                            rows = rows
                        )
                        onDismiss()
                        DocumentAndExportManager.shareFile(context, pdfFile, "application/pdf", "Share $partnerType Statement PDF")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download PDF Statement")
                }

                OutlinedButton(
                    onClick = {
                        val csvFile = DocumentAndExportManager.exportPartnerStatementCsv(
                            context = context,
                            partnerType = partnerType,
                            partnerCode = partnerCode,
                            partnerName = partnerName,
                            periodLabel = selectedPeriod,
                            rows = rows
                        )
                        onDismiss()
                        DocumentAndExportManager.shareFile(context, csvFile, "text/csv", "Share $partnerType Statement CSV")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV Spreadsheet")
                }

                OutlinedButton(
                    onClick = {
                        val msg = buildString {
                            appendLine("Assalam-o-Alaikum $partnerName ($partnerCode),")
                            appendLine("WandaBook Pro Statement Summary (${if (selectedPeriod == "Monthly") "Monthly" else if (selectedPeriod == "Weekly") "Weekly" else "Overall"}):")
                            appendLine("• Total Feed Purchased: $totalBags Bags (${BusinessCalculations.formatDecimal(totalMt)} MT) = $cur ${BusinessCalculations.formatCurrency(totalPurchased, "")}")
                            appendLine("• Total Amount Received: $cur ${BusinessCalculations.formatCurrency(totalReceived, "")}")
                            appendLine("• Outstanding Net Balance: $cur ${BusinessCalculations.formatCurrency(netBalance, "")}")
                        }
                        onDismiss()
                        DocumentAndExportManager.openWhatsAppChat(context, partnerPhone, msg)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share on WhatsApp")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
