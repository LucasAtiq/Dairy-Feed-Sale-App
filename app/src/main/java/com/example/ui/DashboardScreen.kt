package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BusinessCalculations
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val DashPrimaryGreen = Color(0xFF0E5A36)
private val DashDarkGreen = Color(0xFF0A4D2E)
private val DashDeepGreen = Color(0xFF073B22)
private val DashGoldHighlight = Color(0xFFF5C518)

@Composable
fun DashboardScreen(
    uiState: AppUiState,
    viewModel: DairyViewModel,
    onQuickAction: (String) -> Unit
) {
    val dash = uiState.dashboard
    val cur = uiState.config.currencySymbol
    val systemDark = isSystemInDarkTheme()
    val isDark = when (uiState.config.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    var showTargetEditDialog by remember { mutableStateOf(false) }

    // Dynamically generated current device date (never hard-coded)
    val currentDeviceDateLabel = remember {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(Date())
    }

    // Latest 3-5 non-cancelled orders
    val recentOrders = remember(uiState.ordersWithItems) {
        uiState.ordersWithItems
            .filter { it.order.status != "Cancelled" && it.order.status != "Draft" }
            .sortedByDescending { it.order.date }
            .take(5)
    }

    val pageBgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF6F8F7)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1 & 2. WELCOME SECTION + DYNAMIC DATE BANNER
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DashDarkGreen, DashPrimaryGreen)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Profile",
                                tint = DashDarkGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Welcome, Attique ur Rehman",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Region Sale Manager",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD2EFE0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Current Date",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = currentDeviceDateLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 3. MAIN KPI CARDS (This Month Sales | This Month Orders | Payments Received)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMainKpiCard(
                    title = "This Month Sales",
                    primaryValue = "${dash.monthlyBags} Bags",
                    secondaryValue = "(${BusinessCalculations.formatDecimal(dash.monthlyMt)} MT)",
                    growthBadge = "+12%",
                    isDark = isDark,
                    modifier = Modifier.weight(1f).testTag("kpi_this_month_sales"),
                    onClick = { viewModel.navigateToSubModule(SubModule.REPORTS) }
                )
                DashboardMainKpiCard(
                    title = "This Month Orders",
                    primaryValue = "${dash.monthlyOrdersCount} Orders",
                    secondaryValue = "(${BusinessCalculations.formatDecimal(dash.monthlyMt)} MT)",
                    growthBadge = "+18%",
                    isDark = isDark,
                    modifier = Modifier.weight(1f).testTag("kpi_this_month_orders"),
                    onClick = { viewModel.navigateToSubModule(SubModule.ORDERS) }
                )
                DashboardMainKpiCard(
                    title = "Payments Received",
                    primaryValue = BusinessCalculations.formatCurrency(dash.monthlyCollection, cur),
                    secondaryValue = "(${dash.monthlyPaymentsCount} Receipts)",
                    growthBadge = "+10%",
                    isDark = isDark,
                    modifier = Modifier.weight(1f).testTag("kpi_payments_received"),
                    onClick = { viewModel.navigateToSubModule(SubModule.PAYMENTS) }
                )
            }
        }

        // 4. MONTHLY PERFORMANCE INDICATOR ("Monthly Target" Green Card)
        item {
            val numberFormatter = remember { NumberFormat.getNumberInstance(Locale.US) }
            val progressPct = dash.achievementPercentBags.coerceIn(0.0, 100.0)
            val progressFraction = (progressPct / 100.0).toFloat().coerceIn(0f, 1f)

            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable { showTargetEditDialog = true }
                    .testTag("monthly_target_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    DashDeepGreen,
                                    DashPrimaryGreen,
                                    Color(0xFF197246)
                                )
                            )
                        )
                ) {
                    // Subtle dairy visual element on the right edge
                    Image(
                        painter = painterResource(id = R.drawable.img_dairy_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(130.dp)
                            .height(128.dp)
                            .alpha(0.28f)
                    )
                    // Smooth gradient overlay to guarantee 100% crisp text contrast
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        DashDeepGreen,
                                        DashPrimaryGreen.copy(alpha = 0.92f),
                                        DashPrimaryGreen.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrackChanges,
                                        contentDescription = "Monthly Target",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Monthly Target",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = Color(0xFFE2F5EA)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Monthly Target",
                                            tint = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "${numberFormatter.format(dash.monthlyBags)} / ${numberFormatter.format(dash.monthlyTargetBags)} Bags",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Remaining: ${numberFormatter.format(dash.remainingTargetBags)} Bags",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "${BusinessCalculations.formatDecimal(dash.monthlyMt)} MT Sold",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD2EFE0)
                                )
                            }
                        }

                        // Gold Progress Bar with Percentage Badge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction.coerceAtLeast(0.14f))
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFF59E0B),
                                                DashGoldHighlight
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${progressPct.roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color(0xFF1B241E)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. QUICK ACTIONS (New Order, Add Dealer, Add Farmer, Add Payment, Create Invoice, Reports)
        item {
            val quickActions = listOf(
                Triple("NEW_SALE", "New Order", Icons.Default.PostAdd),
                Triple("NEW_DEALER", "Add Dealer", Icons.Default.Groups),
                Triple("NEW_FARMER", "Add Farmer", Icons.Default.Agriculture),
                Triple("PAYMENT", "Add Payment", Icons.Default.Payments),
                Triple("CREATE_INVOICE", "Create Invoice", Icons.Default.Description),
                Triple("REPORTS", "Reports", Icons.Default.BarChart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickActions.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (actionKey, label, icon) ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(88.dp)
                                    .clickable { onQuickAction(actionKey) }
                                    .testTag("quick_action_${actionKey.lowercase()}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0xFF2A4235) else Color(0xFFE2E8F0)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isDark) Color(0xFF4ADE80) else DashPrimaryGreen,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                        textAlign = TextAlign.Center,
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

        // 6. RECENT ORDERS SECTION
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF2A4235) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Recent Orders",
                                tint = if (isDark) Color(0xFF4ADE80) else DashPrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Recent Orders",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.navigateToSubModule(SubModule.ORDERS) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("view_all_orders_btn")
                        ) {
                            Text(
                                text = "View All →",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isDark) Color(0xFF4ADE80) else DashPrimaryGreen
                            )
                        }
                    }

                    if (recentOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No orders recorded yet. Tap 'New Order' above to add your first order.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        recentOrders.forEachIndexed { idx, owi ->
                            val o = owi.order
                            val payStatus = BusinessCalculations.getOrderPaymentStatus(
                                paidAmount = o.paidAmount,
                                netAmount = o.netAmount,
                                outstandingAmount = o.outstandingAmount
                            )

                            if (idx > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.navigateToSubModule(SubModule.ORDERS)
                                        viewModel.openInvoicePreview(o.id)
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("recent_order_row_${o.orderNumber}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Storefront Icon Circle
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDark) Color(0xFF143322) else Color(0xFFF0FDF4)
                                        )
                                        .border(
                                            1.dp,
                                            if (isDark) Color(0xFF166534) else Color(0xFFBBF7D0),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF4ADE80) else DashPrimaryGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Order Details Column
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = o.orderNumber,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = o.dealerName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${o.totalBags} Bags (${o.totalKg.roundToInt()} KG)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Amount & Status Badges Column
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = BusinessCalculations.formatCurrency(o.netAmount, cur),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DashboardOrderStatusPill(status = payStatus)
                                        DashboardOrderStatusPill(status = o.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTargetEditDialog) {
        var bagsInput by remember(dash.monthlyTargetBags) {
            mutableStateOf(dash.monthlyTargetBags.toString())
        }
        AlertDialog(
            onDismissRequest = { showTargetEditDialog = false },
            title = { Text("Set Monthly Sales Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your monthly sales target in Bags. Progress % and Remaining Bags will update automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bagsInput,
                        onValueChange = { bagsInput = it },
                        label = { Text("Monthly Target (Bags)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_target_bags_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBags = (bagsInput.toIntOrNull() ?: dash.monthlyTargetBags).coerceAtLeast(1)
                        viewModel.updateMonthlyTargetBags(newBags)
                        showTargetEditDialog = false
                    },
                    modifier = Modifier.testTag("dashboard_save_target_btn")
                ) {
                    Text("Save Target")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTargetEditDialog = false
                        viewModel.navigateToSubModule(SubModule.TARGETS)
                    }
                ) {
                    Text("All Target Settings")
                }
            }
        )
    }
}

@Composable
private fun DashboardMainKpiCard(
    title: String,
    primaryValue: String,
    secondaryValue: String,
    growthBadge: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2A4235) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                ),
                color = if (isDark) Color.White else Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp
                ),
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                color = if (isDark) Color(0xFF14532D).copy(alpha = 0.5f) else Color(0xFFDCFCE7),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D),
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = growthBadge,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardOrderStatusPill(
    status: String
) {
    val (bgColor, fgColor) = when (status.lowercase()) {
        "delivered", "paid" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        "partial", "partially delivered" -> Pair(Color(0xFFFFEDD5), Color(0xFFC2410C))
        "pending", "in transit" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
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

private fun formatOrderDateForDashboard(rawDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = parser.parse(rawDate)
        if (parsed != null) {
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}
