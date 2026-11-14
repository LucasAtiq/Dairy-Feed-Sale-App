package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.theme.DairyFeedTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dairyViewModel: DairyViewModel = viewModel()
            val uiState by dairyViewModel.uiState.collectAsStateWithLifecycle()

            DairyFeedTheme(themeMode = uiState.config.themeMode) {
                DairyFeedAppRoot(
                    uiState = uiState,
                    viewModel = dairyViewModel
                )
            }
        }
    }
}

private val BrandDarkGreen = Color(0xFF0A4D2E)
private val BrandDeepEmblemBg = Color(0xFF06351E)
private val BrandGoldAccent = Color(0xFFF5C518)
private val BrandSubtitleMint = Color(0xFFD2EFE0)

private data class BottomNavDestination(
    val key: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val isSelected: (AppUiState) -> Boolean,
    val onSelect: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DairyFeedAppRoot(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    // Quick action triggers launched from the Dashboard
    var triggerNewSale by remember { mutableStateOf(false) }
    var triggerNewPayment by remember { mutableStateOf(false) }
    var triggerStockIn by remember { mutableStateOf(false) }
    var triggerNewDealer by remember { mutableStateOf(false) }
    var triggerNewFarmer by remember { mutableStateOf(false) }
    var triggerNewTrial by remember { mutableStateOf(false) }
    var triggerNewExpense by remember { mutableStateOf(false) }

    // Auto-clear status toast message after 3.5 seconds
    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(3500)
            viewModel.clearMessage()
        }
    }

    // Intercept system Back button whenever not on the root Dashboard tab or when a detail screen is open
    val canNavigateBack = uiState.activeTab != PrimaryTab.DASHBOARD ||
        uiState.selectedDealerForDetail != null ||
        uiState.selectedFarmerForDetail != null ||
        uiState.selectedOrderForInvoice != null ||
        uiState.selectedTrialForDetail != null

    BackHandler(enabled = canNavigateBack) {
        viewModel.handleBackNavigation()
    }

    if (uiState.isLockedByPin) {
        PinLockScreen(
            companyName = uiState.config.companyName,
            onUnlock = { pin -> viewModel.unlockWithPin(pin) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable {
                            viewModel.navigateToTab(PrimaryTab.DASHBOARD)
                        }
                    ) {
                        // WandaBook Pro Circular Emblem
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandDeepEmblemBg)
                                .border(1.5.dp, BrandGoldAccent.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = "WandaBook Pro Logo",
                                tint = BrandGoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append("WandaBook ")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            color = BrandGoldAccent,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append("Pro")
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (uiState.activeTab == PrimaryTab.DASHBOARD) {
                                    "Dairy Feed Sales & Management"
                                } else {
                                    "Dairy Feed Sales & Management • ${uiState.activeSubModule.title}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandSubtitleMint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateToSubModule(SubModule.SEARCH) },
                        modifier = Modifier.testTag("top_bar_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Global Search",
                            tint = Color.White
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (uiState.smartReminders.isNotEmpty()) {
                                Badge(
                                    containerColor = Color(0xFFE53935),
                                    contentColor = Color.White
                                ) {
                                    Text("${uiState.smartReminders.size}")
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToSubModule(SubModule.REMINDERS) },
                            modifier = Modifier.testTag("top_bar_reminders_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminders & Alerts",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.navigateToSubModule(SubModule.SETTINGS) },
                        modifier = Modifier.testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Admin",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDarkGreen,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            WandaBookBottomNavigationBar(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                PrimaryTab.DASHBOARD -> DashboardScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onQuickAction = { actionKey ->
                        when (actionKey) {
                            "NEW_SALE" -> {
                                viewModel.navigateToSubModule(SubModule.ORDERS)
                                triggerNewSale = true
                            }
                            "NEW_DEALER" -> {
                                viewModel.navigateToSubModule(SubModule.DEALERS)
                                triggerNewDealer = true
                            }
                            "NEW_FARMER" -> {
                                viewModel.navigateToSubModule(SubModule.FARMERS)
                                triggerNewFarmer = true
                            }
                            "PAYMENT" -> {
                                viewModel.navigateToSubModule(SubModule.PAYMENTS)
                                triggerNewPayment = true
                            }
                            "CREATE_INVOICE" -> {
                                viewModel.navigateToSubModule(SubModule.INVOICES)
                            }
                            "REPORTS" -> {
                                viewModel.navigateToSubModule(SubModule.REPORTS)
                            }
                            "CONSOLIDATED" -> {
                                viewModel.navigateToSubModule(SubModule.CONSOLIDATED)
                            }
                            "VIEW_ORDERS" -> {
                                viewModel.navigateToSubModule(SubModule.ORDERS)
                            }
                            "BACKUP_DATA" -> {
                                viewModel.navigateToSubModule(SubModule.BACKUP)
                            }
                            "NEW_TRIAL" -> {
                                viewModel.navigateToSubModule(SubModule.TRIALS)
                                triggerNewTrial = true
                            }
                            "STOCK_IN" -> {
                                viewModel.navigateToSubModule(SubModule.STOCK)
                                triggerStockIn = true
                            }
                            "EXPENSE" -> {
                                viewModel.navigateToSubModule(SubModule.EXPENSES_TRAVEL)
                                triggerNewExpense = true
                            }
                        }
                    }
                )
                PrimaryTab.SALES -> SalesAndStockScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    triggerNewSale = triggerNewSale,
                    triggerNewPayment = triggerNewPayment,
                    triggerStockIn = triggerStockIn,
                    onConsumeTrigger = {
                        triggerNewSale = false
                        triggerNewPayment = false
                        triggerStockIn = false
                    }
                )
                PrimaryTab.PARTNERS -> PartnersScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    triggerNewDealer = triggerNewDealer,
                    triggerNewFarmer = triggerNewFarmer,
                    onConsumeTrigger = {
                        triggerNewDealer = false
                        triggerNewFarmer = false
                    }
                )
                PrimaryTab.FIELD -> FieldOpsScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    triggerNewTrial = triggerNewTrial,
                    triggerNewExpense = triggerNewExpense,
                    onConsumeTrigger = {
                        triggerNewTrial = false
                        triggerNewExpense = false
                    }
                )
                PrimaryTab.HUB -> MoreHubScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }

            // Status Banner Toast at bottom-center
            AnimatedVisibility(
                visible = uiState.statusBannerMessage != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = uiState.statusBannerMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WandaBookBottomNavigationBar(
    uiState: AppUiState,
    viewModel: DairyViewModel
) {
    val destinations = remember(viewModel) {
        listOf(
            BottomNavDestination(
                key = "dashboard",
                label = "Dashboard",
                filledIcon = Icons.Filled.Home,
                outlinedIcon = Icons.Outlined.Home,
                isSelected = { it.activeTab == PrimaryTab.DASHBOARD },
                onSelect = { viewModel.navigateToTab(PrimaryTab.DASHBOARD) }
            ),
            BottomNavDestination(
                key = "orders",
                label = "Orders",
                filledIcon = Icons.Filled.ShoppingCart,
                outlinedIcon = Icons.Outlined.ShoppingCart,
                isSelected = { it.activeTab == PrimaryTab.SALES && it.activeSubModule == SubModule.ORDERS },
                onSelect = { viewModel.navigateToSubModule(SubModule.ORDERS) }
            ),
            BottomNavDestination(
                key = "dealers",
                label = "Dealers",
                filledIcon = Icons.Filled.Storefront,
                outlinedIcon = Icons.Outlined.Storefront,
                isSelected = { it.activeTab == PrimaryTab.PARTNERS && it.activeSubModule == SubModule.DEALERS },
                onSelect = { viewModel.navigateToSubModule(SubModule.DEALERS) }
            ),
            BottomNavDestination(
                key = "field_ops",
                label = "Field Ops",
                filledIcon = Icons.Filled.Agriculture,
                outlinedIcon = Icons.Outlined.Agriculture,
                isSelected = { it.activeTab == PrimaryTab.FIELD },
                onSelect = { viewModel.navigateToTab(PrimaryTab.FIELD) }
            ),
            BottomNavDestination(
                key = "reports",
                label = "Reports",
                filledIcon = Icons.Filled.BarChart,
                outlinedIcon = Icons.Outlined.BarChart,
                isSelected = { it.activeTab == PrimaryTab.HUB && it.activeSubModule == SubModule.REPORTS },
                onSelect = { viewModel.navigateToSubModule(SubModule.REPORTS) }
            )
        )
    }

    Surface(
        color = BrandDarkGreen,
        contentColor = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val selected = dest.isSelected(uiState)
                val tintColor = if (selected) BrandGoldAccent else Color.White.copy(alpha = 0.85f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { dest.onSelect() }
                        .padding(vertical = 6.dp)
                        .testTag("bottom_nav_${dest.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = if (selected) dest.filledIcon else dest.outlinedIcon,
                        contentDescription = dest.label,
                        tint = tintColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = dest.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) BrandGoldAccent else Color.Transparent)
                    )
                }
            }
        }
    }
}
