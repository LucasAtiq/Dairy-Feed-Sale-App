package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BusinessCalculations
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun DashboardScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onQuickAction: (String) -> Unit
) {
    val dash = uiState.dashboard
    val cur = uiState.config.currencySymbol
    var smartQueryInput by remember { mutableStateOf("") }
    var smartQueryAnswer by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Banner Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_dairy_hero),
                        contentDescription = "Dairy Farm Field Operations Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xAA072415),
                                        Color(0xEE082E1B)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF15803D),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Offline Ready",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Offline-First Ready • ${uiState.config.activeRole}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.navigateToSubModule(SubModule.SEARCH) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .testTag("hero_global_search_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Global Search",
                                    tint = Color.White
                                )
                            }
                        }

                        Column {
                            Text(
                                text = uiState.config.companyName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = "${uiState.config.businessTitle} • ${uiState.products.count { !it.isArchived }} Active Feed Lines",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD2F5E1)
                            )
                        }
                    }
                }
            }
        }

        // 2. Quick Actions Grid (8 Large One-Handed Mobile Buttons)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Field Actions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "One-Handed Fast Entry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val quickButtons = listOf(
                    Triple("NEW_SALE", "New Sale", Icons.Default.AddShoppingCart),
                    Triple("NEW_DEALER", "New Dealer", Icons.Default.Store),
                    Triple("NEW_FARMER", "New Farmer", Icons.Default.Agriculture),
                    Triple("PAYMENT", "Payment", Icons.Default.Payments),
                    Triple("NEW_VISIT", "New Visit", Icons.Default.Place),
                    Triple("NEW_TRIAL", "New Trial", Icons.Default.Science),
                    Triple("STOCK_IN", "Stock In", Icons.Default.Inventory),
                    Triple("EXPENSE", "Expense", Icons.Default.ReceiptLong)
                )

                quickButtons.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (actionKey, label, icon) ->
                            ElevatedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(78.dp)
                                    .clickable { onQuickAction(actionKey) }
                                    .testTag("quick_action_${actionKey.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Today's Summary Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Today's Field Summary",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Today's Orders",
                        value = "${dash.todayOrdersCount}",
                        subtitle = "${dash.todayBags} Bags booked",
                        icon = Icons.Default.ShoppingBag,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                    MetricStatCard(
                        title = "Today's Bags",
                        value = "${dash.todayBags}",
                        subtitle = "${BusinessCalculations.formatDecimal(dash.todayBags * 40.0 / 1000.0)} MT est.",
                        icon = Icons.Default.Scale,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.CONSOLIDATED) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Today's Sales",
                        value = BusinessCalculations.formatCurrency(dash.todaySales, cur),
                        subtitle = "Net invoiced today",
                        icon = Icons.Default.TrendingUp,
                        accentColor = StatusSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                    MetricStatCard(
                        title = "Today's Collection",
                        value = BusinessCalculations.formatCurrency(dash.todayCollection, cur),
                        subtitle = "Cash & Bank received",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = StatusInfo,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.PAYMENTS) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Today's Visits",
                        value = "${dash.todayVisitsCount}",
                        subtitle = "Field visits logged",
                        icon = Icons.Default.DirectionsWalk,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.VISITS) }
                    )
                    MetricStatCard(
                        title = "Pending Deliveries",
                        value = "${dash.pendingDeliveriesCount}",
                        subtitle = "Awaiting dispatch",
                        icon = Icons.Default.LocalShipping,
                        accentColor = StatusWarning,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                }
            }
        }

        // 4. Monthly Summary & Target Achievement Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Performance & Target",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Target: ${dash.monthlyTargetBags} Bags • Achieved: ${BusinessCalculations.formatDecimal(dash.achievementPercentBags, 1)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.navigateToSubModule(SubModule.TARGETS) }) {
                            Text("Edit Target")
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (dash.achievementPercentBags.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Monthly Bags", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${dash.monthlyBags} Bags", style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily))
                        }
                        Column {
                            Text("Monthly MT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${BusinessCalculations.formatDecimal(dash.monthlyMt)} MT", style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Remaining Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${dash.remainingTargetBags} Bags", style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily))
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monthly Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                BusinessCalculations.formatCurrency(dash.monthlySales, cur),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = StatusSuccess
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monthly Collection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                BusinessCalculations.formatCurrency(dash.monthlyCollection, cur),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = StatusInfo
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Outstanding Payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                BusinessCalculations.formatCurrency(dash.totalOutstandingPayments, cur),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = StatusDanger
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Est. Gross Profit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                BusinessCalculations.formatCurrency(dash.monthlyGrossProfit, cur),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 5. Alerts & Actionable Field Reminders
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alerts & Field Reminders (${uiState.smartReminders.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { viewModel.navigateToSubModule(SubModule.REMINDERS) }) {
                        Text("View All")
                    }
                }

                // Alert Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertSummaryPill(
                        label = "Overdue Dealers: ${dash.overdueDealersCount}",
                        color = StatusDanger,
                        onClick = { viewModel.navigateToSubModule(SubModule.PAYMENTS) }
                    )
                    AlertSummaryPill(
                        label = "Pending Deliveries: ${dash.pendingDeliveriesCount}",
                        color = StatusWarning,
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                    AlertSummaryPill(
                        label = "Active Feed Trials: ${dash.pendingTrialsCount}",
                        color = StatusInfo,
                        onClick = { viewModel.navigateToSubModule(SubModule.TRIALS) }
                    )
                    AlertSummaryPill(
                        label = "Follow-ups Due: ${dash.followUpDueCount}",
                        color = StatusWarning,
                        onClick = { viewModel.navigateToSubModule(SubModule.VISITS) }
                    )
                    AlertSummaryPill(
                        label = "Low Stock: ${dash.lowStockProductsCount}",
                        color = StatusDanger,
                        onClick = { viewModel.navigateToSubModule(SubModule.STOCK) }
                    )
                }

                uiState.smartReminders.take(4).forEach { rem ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (rem.targetModule) {
                                    "STOCK" -> viewModel.navigateToSubModule(SubModule.STOCK)
                                    "PAYMENTS" -> viewModel.navigateToSubModule(SubModule.PAYMENTS)
                                    "ORDERS" -> viewModel.navigateToSubModule(SubModule.ORDERS)
                                    "VISITS" -> viewModel.navigateToSubModule(SubModule.VISITS)
                                    "TRIALS" -> viewModel.navigateToSubModule(SubModule.TRIALS)
                                    "TARGETS" -> viewModel.navigateToSubModule(SubModule.TARGETS)
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (rem.severity) {
                                    "HIGH" -> Icons.Default.WarningAmber
                                    "MEDIUM" -> Icons.Default.NotificationsActive
                                    else -> Icons.Default.Info
                                },
                                contentDescription = rem.category,
                                tint = when (rem.severity) {
                                    "HIGH" -> StatusDanger
                                    "MEDIUM" -> StatusWarning
                                    else -> StatusInfo
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rem.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = rem.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6. Optional Offline Smart Business Query Foundation (Section 36)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = "Instant Business Query",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Instant Offline Business Query",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "What was my 55M sales this month?",
                            "Which dealers have outstanding payments?",
                            "Show my top 10 dealers",
                            "How much stock do I have?",
                            "Generate monthly sales summary"
                        ).forEach { sampleQ ->
                            SuggestionChip(
                                onClick = {
                                    smartQueryInput = sampleQ
                                    smartQueryAnswer = viewModel.executeSmartBusinessQuery(sampleQ)
                                },
                                label = { Text(sampleQ, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = smartQueryInput,
                            onValueChange = { smartQueryInput = it },
                            placeholder = { Text("Ask e.g. 'How many bags did I sell this month?'") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledIconButton(
                            onClick = {
                                smartQueryAnswer = viewModel.executeSmartBusinessQuery(smartQueryInput)
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Run Query")
                        }
                    }
                    if (smartQueryAnswer != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = smartQueryAnswer!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertSummaryPill(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
