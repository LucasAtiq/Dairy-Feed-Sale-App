package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun FieldOpsScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    triggerNewTrial: Boolean = false,
    triggerNewExpense: Boolean = false,
    onConsumeTrigger: () -> Unit = {}
) {
    var showTrialDialog by remember { mutableStateOf(false) }
    var editingTrial by remember { mutableStateOf<FeedTrialEntity?>(null) }

    var showComplaintDialog by remember { mutableStateOf(false) }
    var editingComplaint by remember { mutableStateOf<ComplaintEntity?>(null) }

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTravelDialog by remember { mutableStateOf(false) }
    var showVisitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(triggerNewTrial, triggerNewExpense) {
        if (triggerNewTrial) {
            editingTrial = null
            showTrialDialog = true
            onConsumeTrigger()
        }
        if (triggerNewExpense) {
            showExpenseDialog = true
            onConsumeTrigger()
        }
    }

    val selectedTrialWithMilk = uiState.trialsWithMilk.firstOrNull { it.trial.id == uiState.selectedTrialForDetail }
    if (selectedTrialWithMilk != null) {
        TrialDetailAndMilkTrackingView(
            trialWithMilk = selectedTrialWithMilk,
            uiState = uiState,
            viewModel = viewModel,
            onEditTrial = {
                editingTrial = selectedTrialWithMilk.trial
                showTrialDialog = true
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SubModulePillBar(
                modules = listOf(
                    SubModule.VISITS,
                    SubModule.TRIALS,
                    SubModule.COMPLAINTS,
                    SubModule.EXPENSES_TRAVEL
                ),
                selected = uiState.activeSubModule,
                onSelect = { viewModel.navigateToSubModule(it) }
            )

            when (uiState.activeSubModule) {
                SubModule.COMPLAINTS -> ComplaintsListSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNewComplaint = {
                        editingComplaint = null
                        showComplaintDialog = true
                    },
                    onEditComplaint = {
                        editingComplaint = it
                        showComplaintDialog = true
                    }
                )
                SubModule.EXPENSES_TRAVEL -> ExpensesAndTravelSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNewExpense = { showExpenseDialog = true },
                    onNewTravel = { showTravelDialog = true }
                )
                SubModule.TRIALS -> FeedTrialsListSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNewTrial = {
                        editingTrial = null
                        showTrialDialog = true
                    },
                    onEditTrial = {
                        editingTrial = it
                        showTrialDialog = true
                    }
                )
                else -> FieldVisitsListSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onAddVisit = { showVisitDialog = true }
                )
            }
        }
    }

    if (showTrialDialog) {
        FeedTrialFormDialog(
            initialTrial = editingTrial,
            existingCount = uiState.trialsWithMilk.size,
            farmers = uiState.farmers.filter { !it.isArchived },
            dealers = uiState.dealers.filter { !it.isArchived },
            products = uiState.products.filter { !it.isArchived },
            customFieldDefs = uiState.customFieldDefs.filter { it.entityType == "TRIAL" },
            onDismiss = { showTrialDialog = false },
            onSave = { saved ->
                viewModel.saveFeedTrial(saved)
                showTrialDialog = false
            }
        )
    }

    if (showComplaintDialog) {
        ComplaintFormDialog(
            initialComplaint = editingComplaint,
            existingCount = uiState.complaints.size,
            dealers = uiState.dealers.filter { !it.isArchived },
            farmers = uiState.farmers.filter { !it.isArchived },
            products = uiState.products.filter { !it.isArchived },
            complaintTypes = uiState.lookups.filter { it.categoryType == "COMPLAINT_TYPE" && !it.isArchived }.map { it.name },
            onDismiss = { showComplaintDialog = false },
            onSave = { saved ->
                viewModel.saveComplaint(saved)
                showComplaintDialog = false
            }
        )
    }

    if (showExpenseDialog) {
        ExpenseFormDialog(
            currencySymbol = uiState.config.currencySymbol,
            categories = uiState.lookups.filter { it.categoryType == "EXPENSE_CATEGORY" && !it.isArchived }.map { it.name },
            paymentMethods = uiState.lookups.filter { it.categoryType == "PAYMENT_METHOD" && !it.isArchived }.map { it.name },
            onDismiss = { showExpenseDialog = false },
            onSave = { saved ->
                viewModel.saveExpense(saved)
                showExpenseDialog = false
            }
        )
    }

    if (showTravelDialog) {
        val lastEndOdo = uiState.travelLogs.firstOrNull()?.endingOdometer ?: 48345.0
        TravelOdometerFormDialog(
            defaultStartOdometer = lastEndOdo,
            onDismiss = { showTravelDialog = false },
            onSave = { saved ->
                viewModel.saveTravelLog(saved)
                showTravelDialog = false
            }
        )
    }

    if (showVisitDialog) {
        AddVisitDialog(
            dealers = uiState.dealers.filter { !it.isArchived },
            farmers = uiState.farmers.filter { !it.isArchived },
            onDismiss = { showVisitDialog = false },
            onSave = { saved ->
                viewModel.saveTravelLog(saved)
                showVisitDialog = false
            }
        )
    }
}

private data class FieldVisitData(
    val id: Long,
    val title: String,
    val date: String,
    val remarks: String,
    val status: String,
    val area: String,
    val partnerName: String
)

@Composable
private fun FieldVisitsListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onAddVisit: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterPill by remember { mutableStateOf("All Visits") }

    // Combine travel logs and trials into unified field visit representations
    val visitsList = remember(uiState.travelLogs, uiState.trialsWithMilk) {
        val travelVisits = uiState.travelLogs.map { t ->
            val isPlanned = t.totalKm <= 0.0 && t.remarks.contains("Planned", ignoreCase = true)
            FieldVisitData(
                id = t.id,
                title = if (t.purpose.isNotBlank()) "${t.destination} – ${t.purpose}" else "${t.destination} – Field Visit",
                date = t.date,
                remarks = t.remarks.ifBlank { "Travel odometer: ${t.startingOdometer.toInt()} → ${t.endingOdometer.toInt()} KM" },
                status = if (isPlanned) "Planned" else "Completed",
                area = t.destination,
                partnerName = t.destination
            )
        }
        val trialVisits = uiState.trialsWithMilk.map { twm ->
            val tr = twm.trial
            FieldVisitData(
                id = 100000L + tr.id,
                title = "${tr.area.ifBlank { "Farm" }} – Farm Visit",
                date = tr.startDate,
                remarks = "Farm visit, feed trial follow-up (${tr.trialFeedCode}). ${tr.remarks}",
                status = if (tr.status.equals("Active", true) || tr.status.equals("Completed", true)) "Completed" else "Planned",
                area = tr.area,
                partnerName = tr.farmName
            )
        }
        (travelVisits + trialVisits).sortedByDescending { it.date }
    }

    val filteredVisits = remember(visitsList, searchQuery, selectedFilterPill) {
        visitsList.filter { v ->
            val matchFilter = when (selectedFilterPill) {
                "Planned" -> v.status.equals("Planned", true)
                "Completed" -> v.status.equals("Completed", true)
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                v.title.lowercase().contains(q) ||
                v.area.lowercase().contains(q) ||
                v.partnerName.lowercase().contains(q) ||
                v.remarks.lowercase().contains(q) ||
                v.date.contains(q)
            matchFilter && matchSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAF9)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar with Filter Icon (Matching Screen 6)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search by Farmer, Dealer or Area",
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
                            modifier = Modifier.clickable { /* Filter */ }
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
                        .testTag("visits_search_input")
                )
            }

            // 2. Filter Pills (All Visits, Planned, Completed)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pills = listOf("All Visits", "Planned", "Completed")
                    pills.forEach { pill ->
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
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Visit Cards (Timeline Style)
            if (filteredVisits.isEmpty()) {
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
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF0F5233),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "No field visits recorded",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "Tap '+ Add Visit' below to record or schedule a farm/dealer visit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(filteredVisits, key = { it.id }) { visit ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Circular Location Pin Emblem
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Middle Info Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = visit.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Status Badge (Completed: soft green, Planned: soft amber)
                                    val (badgeBg, badgeFg) = if (visit.status.equals("Completed", true)) {
                                        Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
                                    } else {
                                        Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                                    }
                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = visit.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = visit.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                if (visit.remarks.isNotBlank()) {
                                    Text(
                                        text = visit.remarks,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF475569),
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom "+ Add Visit" Pill Button
        Button(
            onClick = onAddVisit,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .height(48.dp)
                .testTag("add_field_visit_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Visit",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun AddVisitDialog(
    dealers: List<DealerEntity>,
    farmers: List<FarmerEntity>,
    onDismiss: () -> Unit,
    onSave: (TravelLogEntity) -> Unit
) {
    var area by remember { mutableStateOf("Gojra") }
    var visitType by remember { mutableStateOf("Dealer Visit") }
    var selectedPartner by remember { mutableStateOf(dealers.firstOrNull()?.businessName ?: "Mustafa Zarai & Wanda Agency") }
    var date by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
    var status by remember { mutableStateOf("Completed") }
    var remarks by remember { mutableStateOf("Met dealer, discussed monthly sales targets.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule / Record Field Visit", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("Area / Territory (e.g. Gojra, Kamalia)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownSelectorField(
                    label = "Visit Type",
                    options = listOf("Dealer Visit", "Farm Visit", "Follow-up", "Feed Trial Check"),
                    selectedOption = visitType,
                    onOptionSelected = { visitType = it }
                )

                OutlinedTextField(
                    value = selectedPartner,
                    onValueChange = { selectedPartner = it },
                    label = { Text("Dealer / Farm Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Visit Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownSelectorField(
                    label = "Status",
                    options = listOf("Completed", "Planned"),
                    selectedOption = status,
                    onOptionSelected = { status = it }
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Discussion / Trial / Order Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val log = TravelLogEntity(
                        date = date.trim(),
                        startingOdometer = 0.0,
                        endingOdometer = if (status == "Completed") 25.0 else 0.0,
                        startingLocation = "Base",
                        destination = area.trim(),
                        totalKm = if (status == "Completed") 25.0 else 0.0,
                        workingKm = if (status == "Completed") 25.0 else 0.0,
                        personalKm = 0.0,
                        purpose = visitType,
                        remarks = "$selectedPartner: $remarks (Status: $status)"
                    )
                    onSave(log)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5233))
            ) {
                Text("Save Visit")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FeedTrialsListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onNewTrial: () -> Unit,
    onEditTrial: (FeedTrialEntity) -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(uiState.trialsWithMilk, statusFilter, searchQuery) {
        uiState.trialsWithMilk.filter { twm ->
            val t = twm.trial
            val matchStatus = statusFilter == "All" || t.status.equals(statusFilter, true)
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                t.trialCode.lowercase().contains(q) ||
                t.farmerName.lowercase().contains(q) ||
                t.farmName.lowercase().contains(q) ||
                t.trialFeedCode.lowercase().contains(q)
            matchStatus && matchSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrial,
                icon = { Icon(Icons.Default.Science, contentDescription = "New Feed Trial") },
                text = { Text("New Feed Trial") },
                modifier = Modifier.testTag("new_feed_trial_fab")
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
                    placeholder = { Text("Search trial ID, farmer, farm, feed code (28P, 55M)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Planned", "Started", "Transition", "Under Observation", "Completed", "Successful", "Unsuccessful", "Cancelled").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) }
                        )
                    }
                }
            }

            items(filtered, key = { it.trial.id }) { twm ->
                val t = twm.trial
                val analytics = BusinessCalculations.computeTrialAnalytics(twm)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openTrialDetail(t.id) }
                        .testTag("trial_card_${t.trialCode}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${t.trialCode} • ${t.farmName}", style = MaterialTheme.typography.titleMedium)
                                Text("Farmer: ${t.farmerName} • Dealer: ${t.dealerName.ifBlank { "Direct" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(t.status)
                        }
                        Text(
                            "Feed Comparison: ${t.currentFeedCode} -> ${t.trialFeedCode} (${t.animalCount} Animals • Transition: ${t.transitionDays}d)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Start Avg Milk", style = MaterialTheme.typography.labelSmall)
                                    Text("${BusinessCalculations.formatDecimal(analytics.startingAvgMilk, 1)} L", style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily))
                                }
                                Column {
                                    Text("Current Avg Milk", style = MaterialTheme.typography.labelSmall)
                                    Text("${BusinessCalculations.formatDecimal(analytics.currentAvgMilk, 1)} L", style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Milk Change", style = MaterialTheme.typography.labelSmall)
                                    val sign = if (analytics.changeLiters >= 0) "+" else ""
                                    Text(
                                        "$sign${BusinessCalculations.formatDecimal(analytics.changeLiters, 1)} L ($sign${BusinessCalculations.formatDecimal(analytics.percentageChange, 1)}%)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                        color = if (analytics.changeLiters >= 0) StatusSuccess else StatusDanger
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${twm.milkRecords.size} Milk Weighing Records • ${t.startDate} to ${t.endDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEditTrial(t) }) { Text("Edit") }
                                Button(onClick = { viewModel.openTrialDetail(t.id) }) { Text("Milk Chart & Log") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrialDetailAndMilkTrackingView(
    trialWithMilk: TrialWithMilkRecords,
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onEditTrial: () -> Unit
) {
    val t = trialWithMilk.trial
    val analytics = remember(trialWithMilk) { BusinessCalculations.computeTrialAnalytics(trialWithMilk) }
    val customFields = remember(t.customFieldsJson) { BusinessCalculations.parseCustomFields(t.customFieldsJson) }
    var showAddMilkDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trial_detail_milk_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.openTrialDetail(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back to Trials")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditTrial) { Text("Edit Trial") }
                    Button(onClick = { showAddMilkDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Milk Entry")
                    }
                }
            }
        }

        // Trial Header & Analytics Summary
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${t.trialCode} • ${t.farmName}", style = MaterialTheme.typography.headlineSmall)
                            Text("Farmer: ${t.farmerName} • Location: ${t.location}", style = MaterialTheme.typography.bodyMedium)
                        }
                        StatusBadge(t.status)
                    }
                    Text(
                        "Current Feed: ${t.currentFeed} (${t.currentFeedCode}) -> Trial Feed: ${t.trialFeed} (${t.trialFeedCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Animals in Trial: ${t.animalCount} (Tracked Tags: ${analytics.distinctAnimals.joinToString(", ").ifBlank { "None yet" }}) • Duration: ${analytics.durationDays} Days",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (customFields.isNotEmpty()) {
                        Text(customFields.entries.joinToString(" • ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (t.remarks.isNotBlank()) {
                        Text("Remarks: ${t.remarks}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 6 Automatic Milk Production Calculations
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatCard(
                        title = "Starting Avg Milk",
                        value = "${BusinessCalculations.formatDecimal(analytics.startingAvgMilk, 2)} L",
                        subtitle = "Baseline before trial",
                        icon = Icons.Default.WaterDrop,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Current / Final Avg Milk",
                        value = "${BusinessCalculations.formatDecimal(analytics.currentAvgMilk, 2)} L",
                        subtitle = "Latest daily average",
                        icon = Icons.Default.LocalDrink,
                        accentColor = StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val sign = if (analytics.changeLiters >= 0) "+" else ""
                    MetricStatCard(
                        title = "Increase / Decrease",
                        value = "$sign${BusinessCalculations.formatDecimal(analytics.changeLiters, 2)} L/cow",
                        subtitle = "Percentage: $sign${BusinessCalculations.formatDecimal(analytics.percentageChange, 1)}%",
                        icon = Icons.Default.TrendingUp,
                        accentColor = if (analytics.changeLiters >= 0) StatusSuccess else StatusDanger,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Trial Duration",
                        value = "${analytics.durationDays} Days",
                        subtitle = "Transition: ${t.transitionDays} Days",
                        icon = Icons.Default.CalendarMonth,
                        accentColor = StatusInfo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Date vs Milk Production Chart
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Date vs Milk Production Chart (Liters / Animal)", style = MaterialTheme.typography.titleMedium)
                    if (analytics.dailyChartPoints.isEmpty()) {
                        Text("Add daily milk entries to visualize Date vs Milk Production curve.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        MilkProductionLineChart(
                            points = analytics.dailyChartPoints,
                            baselineMilk = analytics.startingAvgMilk,
                            primaryColor = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            analytics.dailyChartPoints.forEach { pt ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(pt.date.takeLast(5), style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "${BusinessCalculations.formatDecimal(pt.avgMilkLiters, 1)}L",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Individual Animal Daily Milk Records List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Animal Daily Milk Records (${trialWithMilk.milkRecords.size})", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showAddMilkDialog = true }) {
                    Text("+ Log Daily Milk")
                }
            }
        }

        items(trialWithMilk.milkRecords.sortedByDescending { it.recordDate }, key = { it.id }) { rec ->
            val diff = rec.dailyMilkLiters - rec.startingMilkLiters
            val sign = if (diff >= 0) "+" else ""
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animal: ${rec.animalIdTag} • Date: ${rec.recordDate}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Start Milk: ${rec.startingMilkLiters} L | Feed Qty: ${rec.feedQuantityKg} KG",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (rec.remarks.isNotBlank()) {
                            Text(rec.remarks, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${rec.dailyMilkLiters} L",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "$sign${BusinessCalculations.formatDecimal(diff, 1)} L",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = if (diff >= 0) StatusSuccess else StatusDanger
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMilkRecord(rec) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Record", tint = StatusDanger)
                        }
                    }
                }
            }
        }
    }

    if (showAddMilkDialog) {
        var animalTag by remember { mutableStateOf(analytics.distinctAnimals.firstOrNull() ?: "HF-101") }
        var recordDate by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
        var startMilkStr by remember {
            val prev = trialWithMilk.milkRecords.firstOrNull { it.animalIdTag == animalTag }?.startingMilkLiters ?: 20.0
            mutableStateOf(prev.toString())
        }
        var dailyMilkStr by remember { mutableStateOf("24.5") }
        var feedKgStr by remember { mutableStateOf("8.5") }
        var remarksStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMilkDialog = false },
            title = { Text("Add Daily Milk Entry — ${t.trialCode}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = animalTag,
                        onValueChange = { tag ->
                            animalTag = tag
                            trialWithMilk.milkRecords.firstOrNull { it.animalIdTag.equals(tag, true) }?.let {
                                startMilkStr = it.startingMilkLiters.toString()
                            }
                        },
                        label = { Text("Animal ID / Tag (e.g. HF-101, BUF-21) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recordDate,
                        onValueChange = { recordDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startMilkStr,
                            onValueChange = { startMilkStr = it },
                            label = { Text("Starting Milk (L)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dailyMilkStr,
                            onValueChange = { dailyMilkStr = it },
                            label = { Text("Daily Milk (L) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = feedKgStr,
                        onValueChange = { feedKgStr = it },
                        label = { Text("Feed Quantity Given (KG)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remarksStr,
                        onValueChange = { remarksStr = it },
                        label = { Text("Remarks / Fat % / Observations") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val daily = dailyMilkStr.toDoubleOrNull() ?: 0.0
                        val start = startMilkStr.toDoubleOrNull() ?: daily
                        val feed = feedKgStr.toDoubleOrNull() ?: 0.0
                        if (animalTag.isNotBlank() && daily > 0.0) {
                            viewModel.saveMilkRecord(
                                MilkRecordEntity(
                                    trialId = t.id,
                                    animalIdTag = animalTag.trim(),
                                    recordDate = recordDate.trim(),
                                    startingMilkLiters = start,
                                    dailyMilkLiters = daily,
                                    feedQuantityKg = feed,
                                    remarks = remarksStr.trim()
                                )
                            )
                            showAddMilkDialog = false
                        }
                    }
                ) {
                    Text("Save Milk Entry")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddMilkDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MilkProductionLineChart(
    points: List<DailyMilkPoint>,
    baselineMilk: Double,
    primaryColor: Color
) {
    val minVal = (points.minOfOrNull { it.avgMilkLiters } ?: 0.0).coerceAtMost(baselineMilk) - 2.0
    val maxVal = (points.maxOfOrNull { it.avgMilkLiters } ?: 30.0).coerceAtLeast(baselineMilk) + 2.0
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(primaryColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        val w = size.width
        val h = size.height

        // Draw baseline dashed/reference line
        val baseY = h - (((baselineMilk - minVal) / range).toFloat() * h)
        drawLine(
            color = Color.Gray.copy(alpha = 0.45f),
            start = Offset(0f, baseY),
            end = Offset(w, baseY),
            strokeWidth = 2f
        )

        if (points.size == 1) {
            val y = h - (((points[0].avgMilkLiters - minVal) / range).toFloat() * h)
            drawCircle(color = primaryColor, radius = 8f, center = Offset(w / 2f, y))
        } else if (points.size > 1) {
            val path = Path()
            val stepX = w / (points.size - 1).coerceAtLeast(1)
            points.forEachIndexed { idx, pt ->
                val x = idx * stepX
                val y = h - (((pt.avgMilkLiters - minVal) / range).toFloat() * h)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
            points.forEachIndexed { idx, pt ->
                val x = idx * stepX
                val y = h - (((pt.avgMilkLiters - minVal) / range).toFloat() * h)
                drawCircle(color = Color.White, radius = 8f, center = Offset(x, y))
                drawCircle(color = primaryColor, radius = 6f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun ComplaintsListSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onNewComplaint: () -> Unit,
    onEditComplaint: (ComplaintEntity) -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") }
    val filtered = remember(uiState.complaints, statusFilter) {
        uiState.complaints.filter { statusFilter == "All" || it.status.equals(statusFilter, true) }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewComplaint,
                icon = { Icon(Icons.Default.ReportProblem, contentDescription = "Log Complaint") },
                text = { Text("New Complaint") }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Open", "Under Investigation", "Follow-up", "Resolved", "Closed").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) }
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { c ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${c.complaintCode} • ${c.partnerName} (${c.partnerType})", style = MaterialTheme.typography.titleMedium)
                                Text("${c.date} • Product: ${c.productName} • Type: ${c.complaintType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(c.status)
                        }
                        Text("Description: ${c.description}", style = MaterialTheme.typography.bodyMedium)
                        if (c.investigation.isNotBlank()) {
                            Text("Investigation: ${c.investigation}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (c.actionTaken.isNotBlank()) {
                            Text("Action Taken: ${c.actionTaken}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (c.resolution.isNotBlank()) {
                            Text("Resolution: ${c.resolution}", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Follow-up: ${c.followUpDate.ifBlank { "None" }}", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(onClick = { onEditComplaint(c) }) { Text("Update / Resolve") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesAndTravelSection(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onNewExpense: () -> Unit,
    onNewTravel: () -> Unit
) {
    val cur = uiState.config.currencySymbol
    val today = SampleDataSeeder.todayDate()
    val monthPrefix = SampleDataSeeder.currentMonthPrefix()

    val dailyExpenseTotal = uiState.expenses.filter { it.date == today }.sumOf { it.amount }
    val monthlyExpenseTotal = uiState.expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount }
    val totalAllExpenses = uiState.expenses.sumOf { it.amount }
    val totalTravelKm = uiState.travelLogs.sumOf { it.totalKm }
    val totalWorkingKm = uiState.travelLogs.sumOf { it.workingKm }

    val categoryBreakdown = remember(uiState.expenses) {
        uiState.expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewExpense, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Expense")
                }
                Button(onClick = onNewTravel, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Odometer Log")
                }
            }
        }

        // Expense & Travel KPI Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricStatCard(
                        title = "Today's Expenses",
                        value = BusinessCalculations.formatCurrency(dailyExpenseTotal, cur),
                        subtitle = "All time: ${BusinessCalculations.formatCurrency(totalAllExpenses, cur)}",
                        icon = Icons.Default.Today,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Monthly Expenses",
                        value = BusinessCalculations.formatCurrency(monthlyExpenseTotal, cur),
                        subtitle = "${categoryBreakdown.size} active categories",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
                MetricStatCard(
                    title = "Travel & Odometer Distance",
                    value = "${totalTravelKm.roundToInt()} KM Total (${totalWorkingKm.roundToInt()} Working KM)",
                    subtitle = "Automatically calculated: Ending Odometer - Starting Odometer",
                    icon = Icons.Default.DirectionsCar,
                    accentColor = StatusInfo
                )
            }
        }

        // Category-wise Expense Summary
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category-wise Expenses", style = MaterialTheme.typography.titleSmall)
                    categoryBreakdown.entries.forEach { (cat, sum) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, style = MaterialTheme.typography.bodyMedium)
                            Text(BusinessCalculations.formatCurrency(sum, cur), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        item {
            Text("Travel / Odometer Logs (${uiState.travelLogs.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.travelLogs, key = { "trv-${it.id}" }) { log ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${log.date} • ${log.startingLocation} -> ${log.destination}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${log.totalKm.roundToInt()} KM",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Odometer: ${log.startingOdometer.roundToInt()} -> ${log.endingOdometer.roundToInt()} | Working: ${log.workingKm.roundToInt()} KM | Personal: ${log.personalKm.roundToInt()} KM",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                    )
                    Text("Purpose: ${log.purpose}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text("Field Expense Entries (${uiState.expenses.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.expenses, key = { "exp-${it.id}" }) { exp ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.category} • ${exp.date} (${exp.location})", style = MaterialTheme.typography.titleSmall)
                        Text("${exp.description} • Paid via ${exp.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            BusinessCalculations.formatCurrency(exp.amount, cur),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = StatusDanger
                        )
                        IconButton(onClick = { viewModel.deleteExpense(exp) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Expense", tint = StatusDanger)
                        }
                    }
                }
            }
        }
    }
}

// --- Field Ops Dialogs ---

@Composable
private fun FeedTrialFormDialog(
    initialTrial: FeedTrialEntity?,
    existingCount: Int,
    farmers: List<FarmerEntity>,
    dealers: List<DealerEntity>,
    products: List<ProductEntity>,
    customFieldDefs: List<CustomFieldDefEntity>,
    onDismiss: () -> Unit,
    onSave: (FeedTrialEntity) -> Unit
) {
    var trialCode by remember { mutableStateOf(initialTrial?.trialCode ?: "TRL-2026-00${existingCount + 1}") }
    var selFarmerName by remember { mutableStateOf(initialTrial?.farmerName ?: (farmers.firstOrNull()?.name ?: "")) }
    var selDealerName by remember { mutableStateOf(initialTrial?.dealerName ?: (dealers.firstOrNull()?.businessName ?: "")) }
    var farmName by remember { mutableStateOf(initialTrial?.farmName ?: (farmers.firstOrNull()?.farmName ?: "")) }
    var location by remember { mutableStateOf(initialTrial?.location ?: (farmers.firstOrNull()?.city ?: "Sahiwal")) }
    var animalCountStr by remember { mutableStateOf(initialTrial?.animalCount?.toString() ?: "5") }
    var currentFeedCode by remember { mutableStateOf(initialTrial?.currentFeedCode ?: "26P") }
    var trialFeedCode by remember { mutableStateOf(initialTrial?.trialFeedCode ?: (products.firstOrNull()?.code ?: "28P")) }
    var startDate by remember { mutableStateOf(initialTrial?.startDate ?: SampleDataSeeder.todayDate()) }
    var transitionDaysStr by remember { mutableStateOf(initialTrial?.transitionDays?.toString() ?: "7") }
    var endDate by remember { mutableStateOf(initialTrial?.endDate ?: SampleDataSeeder.daysOffset(14)) }
    var status by remember { mutableStateOf(initialTrial?.status ?: "Started") }
    var remarks by remember { mutableStateOf(initialTrial?.remarks ?: "") }
    var customMap by remember { mutableStateOf(BusinessCalculations.parseCustomFields(initialTrial?.customFieldsJson ?: "{}")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTrial == null) "Start New Feed Trial" else "Edit Trial ${initialTrial.trialCode}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = trialCode,
                        onValueChange = { trialCode = it },
                        label = { Text("Trial ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Status",
                        options = listOf("Planned", "Started", "Transition", "Under Observation", "Completed", "Successful", "Unsuccessful", "Cancelled"),
                        selectedOption = status,
                        onOptionSelected = { status = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownSelectorField(
                    label = "Farmer",
                    options = farmers.map { it.name }.ifEmpty { listOf("Progressive Dairy Farmer") },
                    selectedOption = selFarmerName,
                    onOptionSelected = { fName ->
                        selFarmerName = fName
                        farmers.firstOrNull { it.name == fName }?.let { f ->
                            farmName = f.farmName
                            location = "${f.village}, ${f.city}"
                            if (f.dealerName.isNotBlank()) selDealerName = f.dealerName
                        }
                    }
                )
                OutlinedTextField(
                    value = farmName,
                    onValueChange = { farmName = it },
                    label = { Text("Farm Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Linked Dealer",
                    options = dealers.map { it.businessName },
                    selectedOption = selDealerName,
                    onOptionSelected = { selDealerName = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = animalCountStr,
                        onValueChange = { animalCountStr = it },
                        label = { Text("Animal Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentFeedCode,
                        onValueChange = { currentFeedCode = it },
                        label = { Text("Current Feed Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Trial Feed Code",
                        options = products.map { it.code }.ifEmpty { listOf("26P", "28P", "55M") },
                        selectedOption = trialFeedCode,
                        onOptionSelected = { trialFeedCode = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = transitionDaysStr,
                        onValueChange = { transitionDaysStr = it },
                        label = { Text("Transition Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("Trial End Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Trial Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
                CustomFieldsFormSection(
                    definitions = customFieldDefs,
                    values = customMap,
                    onValueChange = { k, v -> customMap = customMap.toMutableMap().apply { put(k, v) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val farmerObj = farmers.firstOrNull { it.name == selFarmerName }
                    val dealerObj = dealers.firstOrNull { it.businessName == selDealerName }
                    val trialProd = products.firstOrNull { it.code == trialFeedCode }
                    onSave(
                        FeedTrialEntity(
                            id = initialTrial?.id ?: 0,
                            trialCode = trialCode.trim(),
                            farmerId = farmerObj?.id ?: 1L,
                            farmerName = selFarmerName,
                            dealerId = dealerObj?.id,
                            dealerName = selDealerName,
                            farmName = farmName.trim(),
                            location = location.trim(),
                            animalCount = (animalCountStr.toIntOrNull() ?: 1).coerceAtLeast(1),
                            currentFeed = currentFeedCode.trim(),
                            trialFeed = trialProd?.name ?: trialFeedCode,
                            currentFeedCode = currentFeedCode.trim(),
                            trialFeedCode = trialFeedCode,
                            startDate = startDate.trim(),
                            transitionDays = (transitionDaysStr.toIntOrNull() ?: 7).coerceAtLeast(1),
                            endDate = endDate.trim(),
                            status = status,
                            remarks = remarks.trim(),
                            customFieldsJson = BusinessCalculations.serializeCustomFields(customMap)
                        )
                    )
                }
            ) {
                Text("Save Trial")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ComplaintFormDialog(
    initialComplaint: ComplaintEntity?,
    existingCount: Int,
    dealers: List<DealerEntity>,
    farmers: List<FarmerEntity>,
    products: List<ProductEntity>,
    complaintTypes: List<String>,
    onDismiss: () -> Unit,
    onSave: (ComplaintEntity) -> Unit
) {
    var code by remember { mutableStateOf(initialComplaint?.complaintCode ?: "CMP-2026-00${existingCount + 1}") }
    var date by remember { mutableStateOf(initialComplaint?.date ?: SampleDataSeeder.todayDate()) }
    var partnerName by remember { mutableStateOf(initialComplaint?.partnerName ?: (dealers.firstOrNull()?.businessName ?: "")) }
    var productName by remember { mutableStateOf(initialComplaint?.productName ?: (products.firstOrNull()?.name ?: "26P")) }
    var complaintType by remember { mutableStateOf(initialComplaint?.complaintType ?: (complaintTypes.firstOrNull() ?: "Milk Yield Variation")) }
    var description by remember { mutableStateOf(initialComplaint?.description ?: "") }
    var milkChange by remember { mutableStateOf(initialComplaint?.milkChange ?: "") }
    var animalDetails by remember { mutableStateOf(initialComplaint?.animalDetails ?: "") }
    var investigation by remember { mutableStateOf(initialComplaint?.investigation ?: "") }
    var actionTaken by remember { mutableStateOf(initialComplaint?.actionTaken ?: "") }
    var followUpDate by remember { mutableStateOf(initialComplaint?.followUpDate ?: SampleDataSeeder.daysOffset(2)) }
    var status by remember { mutableStateOf(initialComplaint?.status ?: "Open") }
    var resolution by remember { mutableStateOf(initialComplaint?.resolution ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialComplaint == null) "Log Complaint" else "Update Complaint ${initialComplaint.complaintCode}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Complaint ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelectorField(
                        label = "Status",
                        options = listOf("Open", "Under Investigation", "Follow-up", "Resolved", "Closed"),
                        selectedOption = status,
                        onOptionSelected = { status = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownSelectorField(
                    label = "Dealer / Farmer",
                    options = dealers.map { it.businessName } + farmers.map { "${it.name} (${it.farmName})" },
                    selectedOption = partnerName,
                    onOptionSelected = { partnerName = it }
                )
                DropdownSelectorField(
                    label = "Product",
                    options = products.map { "${it.code} - ${it.name}" },
                    selectedOption = productName,
                    onOptionSelected = { productName = it }
                )
                DropdownSelectorField(
                    label = "Complaint Type",
                    options = complaintTypes.ifEmpty { listOf("Milk Yield Variation", "Palatability", "Moisture", "Bag Damage") },
                    selectedOption = complaintType,
                    onOptionSelected = { complaintType = it }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Complaint Description *") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = milkChange,
                        onValueChange = { milkChange = it },
                        label = { Text("Milk Change") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = animalDetails,
                        onValueChange = { animalDetails = it },
                        label = { Text("Animal Details") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = investigation,
                    onValueChange = { investigation = it },
                    label = { Text("Field Investigation Findings") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = actionTaken,
                    onValueChange = { actionTaken = it },
                    label = { Text("Action Taken") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    label = { Text("Resolution") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = followUpDate,
                    onValueChange = { followUpDate = it },
                    label = { Text("Follow-up Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (description.isBlank()) return@Button
                    onSave(
                        ComplaintEntity(
                            id = initialComplaint?.id ?: 0,
                            complaintCode = code.trim(),
                            date = date.trim(),
                            partnerType = if (dealers.any { it.businessName == partnerName }) "Dealer" else "Farmer",
                            partnerId = 1L,
                            partnerName = partnerName,
                            productName = productName,
                            complaintType = complaintType,
                            description = description.trim(),
                            milkChange = milkChange.trim(),
                            animalDetails = animalDetails.trim(),
                            investigation = investigation.trim(),
                            actionTaken = actionTaken.trim(),
                            followUpDate = followUpDate.trim(),
                            status = status,
                            resolution = resolution.trim()
                        )
                    )
                }
            ) {
                Text("Save Complaint")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExpenseFormDialog(
    currencySymbol: String,
    categories: List<String>,
    paymentMethods: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit
) {
    var date by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "Fuel") }
    var amountStr by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(paymentMethods.firstOrNull() ?: "Cash") }
    var location by remember { mutableStateOf("Sahiwal") }
    var description by remember { mutableStateOf("") }
    var receiptRef by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Field Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelectorField(
                    label = "Expense Category",
                    options = categories.ifEmpty { listOf("Fuel", "Transport", "Food", "Hotel", "Ticket", "Other") },
                    selectedOption = category,
                    onOptionSelected = { category = it }
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
                    options = paymentMethods.ifEmpty { listOf("Cash", "Online", "Bank Transfer") },
                    selectedOption = paymentMethod,
                    onOptionSelected = { paymentMethod = it }
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = receiptRef,
                    onValueChange = { receiptRef = it },
                    label = { Text("Receipt / Voucher Ref") },
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
                        onSave(
                            ExpenseEntity(
                                date = date.trim(),
                                category = category,
                                amount = amt,
                                paymentMethod = paymentMethod,
                                location = location.trim(),
                                description = description.trim().ifBlank { "$category field expense" },
                                receiptReference = receiptRef.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Save Expense")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TravelOdometerFormDialog(
    defaultStartOdometer: Double,
    onDismiss: () -> Unit,
    onSave: (TravelLogEntity) -> Unit
) {
    var date by remember { mutableStateOf(SampleDataSeeder.todayDate()) }
    var startOdoStr by remember { mutableStateOf(defaultStartOdometer.roundToInt().toString()) }
    var endOdoStr by remember { mutableStateOf((defaultStartOdometer.roundToInt() + 110).toString()) }
    var startLoc by remember { mutableStateOf("Sahiwal Office") }
    var destLoc by remember { mutableStateOf("Okara & Renala Khurd Route") }
    var personalKmStr by remember { mutableStateOf("0") }
    var purpose by remember { mutableStateOf("Dealer visits & feed trial milk recording") }

    val startOdo = startOdoStr.toDoubleOrNull() ?: 0.0
    val endOdo = endOdoStr.toDoubleOrNull() ?: 0.0
    val totalKm = (endOdo - startOdo).coerceAtLeast(0.0)
    val personalKm = (personalKmStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalKm)
    val workingKm = (totalKm - personalKm).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Odometer / Field Travel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startOdoStr,
                        onValueChange = { startOdoStr = it },
                        label = { Text("Starting Odometer *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endOdoStr,
                        onValueChange = { endOdoStr = it },
                        label = { Text("Ending Odometer *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Auto Calculated: Total = ${totalKm.roundToInt()} KM (Working: ${workingKm.roundToInt()} KM)",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.padding(10.dp)
                    )
                }
                OutlinedTextField(
                    value = personalKmStr,
                    onValueChange = { personalKmStr = it },
                    label = { Text("Personal KM (if any)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startLoc,
                        onValueChange = { startLoc = it },
                        label = { Text("Start Location") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = destLoc,
                        onValueChange = { destLoc = it },
                        label = { Text("Destination") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endOdo > startOdo) {
                        onSave(
                            TravelLogEntity(
                                date = date.trim(),
                                startingOdometer = startOdo,
                                endingOdometer = endOdo,
                                startingLocation = startLoc.trim(),
                                destination = destLoc.trim(),
                                totalKm = totalKm,
                                workingKm = workingKm,
                                personalKm = personalKm,
                                purpose = purpose.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Save Travel Log")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
