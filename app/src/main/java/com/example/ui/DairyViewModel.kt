package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import android.net.Uri

enum class PrimaryTab(val label: String) {
    DASHBOARD("Dashboard"),
    SALES("Orders"),
    PARTNERS("Dealers"),
    FIELD("Field Ops"),
    HUB("More")
}

enum class SubModule(val title: String) {
    // Under Sales & Stock
    ORDERS("Orders & Sales"),
    CONSOLIDATED("Consolidated Orders"),
    STOCK("Stock & Inventory"),
    PAYMENTS("Payments & Ledger"),
    INVOICES("Invoices"),
    PRODUCTS("Products & Feed"),

    // Under Partners
    DEALERS("Dealers & Customers"),
    FARMERS("Farmers"),

    // Under Field Ops
    VISITS("Field Visits"),
    TRIALS("Feed Trials & Milk"),
    COMPLAINTS("Complaints"),
    EXPENSES_TRAVEL("Expenses & Odometer"),

    // Under Hub / More
    MORE("More & Profile"),
    REPORTS("Business Reports"),
    TARGETS("Sales Targets"),
    REMINDERS("Notifications & Alerts"),
    SETTINGS("Settings & Admin"),
    BACKUP("Backup & Restore"),
    SEARCH("Global Search"),
    ABOUT("About WandaBook Pro")
}

data class DashboardMetrics(
    // Today's Summary
    val todayOrdersCount: Int = 0,
    val todayBags: Int = 0,
    val todaySales: Double = 0.0,
    val todayCollection: Double = 0.0,
    val pendingDeliveriesCount: Int = 0,

    // Monthly & Executive Summary
    val monthlyOrdersCount: Int = 0,
    val monthlySales: Double = 0.0,
    val monthlyBags: Int = 0,
    val monthlyKg: Double = 0.0,
    val monthlyMt: Double = 0.0,
    val monthlyCollection: Double = 0.0,
    val monthlyPaymentsCount: Int = 0,
    val totalAllTimeSales: Double = 0.0,
    val totalAllTimeBags: Int = 0,
    val totalAllTimeKg: Double = 0.0,
    val activeDealersCount: Int = 0,
    val totalOutstandingPayments: Double = 0.0,
    val totalOverduePayments: Double = 0.0,
    val monthlyGrossProfit: Double = 0.0,
    val monthlyTargetBags: Int = 1000,
    val monthlyTargetSales: Double = 3600000.0,
    val achievementPercentBags: Double = 0.0,
    val remainingTargetBags: Int = 0,

    // Alerts counts
    val overdueDealersCount: Int = 0,
    val pendingTrialsCount: Int = 0,
    val followUpDueCount: Int = 0,
    val lowStockProductsCount: Int = 0,
    val openComplaintsCount: Int = 0
)

data class AppUiState(
    val isInitialized: Boolean = false,
    val isLockedByPin: Boolean = false,
    val activeTab: PrimaryTab = PrimaryTab.DASHBOARD,
    val activeSubModule: SubModule = SubModule.ORDERS,
    val selectedDealerForDetail: Long? = null,
    val selectedFarmerForDetail: Long? = null,
    val selectedOrderForInvoice: Long? = null,
    val selectedTrialForDetail: Long? = null,
    val statusBannerMessage: String? = null,

    val config: BusinessConfigEntity = BusinessConfigEntity(),
    val lookups: List<LookupItemEntity> = emptyList(),
    val bagSizes: List<BagSizeEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val priceHistory: List<PriceHistoryEntity> = emptyList(),
    val dealers: List<DealerEntity> = emptyList(),
    val dealerSummaries: List<DealerFinancialSummary> = emptyList(),
    val farmers: List<FarmerEntity> = emptyList(),
    val ordersWithItems: List<OrderWithItems> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val productStockStatuses: List<ProductStockStatus> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val trialsWithMilk: List<TrialWithMilkRecords> = emptyList(),
    val complaints: List<ComplaintEntity> = emptyList(),
    val targets: List<SalesTargetEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val travelLogs: List<TravelLogEntity> = emptyList(),
    val customFieldDefs: List<CustomFieldDefEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val dashboard: DashboardMetrics = DashboardMetrics(),
    val smartReminders: List<SmartReminderItem> = emptyList()
)

class DairyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DairyDatabase.getInstance(application)
    val repository = DairyRepository(database.dairyDao())

    private val _activeTab = MutableStateFlow(PrimaryTab.DASHBOARD)
    private val _activeSubModule = MutableStateFlow(SubModule.ORDERS)
    private val _selectedDealerId = MutableStateFlow<Long?>(null)
    private val _selectedFarmerId = MutableStateFlow<Long?>(null)
    private val _selectedOrderInvoiceId = MutableStateFlow<Long?>(null)
    private val _selectedTrialId = MutableStateFlow<Long?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isUnlockedThisSession = MutableStateFlow(false)

    // Navigation history stack for clean Android back button handling
    private val navBackStack = ArrayDeque<Pair<PrimaryTab, SubModule>>()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
            repository.runAutoBackupIfDue(getApplication())
            observeAllDataStreams()
        }
    }

    private fun observeAllDataStreams() {
        viewModelScope.launch {
            combine(
                repository.businessConfig,
                repository.allLookups,
                repository.allBagSizes,
                repository.allProducts,
                repository.priceHistory
            ) { cfg, lookups, bagSizes, products, priceHist ->
                 Tuple5(cfg ?: BusinessConfigEntity(), lookups, bagSizes, products, priceHist)
            }.combine(
                combine(
                    repository.allDealers,
                    repository.allFarmers,
                    repository.allOrdersWithItems,
                    repository.allStockMovements,
                    repository.allPayments
                ) { dealers, farmers, orders, stock, payments ->
                    Tuple5(dealers, farmers, orders, stock, payments)
                }
            ) { part1, part2 ->
                part1 to part2
            }.combine(
                combine(
                    repository.allTrialsWithMilk,
                    repository.allComplaints,
                    repository.allTargets,
                    repository.allExpenses
                ) { trials, complaints, targets, expenses ->
                    Tuple4(trials, complaints, targets, expenses)
                }
            ) { (part1, part2), part3 ->
                Triple(part1, part2, part3)
            }.combine(
                combine(
                    repository.allTravelLogs,
                    repository.allCustomFieldDefs,
                    repository.auditLogs
                ) { travel, customDefs, audits ->
                    Triple(travel, customDefs, audits)
                }
            ) { (part1, part2, part3), part4 ->
                val cfg = part1.a
                val lookups = part1.b
                val bagSizes = part1.c
                val products = part1.d
                val priceHist = part1.e

                val dealers = part2.a
                val farmers = part2.b
                val orders = part2.c
                val stockMovements = part2.d
                val payments = part2.e

                val trials = part3.a
                val complaints = part3.b
                val targets = part3.c
                val expenses = part3.d

                val travelLogs = part4.first
                val customDefs = part4.second
                val audits = part4.third

                val dealerSummaries = dealers.map { d ->
                    BusinessCalculations.computeDealerSummary(d, orders, payments)
                }
                val stockStatuses = BusinessCalculations.computeProductStockStatuses(products, stockMovements)
                val dash = computeDashboard(orders, payments, trials, complaints, targets, dealerSummaries, stockStatuses)
                val reminders = computeReminders(cfg, orders, complaints, trials, dealerSummaries, stockStatuses, dash)

                _uiState.value.copy(
                    isInitialized = true,
                    config = cfg,
                    isLockedByPin = cfg.pinLockEnabled && !_isUnlockedThisSession.value,
                    lookups = lookups,
                    bagSizes = bagSizes,
                    products = products,
                    priceHistory = priceHist,
                    dealers = dealers,
                    dealerSummaries = dealerSummaries,
                    farmers = farmers,
                    ordersWithItems = orders,
                    stockMovements = stockMovements,
                    productStockStatuses = stockStatuses,
                    payments = payments,
                    trialsWithMilk = trials,
                    complaints = complaints,
                    targets = targets,
                    expenses = expenses,
                    travelLogs = travelLogs,
                    customFieldDefs = customDefs,
                    auditLogs = audits,
                    dashboard = dash,
                    smartReminders = reminders
                )
            }.combine(
                combine(
                    _activeTab,
                    _activeSubModule,
                    _selectedDealerId,
                    _selectedFarmerId,
                    _selectedOrderInvoiceId
                ) { tab, sub, dId, fId, oId ->
                    Tuple5(tab, sub, dId, fId, oId)
                }
            ) { state, nav ->
                state.copy(
                    activeTab = nav.a,
                    activeSubModule = nav.b,
                    selectedDealerForDetail = nav.c,
                    selectedFarmerForDetail = nav.d,
                    selectedOrderForInvoice = nav.e
                )
            }.combine(
                combine(_selectedTrialId, _statusMessage, _isUnlockedThisSession) { tId, msg, unlocked ->
                    Triple(tId, msg, unlocked)
                }
            ) { state, (tId, msg, unlocked) ->
                state.copy(
                    selectedTrialForDetail = tId,
                    statusBannerMessage = msg,
                    isLockedByPin = state.config.pinLockEnabled && !unlocked
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    private fun computeDashboard(
        orders: List<OrderWithItems>,
        payments: List<PaymentEntity>,
        trials: List<TrialWithMilkRecords>,
        complaints: List<ComplaintEntity>,
        targets: List<SalesTargetEntity>,
        dealerSummaries: List<DealerFinancialSummary>,
        stockStatuses: List<ProductStockStatus>
    ): DashboardMetrics {
        val today = SampleDataSeeder.todayDate()
        val monthPrefix = SampleDataSeeder.currentMonthPrefix()

        val validOrders = orders.filter { it.order.status != "Cancelled" && it.order.status != "Draft" }
        val todayOrders = validOrders.filter { it.order.date == today }
        val monthOrders = validOrders.filter { it.order.date.startsWith(monthPrefix) && it.order.date <= today }

        val todayPayments = payments.filter { it.date == today }
        val monthPayments = payments.filter { it.date.startsWith(monthPrefix) && it.date <= today }

        val pendingDeliveries = orders.count {
            it.order.status == "Pending" || it.order.status == "Confirmed" || it.order.status == "In Transit" || it.order.status == "Partially Delivered"
        }

        val monthlyBags = monthOrders.sumOf { it.order.totalBags }
        val monthlyKg = monthOrders.sumOf { it.order.totalKg }
        val monthlyMt = monthOrders.sumOf { it.order.totalMt }
        val monthlySales = monthOrders.sumOf { it.order.netAmount }
        val monthlyProfit = monthOrders.sumOf { it.order.estimatedProfit }
        val monthlyCollection = monthPayments.sumOf { it.amount }
        val monthlyPaymentsCount = monthPayments.size

        val totalAllTimeSales = validOrders.sumOf { it.order.netAmount }
        val totalAllTimeBags = validOrders.sumOf { it.order.totalBags }
        val totalAllTimeKg = validOrders.sumOf { it.order.totalKg }

        val activeDealers = dealerSummaries.filter { !it.dealer.isArchived }
        val totalOutstanding = activeDealers.sumOf { it.outstandingBalance.coerceAtLeast(0.0) }
        val totalOverdue = activeDealers.sumOf { it.overdueAmount.coerceAtLeast(0.0) }
        val overdueDealersCount = activeDealers.count { it.overdueAmount > 0.0 || it.outstandingBalance > it.dealer.creditLimit }

        val currentTarget = targets.firstOrNull { it.periodLabel == monthPrefix } ?: targets.firstOrNull()
        val targetBags = currentTarget?.targetBags ?: 1000
        val targetSales = currentTarget?.targetSalesValue ?: 3600000.0
        val achPct = if (targetBags > 0) {
            ((monthlyBags.toDouble() / targetBags.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
        val remBags = (targetBags - monthlyBags).coerceAtLeast(0)

        val pendingTrials = trials.count {
            it.trial.status in listOf("Planned", "Started", "Transition", "Under Observation")
        }
        val followUpDue = complaints.count { it.followUpDate.isNotBlank() && it.followUpDate <= today && it.status != "Closed" && it.status != "Resolved" }
        val lowStockCount = stockStatuses.count { it.isLowStock }
        val openComplaintsCount = complaints.count { it.status != "Closed" && it.status != "Resolved" }

        return DashboardMetrics(
            todayOrdersCount = todayOrders.size,
            todayBags = todayOrders.sumOf { it.order.totalBags },
            todaySales = todayOrders.sumOf { it.order.netAmount },
            todayCollection = todayPayments.sumOf { it.amount },
            pendingDeliveriesCount = pendingDeliveries,
            monthlyOrdersCount = monthOrders.size,
            monthlySales = monthlySales,
            monthlyBags = monthlyBags,
            monthlyKg = monthlyKg,
            monthlyMt = monthlyMt,
            monthlyCollection = monthlyCollection,
            monthlyPaymentsCount = monthlyPaymentsCount,
            totalAllTimeSales = totalAllTimeSales,
            totalAllTimeBags = totalAllTimeBags,
            totalAllTimeKg = totalAllTimeKg,
            activeDealersCount = activeDealers.size,
            totalOutstandingPayments = totalOutstanding,
            totalOverduePayments = totalOverdue,
            monthlyGrossProfit = monthlyProfit,
            monthlyTargetBags = targetBags,
            monthlyTargetSales = targetSales,
            achievementPercentBags = achPct,
            remainingTargetBags = remBags,
            overdueDealersCount = overdueDealersCount,
            pendingTrialsCount = pendingTrials,
            followUpDueCount = followUpDue,
            lowStockProductsCount = lowStockCount,
            openComplaintsCount = openComplaintsCount
        )
    }

    private fun computeReminders(
        cfg: BusinessConfigEntity,
        orders: List<OrderWithItems>,
        complaints: List<ComplaintEntity>,
        trials: List<TrialWithMilkRecords>,
        dealerSummaries: List<DealerFinancialSummary>,
        stockStatuses: List<ProductStockStatus>,
        dash: DashboardMetrics
    ): List<SmartReminderItem> {
        if (!cfg.notificationsEnabled) return emptyList()
        val list = mutableListOf<SmartReminderItem>()
        val today = SampleDataSeeder.todayDate()

        if (cfg.notifyLowStock) {
            stockStatuses.filter { it.isLowStock }.forEach { st ->
                list.add(
                    SmartReminderItem(
                        id = "LOW-${st.product.id}",
                        category = "LOW_STOCK",
                        title = "Low Stock: ${st.product.code} (${st.currentBags} bags left)",
                        subtitle = "Below minimum threshold of ${st.product.minStockBags} bags. Schedule mill dispatch.",
                        severity = "HIGH",
                        dateLabel = today,
                        targetModule = "STOCK"
                    )
                )
            }
        }

        if (cfg.notifyPaymentDue) {
            dealerSummaries.filter { !it.dealer.isArchived && (it.overdueAmount > 0 || it.outstandingBalance > it.dealer.creditLimit) }
                .forEach { ds ->
                    list.add(
                        SmartReminderItem(
                            id = "PAYDUE-${ds.dealer.id}",
                            category = "PAYMENT_DUE",
                            title = "Payment Recovery: ${ds.dealer.businessName}",
                            subtitle = "Outstanding: ${BusinessCalculations.formatCurrency(ds.outstandingBalance, cfg.currencySymbol)} (Overdue: ${BusinessCalculations.formatCurrency(ds.overdueAmount, cfg.currencySymbol)})",
                            severity = "HIGH",
                            dateLabel = ds.lastOrderDate,
                            targetModule = "PAYMENTS"
                        )
                    )
                }
        }

        if (cfg.notifyPendingDelivery) {
            orders.filter { it.order.status in listOf("Pending", "Confirmed", "In Transit", "Partially Delivered") }
                .forEach { owi ->
                    list.add(
                        SmartReminderItem(
                            id = "DELIV-${owi.order.id}",
                            category = "PENDING_DELIVERY",
                            title = "Pending Delivery: ${owi.order.orderNumber} (${owi.order.totalBags} bags)",
                            subtitle = "${owi.order.dealerName} • Expected: ${owi.order.expectedDeliveryDate} (${owi.order.status})",
                            severity = "MEDIUM",
                            dateLabel = owi.order.expectedDeliveryDate,
                            targetModule = "ORDERS"
                        )
                    )
                }
        }

        if (cfg.notifyFollowUpDue) {
            complaints.filter { it.followUpDate.isNotBlank() && it.followUpDate <= today && it.status != "Closed" && it.status != "Resolved" }.forEach { c ->
                list.add(
                    SmartReminderItem(
                        id = "CMP-${c.id}",
                        category = "FOLLOW_UP",
                        title = "Complaint Follow-up: ${c.partnerName}",
                        subtitle = "${c.complaintType} (${c.productName}) • Status: ${c.status}",
                        severity = "MEDIUM",
                        dateLabel = c.followUpDate,
                        targetModule = "COMPLAINTS"
                    )
                )
            }
        }

        if (cfg.notifyTrialFollowUp) {
            trials.filter { it.trial.status in listOf("Started", "Transition", "Under Observation") }.forEach { twm ->
                list.add(
                    SmartReminderItem(
                        id = "TRL-${twm.trial.id}",
                        category = "TRIAL_FOLLOW_UP",
                        title = "Active Feed Trial: ${twm.trial.trialCode} (${twm.trial.trialFeedCode})",
                        subtitle = "${twm.trial.farmName} (${twm.trial.farmerName}) • Status: ${twm.trial.status}",
                        severity = "INFO",
                        dateLabel = twm.trial.endDate,
                        targetModule = "TRIALS"
                    )
                )
            }
        }

        if (cfg.notifyTargetProgress) {
            list.add(
                SmartReminderItem(
                    id = "TGT-MONTH",
                    category = "TARGET",
                    title = "Monthly Target Progress: ${BusinessCalculations.formatDecimal(dash.achievementPercentBags, 1)}%",
                    subtitle = "Sold ${dash.monthlyBags} / ${dash.monthlyTargetBags} bags (${dash.remainingTargetBags} bags remaining)",
                    severity = "INFO",
                    dateLabel = SampleDataSeeder.currentMonthPrefix(),
                    targetModule = "TARGETS"
                )
            )
        }

        return list
    }

    // --- Navigation Actions ---
    fun navigateToTab(tab: PrimaryTab) {
        val defaultSub = when (tab) {
            PrimaryTab.DASHBOARD -> _activeSubModule.value
            PrimaryTab.SALES -> if (_activeSubModule.value in listOf(
                    SubModule.ORDERS,
                    SubModule.CONSOLIDATED,
                    SubModule.PAYMENTS,
                    SubModule.INVOICES,
                    SubModule.PRODUCTS
                )
            ) _activeSubModule.value else SubModule.ORDERS
            PrimaryTab.PARTNERS -> if (_activeSubModule.value in listOf(SubModule.DEALERS, SubModule.FARMERS)) _activeSubModule.value else SubModule.DEALERS
            PrimaryTab.FIELD -> if (_activeSubModule.value in listOf(
                    SubModule.VISITS,
                    SubModule.TRIALS,
                    SubModule.COMPLAINTS,
                    SubModule.EXPENSES_TRAVEL
                )
            ) _activeSubModule.value else SubModule.VISITS
            PrimaryTab.HUB -> if (_activeSubModule.value in listOf(
                    SubModule.MORE,
                    SubModule.REPORTS,
                    SubModule.TARGETS,
                    SubModule.REMINDERS,
                    SubModule.SETTINGS,
                    SubModule.BACKUP,
                    SubModule.SEARCH,
                    SubModule.ABOUT
                )
            ) _activeSubModule.value else SubModule.REPORTS
        }
        pushBackStack()
        _selectedDealerId.value = null
        _selectedFarmerId.value = null
        _selectedOrderInvoiceId.value = null
        _selectedTrialId.value = null
        _activeTab.value = tab
        _activeSubModule.value = defaultSub
    }

    fun navigateToSubModule(subModule: SubModule) {
        pushBackStack()
        _selectedDealerId.value = null
        _selectedFarmerId.value = null
        _selectedOrderInvoiceId.value = null
        _selectedTrialId.value = null
        val targetTab = when (subModule) {
            SubModule.ORDERS, SubModule.CONSOLIDATED, SubModule.STOCK, SubModule.PAYMENTS, SubModule.INVOICES, SubModule.PRODUCTS -> PrimaryTab.SALES
            SubModule.DEALERS, SubModule.FARMERS -> PrimaryTab.PARTNERS
            SubModule.VISITS, SubModule.TRIALS, SubModule.COMPLAINTS, SubModule.EXPENSES_TRAVEL -> PrimaryTab.FIELD
            SubModule.MORE, SubModule.REPORTS, SubModule.TARGETS, SubModule.REMINDERS, SubModule.SETTINGS, SubModule.BACKUP, SubModule.SEARCH, SubModule.ABOUT -> PrimaryTab.HUB
        }
        _activeTab.value = targetTab
        _activeSubModule.value = subModule
    }

    private fun pushBackStack() {
        val current = _activeTab.value to _activeSubModule.value
        if (navBackStack.lastOrNull() != current) {
            navBackStack.addLast(current)
            if (navBackStack.size > 20) navBackStack.removeFirst()
        }
    }

    fun handleBackNavigation(): Boolean {
        if (_selectedOrderInvoiceId.value != null) {
            _selectedOrderInvoiceId.value = null
            return true
        }
        if (_selectedDealerId.value != null) {
            _selectedDealerId.value = null
            return true
        }
        if (_selectedFarmerId.value != null) {
            _selectedFarmerId.value = null
            return true
        }
        if (_selectedTrialId.value != null) {
            _selectedTrialId.value = null
            return true
        }
        if (navBackStack.isNotEmpty()) {
            val prev = navBackStack.removeLast()
            _activeTab.value = prev.first
            _activeSubModule.value = prev.second
            return true
        }
        if (_activeTab.value != PrimaryTab.DASHBOARD) {
            _activeTab.value = PrimaryTab.DASHBOARD
            return true
        }
        return false
    }

    fun openDealerDetail(dealerId: Long?) {
        _selectedDealerId.value = dealerId
    }

    fun openFarmerDetail(farmerId: Long?) {
        _selectedFarmerId.value = farmerId
    }

    fun openInvoicePreview(orderId: Long?) {
        _selectedOrderInvoiceId.value = orderId
    }

    fun openTrialDetail(trialId: Long?) {
        _selectedTrialId.value = trialId
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun unlockWithPin(enteredPin: String): Boolean {
        val expected = _uiState.value.config.securityPin
        return if (enteredPin == expected) {
            _isUnlockedThisSession.value = true
            true
        } else {
            false
        }
    }

    fun lockAppSession() {
        _isUnlockedThisSession.value = false
    }

    // --- Business Data Actions ---
    fun saveBusinessConfig(config: BusinessConfigEntity) {
        viewModelScope.launch {
            repository.saveBusinessConfig(config)
            showMessage("Settings updated")
        }
    }

    fun saveLookup(item: LookupItemEntity) {
        viewModelScope.launch {
            repository.saveLookup(item)
            showMessage("Saved '${item.name}'")
        }
    }

    fun deleteLookup(item: LookupItemEntity) {
        viewModelScope.launch {
            repository.deleteLookup(item)
            showMessage("Removed '${item.name}'")
        }
    }

    fun saveBagSize(bagSize: BagSizeEntity) {
        viewModelScope.launch {
            repository.saveBagSize(bagSize)
            showMessage("Saved bag size ${bagSize.label}")
        }
    }

    fun deleteBagSize(bagSize: BagSizeEntity) {
        viewModelScope.launch {
            repository.deleteBagSize(bagSize)
            showMessage("Removed bag size ${bagSize.label}")
        }
    }

    fun saveProduct(product: ProductEntity, remarks: String = "") {
        viewModelScope.launch {
            repository.saveProduct(product, remarks)
            showMessage("Saved product ${product.code} (old sales keep snapshotted prices)")
        }
    }

    fun deleteOrArchiveProduct(product: ProductEntity) {
        viewModelScope.launch {
            val deleted = repository.deleteProductSafely(product)
            if (deleted) {
                showMessage("Deleted unused product ${product.code}")
            } else {
                showMessage("Archived ${product.code} to preserve historical sales records")
            }
        }
    }

    fun saveDealer(dealer: DealerEntity) {
        viewModelScope.launch {
            repository.saveDealer(dealer)
            showMessage("Saved dealer ${dealer.businessName}")
        }
    }

    fun archiveOrDeleteDealer(dealer: DealerEntity, archiveOnly: Boolean = true) {
        viewModelScope.launch {
            repository.archiveOrDeleteDealer(dealer, archiveOnly)
            _selectedDealerId.value = null
            showMessage("Archived dealer ${dealer.businessName}")
        }
    }

    fun saveFarmer(farmer: FarmerEntity) {
        viewModelScope.launch {
            repository.saveFarmer(farmer)
            showMessage("Saved farmer ${farmer.name}")
        }
    }

    fun archiveOrDeleteFarmer(farmer: FarmerEntity) {
        viewModelScope.launch {
            repository.archiveOrDeleteFarmer(farmer)
            _selectedFarmerId.value = null
            showMessage("Archived farmer ${farmer.name}")
        }
    }

    fun saveOrder(order: SaleOrderEntity, items: List<OrderItemEntity>, paymentMethod: String = "Cash") {
        viewModelScope.launch {
            repository.saveOrderTransaction(order, items, paymentMethod)
            showMessage("Saved Order ${order.orderNumber} (${order.totalBags} bags, ${order.totalMt} MT)")
            triggerDriveAutoSyncIfConfigured()
        }
    }

    fun cancelOrder(order: SaleOrderEntity) {
        viewModelScope.launch {
            repository.cancelOrDeleteOrder(order)
            showMessage("Cancelled Order ${order.orderNumber} and updated stock/ledger")
        }
    }

    fun saveStockMovement(movement: StockMovementEntity) {
        viewModelScope.launch {
            repository.saveStockMovement(movement)
            showMessage("Recorded ${movement.type} for ${movement.productCode} (${movement.quantityBags} bags)")
        }
    }

    fun deleteStockMovement(movement: StockMovementEntity) {
        viewModelScope.launch {
            repository.deleteStockMovement(movement)
            showMessage("Removed stock movement entry")
        }
    }

    fun savePayment(payment: PaymentEntity) {
        viewModelScope.launch {
            repository.savePayment(payment)
            showMessage("Recorded payment ${payment.paymentNumber} (${payment.amount})")
            triggerDriveAutoSyncIfConfigured()
        }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            showMessage("Deleted payment ${payment.paymentNumber}")
        }
    }

    fun saveFeedTrial(trial: FeedTrialEntity) {
        viewModelScope.launch {
            repository.saveFeedTrial(trial)
            showMessage("Saved Feed Trial ${trial.trialCode}")
        }
    }

    fun deleteFeedTrial(trial: FeedTrialEntity) {
        viewModelScope.launch {
            repository.deleteFeedTrial(trial)
            _selectedTrialId.value = null
            showMessage("Removed Feed Trial ${trial.trialCode}")
        }
    }

    fun saveMilkRecord(record: MilkRecordEntity) {
        viewModelScope.launch {
            repository.saveMilkRecord(record)
            showMessage("Added milk record (${record.dailyMilkLiters} L) for ${record.animalIdTag}")
        }
    }

    fun deleteMilkRecord(record: MilkRecordEntity) {
        viewModelScope.launch {
            repository.deleteMilkRecord(record)
            showMessage("Removed milk entry")
        }
    }

    fun saveComplaint(complaint: ComplaintEntity) {
        viewModelScope.launch {
            repository.saveComplaint(complaint)
            showMessage("Saved complaint ${complaint.complaintCode}")
        }
    }

    fun deleteComplaint(complaint: ComplaintEntity) {
        viewModelScope.launch {
            repository.deleteComplaint(complaint)
            showMessage("Deleted complaint ${complaint.complaintCode}")
        }
    }

    fun saveTarget(target: SalesTargetEntity) {
        viewModelScope.launch {
            repository.saveTarget(target)
            showMessage("Saved target for ${target.periodLabel}")
        }
    }

    fun updateMonthlyTargetBags(newTargetBags: Int) {
        viewModelScope.launch {
            val safeBags = newTargetBags.coerceAtLeast(1)
            val monthPrefix = SampleDataSeeder.currentMonthPrefix()
            val existing = _uiState.value.targets.firstOrNull { it.periodLabel == monthPrefix }
                ?: _uiState.value.targets.firstOrNull()
            val avgBagPrice = 3600.0
            val updated = SalesTargetEntity(
                id = existing?.id ?: 0,
                periodLabel = monthPrefix,
                periodType = "Monthly",
                targetBags = safeBags,
                targetMt = (safeBags * 40.0) / 1000.0,
                targetSalesValue = existing?.targetSalesValue ?: (safeBags * avgBagPrice),
                targetCollection = existing?.targetCollection ?: (safeBags * avgBagPrice * 0.85),
                notes = existing?.notes ?: "Monthly Sales Target"
            )
            repository.saveTarget(updated)
            showMessage("Monthly Sales Target set to $safeBags Bags")
        }
    }

    fun deleteTarget(target: SalesTargetEntity) {
        viewModelScope.launch {
            repository.deleteTarget(target)
            showMessage("Deleted target ${target.periodLabel}")
        }
    }

    fun saveExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.saveExpense(expense)
            showMessage("Saved ${expense.category} expense (${expense.amount})")
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            showMessage("Removed expense entry")
        }
    }

    fun saveTravelLog(log: TravelLogEntity) {
        viewModelScope.launch {
            repository.saveTravelLog(log)
            showMessage("Saved travel log (${log.totalKm} KM)")
        }
    }

    fun deleteTravelLog(log: TravelLogEntity) {
        viewModelScope.launch {
            repository.deleteTravelLog(log)
            showMessage("Removed travel log")
        }
    }

    fun saveCustomFieldDef(def: CustomFieldDefEntity) {
        viewModelScope.launch {
            repository.saveCustomFieldDef(def)
            showMessage("Updated custom field '${def.fieldName}'")
        }
    }

    fun reloadSampleData() {
        viewModelScope.launch {
            repository.reloadSampleData()
            showMessage("Loaded realistic sample dairy feed business data")
        }
    }

    fun clearSampleDataStartFresh() {
        viewModelScope.launch {
            repository.clearSampleDataStartFresh()
            showMessage("Cleared sample records. Core products & settings preserved.")
        }
    }

    fun performManualBackup(context: Context, shareAfterSave: Boolean = false) {
        viewModelScope.launch {
            try {
                val file = repository.createBackupFile(context)
                showMessage("Backup saved: ${file.name}")
                if (shareAfterSave) {
                    DocumentAndExportManager.shareFile(context, file, "application/json", "Export WandaBook Pro Backup")
                }
            } catch (_: Exception) {
                showMessage("Something went wrong. Your data has not been deleted. Please try again.")
            }
        }
    }

    fun restoreBackup(context: Context, customJson: String? = null) {
        viewModelScope.launch {
            try {
                val (_, message) = repository.restoreFromLatestBackupWithValidation(context, customJson)
                showMessage(message)
            } catch (_: Exception) {
                showMessage("Something went wrong. Your data has not been deleted. Please try again.")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonText.isNullOrBlank()) {
                    showMessage("Selected backup file is empty. Your current data is safe.")
                    return@launch
                }
                val (_, message) = repository.restoreFromLatestBackupWithValidation(context, jsonText)
                showMessage(message)
            } catch (_: Exception) {
                showMessage("Something went wrong. Your data has not been deleted. Please try again.")
            }
        }
    }

    fun sendEmailBackup(context: Context, recipientEmail: String, isTest: Boolean = false) {
        viewModelScope.launch {
            try {
                val (_, message) = repository.dispatchEmailBackup(context, recipientEmail, isTest)
                showMessage(message)
            } catch (_: Exception) {
                showMessage("Something went wrong. Your data has not been deleted. Please try again.")
            }
        }
    }

    fun connectGoogleDriveFolder(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                val docFile = DocumentFile.fromTreeUri(context, uri)
                val folderName = docFile?.name ?: "Google Drive Folder"

                val currentCfg = _uiState.value.config
                val updatedCfg = currentCfg.copy(
                    googleDriveBackupEnabled = true,
                    googleDriveFolderUri = uri.toString(),
                    googleDriveFolderLabel = folderName,
                    googleDriveSyncStatus = "Connected"
                )
                repository.saveBusinessConfig(updatedCfg)
                showMessage("Connected to Google Drive folder: $folderName")

                // Immediately sync initial backup to Drive folder
                val (_, syncMsg) = repository.syncToGoogleDrive(context, uri.toString())
                showMessage(syncMsg)
            } catch (e: Exception) {
                showMessage("Failed to connect Google Drive: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun disconnectGoogleDrive(context: Context) {
        viewModelScope.launch {
            try {
                val currentCfg = _uiState.value.config
                if (currentCfg.googleDriveFolderUri.isNotBlank()) {
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(currentCfg.googleDriveFolderUri), flags)
                    } catch (_: Exception) {}
                }
                val updatedCfg = currentCfg.copy(
                    googleDriveBackupEnabled = false,
                    googleDriveFolderUri = "",
                    googleDriveFolderLabel = "",
                    googleDriveSyncStatus = "Not Connected"
                )
                repository.saveBusinessConfig(updatedCfg)
                showMessage("Google Drive backup disconnected.")
            } catch (e: Exception) {
                showMessage("Error disconnecting Google Drive: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun syncToGoogleDriveNow(context: Context, isSilent: Boolean = false) {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            if (currentCfg.googleDriveFolderUri.isBlank()) {
                if (!isSilent) showMessage("Please connect a Google Drive folder first.")
                return@launch
            }
            val (success, msg) = repository.syncToGoogleDrive(context, currentCfg.googleDriveFolderUri)
            if (!isSilent || !success) {
                showMessage(msg)
            }
        }
    }

    fun restoreFromGoogleDriveNow(context: Context) {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            if (currentCfg.googleDriveFolderUri.isBlank()) {
                showMessage("Please connect a Google Drive folder first.")
                return@launch
            }
            val (success, msg) = repository.restoreFromGoogleDrive(context, currentCfg.googleDriveFolderUri)
            showMessage(msg)
        }
    }

    private fun triggerDriveAutoSyncIfConfigured() {
        val cfg = _uiState.value.config
        if (cfg.googleDriveBackupEnabled && cfg.googleDriveFolderUri.isNotBlank() && cfg.googleDriveFrequency == "On Every Sale Order") {
            viewModelScope.launch {
                try {
                    repository.syncToGoogleDrive(getApplication(), cfg.googleDriveFolderUri)
                } catch (_: Exception) {}
            }
        }
    }

    fun downloadInvoiceAsPdf(context: Context, orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            try {
                val config = _uiState.value.config
                val pdf = DocumentAndExportManager.generateInvoicePdfFile(context, config, orderWithItems)
                val safeName = "Invoice_${orderWithItems.order.invoiceNumber}.pdf"
                val saved = DocumentAndExportManager.saveFileToPublicDownloads(context, pdf, "application/pdf", safeName)
                if (saved) {
                    showMessage("Saved PDF to Downloads: $safeName")
                }
                DocumentAndExportManager.shareFile(context, pdf, "application/pdf", "Invoice ${orderWithItems.order.invoiceNumber} (PDF)")
            } catch (e: Exception) {
                showMessage("Could not generate PDF: ${e.localizedMessage}")
            }
        }
    }

    fun downloadInvoiceAsImage(context: Context, orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            try {
                val config = _uiState.value.config
                val img = DocumentAndExportManager.generateInvoiceImageFile(context, config, orderWithItems)
                val safeName = "Invoice_${orderWithItems.order.invoiceNumber}.png"
                val saved = DocumentAndExportManager.saveFileToPublicDownloads(context, img, "image/png", safeName)
                if (saved) {
                    showMessage("Saved Image to Downloads: $safeName")
                }
                DocumentAndExportManager.shareFile(context, img, "image/png", "Invoice ${orderWithItems.order.invoiceNumber} (Image)")
            } catch (e: Exception) {
                showMessage("Could not generate Image: ${e.localizedMessage}")
            }
        }
    }

    fun uploadBackupDirectToDriveApp(context: Context) {
        viewModelScope.launch {
            try {
                val file = repository.createBackupFile(context)
                val currentCfg = _uiState.value.config
                val timeStamp = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                repository.saveBusinessConfig(currentCfg.copy(googleDriveLastSync = timeStamp, googleDriveSyncStatus = "Active"))
                DocumentAndExportManager.shareFileToGoogleDriveApp(context, file, "application/json")
                sendDiscordAuditAlert(
                    eventType = "Cloud Backup",
                    detail = "Backup file (${file.name}) exported directly to Google Drive app."
                )
            } catch (e: Exception) {
                showMessage("Failed to launch Google Drive: ${e.localizedMessage}")
            }
        }
    }

    fun attachGoogleAccount(email: String) {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            val cleanEmail = email.trim()
            val updated = currentCfg.copy(
                googleAccountEmail = cleanEmail,
                userEmail = if (cleanEmail.isNotBlank()) cleanEmail else currentCfg.userEmail,
                googleDriveSyncStatus = if (cleanEmail.isNotBlank()) "Linked" else "Not Connected",
                googleDriveBackupEnabled = cleanEmail.isNotBlank()
            )
            repository.saveBusinessConfig(updated)
            showMessage(if (cleanEmail.isNotBlank()) "Google Account attached: $cleanEmail" else "Google Account detached.")
            if (cleanEmail.isNotBlank()) {
                sendDiscordAuditAlert(
                    eventType = "Account Attached",
                    detail = "User attached Google Account: $cleanEmail"
                )
            }
        }
    }

    fun updateDiscordWebhookUrl(url: String) {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            val updated = currentCfg.copy(discordWebhookUrl = url.trim())
            repository.saveBusinessConfig(updated)
            showMessage("Discord Webhook saved successfully.")
        }
    }

    fun sendTestDiscordAlert() {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            val webhook = currentCfg.discordWebhookUrl.ifBlank { currentCfg.emailWebhookUrl }
            if (webhook.isBlank()) {
                showMessage("Please enter a Discord Webhook URL first.")
                return@launch
            }
            showMessage("Sending test notification to Discord...")
            val orders = _uiState.value.ordersWithItems
            val totalSales = orders.sumOf { it.order.netAmount }
            val email = currentCfg.googleAccountEmail.ifBlank { currentCfg.userEmail }
            val result = com.example.data.DiscordAuditNotifier.sendActivityNotification(
                webhookUrl = webhook,
                eventType = "Test Ping",
                userEmail = email,
                businessName = currentCfg.companyName,
                details = "Discord webhook connected successfully with WandaBook Pro!",
                totalOrders = orders.size,
                totalSales = totalSales,
                currencySymbol = currentCfg.currencySymbol
            )
            if (result.isSuccess) {
                showMessage("✅ Discord Test Alert Sent Successfully!")
            } else {
                showMessage("❌ Discord Alert Failed: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun sendDiscordAuditAlert(eventType: String, detail: String) {
        viewModelScope.launch {
            val currentCfg = _uiState.value.config
            val webhook = currentCfg.discordWebhookUrl.ifBlank { currentCfg.emailWebhookUrl }
            val orders = _uiState.value.ordersWithItems
            val totalSales = orders.sumOf { it.order.netAmount }
            val email = currentCfg.googleAccountEmail.ifBlank { currentCfg.userEmail }
            com.example.data.DiscordAuditNotifier.sendActivityNotification(
                webhookUrl = webhook,
                eventType = eventType,
                userEmail = email,
                businessName = currentCfg.companyName,
                details = detail,
                totalOrders = orders.size,
                totalSales = totalSales,
                currencySymbol = currentCfg.currencySymbol
            )
        }
    }

    /**
     * Section 36: Optional Offline Smart Business Query Engine Foundation
     * Answers natural-language field queries offline using live database state,
     * with clean extensibility for future LLM/AI cloud integration.
     */
    fun executeSmartBusinessQuery(query: String): String {
        val state = _uiState.value
        val q = query.lowercase().trim()
        val cur = state.config.currencySymbol
        val monthPrefix = SampleDataSeeder.currentMonthPrefix()
        val validOrders = state.ordersWithItems.filter { it.order.status != "Cancelled" && it.order.status != "Draft" }
        val monthOrders = validOrders.filter { it.order.date.startsWith(monthPrefix) }

        return when {
            q.contains("55m") -> {
                val bags55M = monthOrders.flatMap { it.items }.filter { it.productCode.equals("55M", true) }.sumOf { it.quantityBags }
                val val55M = monthOrders.flatMap { it.items }.filter { it.productCode.equals("55M", true) }.sumOf { it.netAmount }
                "55M Sales This Month ($monthPrefix): $bags55M bags (${BusinessCalculations.formatDecimal(bags55M * 40.0 / 1000.0)} MT) totaling ${BusinessCalculations.formatCurrency(val55M, cur)}."
            }
            q.contains("outstanding") || q.contains("overdue") -> {
                val dueDealers = state.dealerSummaries.filter { !it.dealer.isArchived && it.outstandingBalance > 0 }
                    .sortedByDescending { it.outstandingBalance }
                if (dueDealers.isEmpty()) "All dealers have 0 outstanding balance."
                else "Dealers with Outstanding Payments:\n" + dueDealers.joinToString("\n") {
                    "• ${it.dealer.businessName}: ${BusinessCalculations.formatCurrency(it.outstandingBalance, cur)} (Bags: ${it.totalBags})"
                }
            }
            q.contains("top") && q.contains("dealer") -> {
                val top = state.dealerSummaries.filter { !it.dealer.isArchived }
                    .sortedByDescending { it.totalSales }
                    .take(10)
                "Top Dealers by Sales Volume:\n" + top.mapIndexed { idx, d ->
                    "${idx + 1}. ${d.dealer.businessName} — ${d.totalBags} bags (${BusinessCalculations.formatDecimal(d.totalMt)} MT) • ${BusinessCalculations.formatCurrency(d.totalSales, cur)}"
                }.joinToString("\n")
            }
            q.contains("product") && (q.contains("most") || q.contains("top") || q.contains("best")) -> {
                val prodMap = mutableMapOf<String, Int>()
                validOrders.flatMap { it.items }.forEach {
                    prodMap[it.productCode] = (prodMap[it.productCode] ?: 0) + it.quantityBags
                }
                val best = prodMap.entries.maxByOrNull { it.value }
                if (best != null) "Top Selling Product: ${best.key} with ${best.value} bags sold across all active orders."
                else "No product sales recorded yet."
            }
            q.contains("stock") || q.contains("inventory") -> {
                "Current Warehouse Stock:\n" + state.productStockStatuses.joinToString("\n") {
                    "• ${it.product.code}: ${it.currentBags} bags (${BusinessCalculations.formatDecimal(it.currentMt)} MT)${if (it.isLowStock) " [LOW STOCK]" else ""}"
                }
            }
            q.contains("how many bags") || q.contains("monthly sales") || q.contains("summary") -> {
                val d = state.dashboard
                "Monthly Summary ($monthPrefix):\n• Bags Sold: ${d.monthlyBags} bags (${BusinessCalculations.formatDecimal(d.monthlyMt)} MT)\n• Net Sales: ${BusinessCalculations.formatCurrency(d.monthlySales, cur)}\n• Collections: ${BusinessCalculations.formatCurrency(d.monthlyCollection, cur)}\n• Est. Gross Profit: ${BusinessCalculations.formatCurrency(d.monthlyGrossProfit, cur)}\n• Target Achievement: ${BusinessCalculations.formatDecimal(d.achievementPercentBags, 1)}%"
            }
            else -> {
                val d = state.dashboard
                "Instant Business Snapshot:\n• Today: ${d.todayBags} bags (${BusinessCalculations.formatCurrency(d.todaySales, cur)}), Collection: ${BusinessCalculations.formatCurrency(d.todayCollection, cur)}\n• Month: ${d.monthlyBags} bags (${BusinessCalculations.formatDecimal(d.monthlyMt)} MT), Sales: ${BusinessCalculations.formatCurrency(d.monthlySales, cur)}\n• Total Outstanding: ${BusinessCalculations.formatCurrency(d.totalOutstandingPayments, cur)}"
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
