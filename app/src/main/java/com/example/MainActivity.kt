package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var triggerNewVisit by remember { mutableStateOf(false) }
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
                    Column {
                        Text(
                            text = when (uiState.activeTab) {
                                PrimaryTab.DASHBOARD -> "DairyFeed Pro"
                                else -> uiState.activeSubModule.title
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.config.companyName} • ${uiState.config.currencySymbol}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateToSubModule(SubModule.SEARCH) },
                        modifier = Modifier.testTag("top_bar_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Global Search"
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (uiState.smartReminders.isNotEmpty()) {
                                Badge { Text("${uiState.smartReminders.size}") }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToSubModule(SubModule.REMINDERS) },
                            modifier = Modifier.testTag("top_bar_reminders_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminders & Alerts"
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.navigateToSubModule(SubModule.SETTINGS) },
                        modifier = Modifier.testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Admin"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                val navItems = listOf(
                    Triple(PrimaryTab.DASHBOARD, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
                    Triple(PrimaryTab.SALES, Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
                    Triple(PrimaryTab.PARTNERS, Icons.Filled.Groups, Icons.Outlined.Groups),
                    Triple(PrimaryTab.FIELD, Icons.Filled.Agriculture, Icons.Outlined.Agriculture),
                    Triple(PrimaryTab.HUB, Icons.Filled.Apps, Icons.Outlined.Apps)
                )
                navItems.forEach { (tab, filledIcon, outlinedIcon) ->
                    val selected = uiState.activeTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.navigateToTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}")
                    )
                }
            }
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
                            "NEW_VISIT" -> {
                                viewModel.navigateToSubModule(SubModule.VISITS)
                                triggerNewVisit = true
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
                    triggerNewVisit = triggerNewVisit,
                    triggerNewTrial = triggerNewTrial,
                    triggerNewExpense = triggerNewExpense,
                    onConsumeTrigger = {
                        triggerNewVisit = false
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
