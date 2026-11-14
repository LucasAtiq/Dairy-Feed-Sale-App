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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BusinessCalculations
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlin.math.roundToInt

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

    val recentOrders = remember(uiState.ordersWithItems) {
        uiState.ordersWithItems.filter { it.order.status != "Cancelled" }.take(4)
    }
    val recentPayments = remember(uiState.payments) {
        uiState.payments.take(4)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Commercial Executive Hero Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_dairy_hero),
                        contentDescription = "WandaBook Pro Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xB3072415),
                                        Color(0xF2082E1B)
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
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Offline-First Ready",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "WandaBook Pro • Offline-First",
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

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "WandaBook Pro",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Dairy Feed Sales & Management • ${uiState.config.companyName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD2F5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. Quick-Action Buttons (Immediate 1-Tap Business Actions)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Business Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Fast One-Handed Entry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val quickButtons = listOf(
                    Triple("NEW_SALE", "+ New Order", Icons.Default.AddShoppingCart),
                    Triple("NEW_DEALER", "+ Add Dealer", Icons.Default.PersonAdd),
                    Triple("PAYMENT", "+ Add Payment", Icons.Default.Payments),
                    Triple("VIEW_ORDERS", "View Orders", Icons.Default.ReceiptLong),
                    Triple("BACKUP_DATA", "Backup Data", Icons.Default.Backup),
                    Triple("STOCK_IN", "+ Stock In", Icons.Default.Inventory),
                    Triple("CONSOLIDATED", "Consolidated", Icons.Default.Summarize),
                    Triple("NEW_TRIAL", "+ Feed Trial", Icons.Default.Science)
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
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Executive At-A-Glance Summary Cards (Large Readable Numbers)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Business Overview at a Glance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "This Month's Sales",
                        value = BusinessCalculations.formatCurrency(dash.monthlySales, cur),
                        subtitle = "Today: ${BusinessCalculations.formatCurrency(dash.todaySales, cur)}",
                        icon = Icons.Default.TrendingUp,
                        accentColor = StatusSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                    MetricStatCard(
                        title = "This Month's Orders",
                        value = "${dash.monthlyOrdersCount} Orders",
                        subtitle = "Today: ${dash.todayOrdersCount} (${dash.todayBags} bags)",
                        icon = Icons.Default.ShoppingBag,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Total Bags",
                        value = "${dash.monthlyBags} Bags",
                        subtitle = "All-time: ${dash.totalAllTimeBags} Bags",
                        icon = Icons.Default.Inventory2,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.CONSOLIDATED) }
                    )
                    MetricStatCard(
                        title = "Total Weight",
                        value = "${dash.monthlyKg.roundToInt()} KG",
                        subtitle = "${BusinessCalculations.formatDecimal(dash.monthlyMt)} Metric Tons (MT)",
                        icon = Icons.Default.Scale,
                        accentColor = StatusInfo,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.CONSOLIDATED) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Total Sales Amount",
                        value = BusinessCalculations.formatCurrency(dash.totalAllTimeSales, cur),
                        subtitle = "Collected Mtd: ${BusinessCalculations.formatCurrency(dash.monthlyCollection, cur)}",
                        icon = Icons.Default.AccountBalance,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.REPORTS) }
                    )
                    MetricStatCard(
                        title = "Outstanding Amount",
                        value = BusinessCalculations.formatCurrency(dash.totalOutstandingPayments, cur),
                        subtitle = "${dash.overdueDealersCount} dealers overdue",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = if (dash.totalOutstandingPayments > 0) StatusDanger else StatusSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.PAYMENTS) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Number of Dealers",
                        value = "${dash.activeDealersCount} Dealers",
                        subtitle = "${uiState.farmers.count { !it.isArchived }} registered farmers",
                        icon = Icons.Default.Groups,
                        accentColor = StatusInfo,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.DEALERS) }
                    )
                    MetricStatCard(
                        title = "Low Stock / Alerts",
                        value = "${dash.lowStockProductsCount} Low Stock",
                        subtitle = "${dash.pendingDeliveriesCount} pending deliveries",
                        icon = Icons.Default.WarningAmber,
                        accentColor = if (dash.lowStockProductsCount > 0) StatusDanger else StatusWarning,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateToSubModule(SubModule.STOCK) }
                    )
                }
            }
        }

        // 4. Monthly Target Progress Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Target Achievement",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Target: ${dash.monthlyTargetBags} Bags • Achieved: ${BusinessCalculations.formatDecimal(dash.achievementPercentBags, 1)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.navigateToSubModule(SubModule.TARGETS) }) {
                            Text("Target Settings")
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
                            Text("Sold This Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${dash.monthlyBags} Bags (${BusinessCalculations.formatDecimal(dash.monthlyMt)} MT)", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Remaining Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${dash.remainingTargetBags} Bags", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // 5. Recent Orders Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Orders",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }) {
                            Text("View All Orders")
                        }
                    }

                    if (recentOrders.isEmpty()) {
                        Text(
                            text = "No orders recorded yet. Tap '+ New Order' above to create your first sale.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentOrders.forEach { owi ->
                            val o = owi.order
                            val payStatus = BusinessCalculations.getOrderPaymentStatus(o.paidAmount, o.netAmount, o.outstandingAmount)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.navigateToSubModule(SubModule.ORDERS)
                                        viewModel.openInvoicePreview(o.id)
                                    }
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
                                            text = "${o.orderNumber} • ${o.dealerName}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${o.date} • ${o.totalBags} Bags (${o.totalKg.roundToInt()} KG)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = BusinessCalculations.formatCurrency(o.netAmount, cur),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            StatusBadge(payStatus)
                                            StatusBadge(o.status)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Recent Payments Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Payments",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { viewModel.navigateToSubModule(SubModule.PAYMENTS) }) {
                            Text("View Ledger")
                        }
                    }

                    if (recentPayments.isEmpty()) {
                        Text(
                            text = "No payments recorded yet. Tap '+ Add Payment' to log dealer collections.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentPayments.forEach { pay ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.navigateToSubModule(SubModule.PAYMENTS) }
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
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${pay.date} • ${pay.paymentMethod} ${if (pay.referenceNumber.isNotBlank()) "(${pay.referenceNumber})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = BusinessCalculations.formatCurrency(pay.amount, cur),
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                        color = StatusSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Alerts & Actionable Field Reminders
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Low Stock & Important Alerts (${uiState.smartReminders.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { viewModel.navigateToSubModule(SubModule.REMINDERS) }) {
                        Text("View All")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertSummaryPill(
                        label = "Low Stock: ${dash.lowStockProductsCount}",
                        color = StatusDanger,
                        onClick = { viewModel.navigateToSubModule(SubModule.STOCK) }
                    )
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
                        label = "Active Trials: ${dash.pendingTrialsCount}",
                        color = StatusInfo,
                        onClick = { viewModel.navigateToSubModule(SubModule.TRIALS) }
                    )
                }

                uiState.smartReminders.take(3).forEach { rem ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (rem.targetModule) {
                                    "STOCK" -> viewModel.navigateToSubModule(SubModule.STOCK)
                                    "PAYMENTS" -> viewModel.navigateToSubModule(SubModule.PAYMENTS)
                                    "ORDERS" -> viewModel.navigateToSubModule(SubModule.ORDERS)
                                    "COMPLAINTS" -> viewModel.navigateToSubModule(SubModule.COMPLAINTS)
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

        // 8. Instant Offline Business Query
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
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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
