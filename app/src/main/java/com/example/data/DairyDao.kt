package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DairyDao {

    // --- Business Config ---
    @Query("SELECT * FROM business_config WHERE id = 1")
    fun observeBusinessConfig(): Flow<BusinessConfigEntity?>

    @Query("SELECT * FROM business_config WHERE id = 1")
    suspend fun getBusinessConfigOnce(): BusinessConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBusinessConfig(config: BusinessConfigEntity)

    // --- Lookup Items (Dynamic Areas, Payment Methods, Visit Types, Complaint Types, Expense Categories, Dealer Types, Categories) ---
    @Query("SELECT * FROM lookup_items WHERE isArchived = 0 ORDER BY categoryType ASC, sortOrder ASC, name ASC")
    fun observeActiveLookups(): Flow<List<LookupItemEntity>>

    @Query("SELECT * FROM lookup_items ORDER BY categoryType ASC, name ASC")
    fun observeAllLookups(): Flow<List<LookupItemEntity>>

    @Query("SELECT * FROM lookup_items")
    suspend fun getAllLookupsOnce(): List<LookupItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLookup(item: LookupItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLookups(items: List<LookupItemEntity>)

    @Update
    suspend fun updateLookup(item: LookupItemEntity)

    @Delete
    suspend fun deleteLookup(item: LookupItemEntity)

    // --- Bag Sizes ---
    @Query("SELECT * FROM bag_sizes ORDER BY weightKg ASC")
    fun observeAllBagSizes(): Flow<List<BagSizeEntity>>

    @Query("SELECT * FROM bag_sizes")
    suspend fun getAllBagSizesOnce(): List<BagSizeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBagSize(bagSize: BagSizeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBagSizes(bagSizes: List<BagSizeEntity>)

    @Update
    suspend fun updateBagSize(bagSize: BagSizeEntity)

    @Delete
    suspend fun deleteBagSize(bagSize: BagSizeEntity)

    // --- Products & Price History ---
    @Query("SELECT * FROM products ORDER BY isArchived ASC, code ASC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("SELECT * FROM price_history ORDER BY changedAt DESC")
    fun observePriceHistory(): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history")
    suspend fun getAllPriceHistoryOnce(): List<PriceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(entry: PriceHistoryEntity)

    // --- Dealers ---
    @Query("SELECT * FROM dealers ORDER BY isArchived ASC, name ASC")
    fun observeAllDealers(): Flow<List<DealerEntity>>

    @Query("SELECT * FROM dealers")
    suspend fun getAllDealersOnce(): List<DealerEntity>

    @Query("SELECT * FROM dealers WHERE id = :id")
    suspend fun getDealerById(id: Long): DealerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDealer(dealer: DealerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDealers(dealers: List<DealerEntity>)

    @Update
    suspend fun updateDealer(dealer: DealerEntity)

    @Delete
    suspend fun deleteDealer(dealer: DealerEntity)

    // --- Farmers ---
    @Query("SELECT * FROM farmers ORDER BY isArchived ASC, name ASC")
    fun observeAllFarmers(): Flow<List<FarmerEntity>>

    @Query("SELECT * FROM farmers")
    suspend fun getAllFarmersOnce(): List<FarmerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmer(farmer: FarmerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmers(farmers: List<FarmerEntity>)

    @Update
    suspend fun updateFarmer(farmer: FarmerEntity)

    @Delete
    suspend fun deleteFarmer(farmer: FarmerEntity)

    // --- Sales Orders & Line Items ---
    @Transaction
    @Query("SELECT * FROM sale_orders ORDER BY date DESC, id DESC")
    fun observeAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Query("SELECT * FROM sale_orders ORDER BY date DESC, id DESC")
    suspend fun getAllOrdersOnce(): List<SaleOrderEntity>

    @Query("SELECT * FROM order_items")
    suspend fun getAllOrderItemsOnce(): List<OrderItemEntity>

    @Transaction
    @Query("SELECT * FROM sale_orders WHERE id = :orderId")
    suspend fun getOrderWithItemsById(orderId: Long): OrderWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleOrder(order: SaleOrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleOrders(orders: List<SaleOrderEntity>)

    @Update
    suspend fun updateSaleOrder(order: SaleOrderEntity)

    @Delete
    suspend fun deleteSaleOrder(order: SaleOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItemsForOrder(orderId: Long)

    // --- Stock / Inventory Movements ---
    @Query("SELECT * FROM stock_movements ORDER BY date DESC, id DESC")
    fun observeAllStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAllStockMovementsOnce(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovements(movements: List<StockMovementEntity>)

    @Query("DELETE FROM stock_movements WHERE relatedOrderId = :orderId")
    suspend fun deleteStockMovementsForOrder(orderId: Long)

    @Delete
    suspend fun deleteStockMovement(movement: StockMovementEntity)

    // --- Payments ---
    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun getAllPaymentsOnce(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    // --- Feed Trials & Milk Production ---
    @Transaction
    @Query("SELECT * FROM feed_trials ORDER BY startDate DESC, id DESC")
    fun observeAllTrialsWithMilk(): Flow<List<TrialWithMilkRecords>>

    @Query("SELECT * FROM feed_trials")
    suspend fun getAllTrialsOnce(): List<FeedTrialEntity>

    @Query("SELECT * FROM milk_records")
    suspend fun getAllMilkRecordsOnce(): List<MilkRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedTrial(trial: FeedTrialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedTrials(trials: List<FeedTrialEntity>)

    @Update
    suspend fun updateFeedTrial(trial: FeedTrialEntity)

    @Delete
    suspend fun deleteFeedTrial(trial: FeedTrialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkRecord(record: MilkRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkRecords(records: List<MilkRecordEntity>)

    @Delete
    suspend fun deleteMilkRecord(record: MilkRecordEntity)

    @Query("DELETE FROM milk_records WHERE trialId = :trialId")
    suspend fun deleteMilkRecordsForTrial(trialId: Long)

    // --- Complaints ---
    @Query("SELECT * FROM complaints ORDER BY date DESC, id DESC")
    fun observeAllComplaints(): Flow<List<ComplaintEntity>>

    @Query("SELECT * FROM complaints")
    suspend fun getAllComplaintsOnce(): List<ComplaintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: ComplaintEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaints(complaints: List<ComplaintEntity>)

    @Update
    suspend fun updateComplaint(complaint: ComplaintEntity)

    @Delete
    suspend fun deleteComplaint(complaint: ComplaintEntity)

    // --- Visits ---
    @Query("SELECT * FROM visits ORDER BY date DESC, id DESC")
    fun observeAllVisits(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits")
    suspend fun getAllVisitsOnce(): List<VisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(visits: List<VisitEntity>)

    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Delete
    suspend fun deleteVisit(visit: VisitEntity)

    // --- Sales Targets ---
    @Query("SELECT * FROM sales_targets ORDER BY periodLabel DESC")
    fun observeAllTargets(): Flow<List<SalesTargetEntity>>

    @Query("SELECT * FROM sales_targets")
    suspend fun getAllTargetsOnce(): List<SalesTargetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: SalesTargetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(targets: List<SalesTargetEntity>)

    @Update
    suspend fun updateTarget(target: SalesTargetEntity)

    @Delete
    suspend fun deleteTarget(target: SalesTargetEntity)

    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesOnce(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // --- Travel / Odometer ---
    @Query("SELECT * FROM travel_logs ORDER BY date DESC, id DESC")
    fun observeAllTravelLogs(): Flow<List<TravelLogEntity>>

    @Query("SELECT * FROM travel_logs")
    suspend fun getAllTravelLogsOnce(): List<TravelLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTravelLog(log: TravelLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTravelLogs(logs: List<TravelLogEntity>)

    @Update
    suspend fun updateTravelLog(log: TravelLogEntity)

    @Delete
    suspend fun deleteTravelLog(log: TravelLogEntity)

    // --- Custom Field Definitions ---
    @Query("SELECT * FROM custom_field_defs ORDER BY entityType ASC, id ASC")
    fun observeAllCustomFieldDefs(): Flow<List<CustomFieldDefEntity>>

    @Query("SELECT * FROM custom_field_defs")
    suspend fun getAllCustomFieldDefsOnce(): List<CustomFieldDefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFieldDef(def: CustomFieldDefEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFieldDefs(defs: List<CustomFieldDefEntity>)

    @Update
    suspend fun updateCustomFieldDef(def: CustomFieldDefEntity)

    @Delete
    suspend fun deleteCustomFieldDef(def: CustomFieldDefEntity)

    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 250")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // --- Clear / Reset Helpers ---
    @Query("DELETE FROM sale_orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Query("DELETE FROM stock_movements")
    suspend fun clearAllStockMovements()

    @Query("DELETE FROM payments")
    suspend fun clearAllPayments()

    @Query("DELETE FROM feed_trials")
    suspend fun clearAllTrials()

    @Query("DELETE FROM milk_records")
    suspend fun clearAllMilkRecords()

    @Query("DELETE FROM complaints")
    suspend fun clearAllComplaints()

    @Query("DELETE FROM visits")
    suspend fun clearAllVisits()

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()

    @Query("DELETE FROM travel_logs")
    suspend fun clearAllTravelLogs()

    @Query("DELETE FROM dealers")
    suspend fun clearAllDealers()

    @Query("DELETE FROM farmers")
    suspend fun clearAllFarmers()

    @Query("DELETE FROM sales_targets")
    suspend fun clearAllTargets()

    // --- Atomic Order + Items + Stock Out + Initial Payment Transaction ---
    @Transaction
    suspend fun saveOrderTransaction(
        order: SaleOrderEntity,
        items: List<OrderItemEntity>,
        paymentMethodForInitialPaid: String = "Cash",
        createPaymentRecord: Boolean = true
    ): Long {
        val isExisting = order.id > 0
        val savedOrderId = if (isExisting) {
            updateSaleOrder(order)
            deleteOrderItemsForOrder(order.id)
            deleteStockMovementsForOrder(order.id)
            order.id
        } else {
            insertSaleOrder(order)
        }

        val boundItems = items.map { it.copy(orderId = savedOrderId) }
        insertOrderItems(boundItems)

        // If status is Delivered or Partially Delivered, automatically record STOCK_OUT per item
        if (order.status == "Delivered" || order.status == "Partially Delivered") {
            val stockOutMovements = boundItems.map { item ->
                StockMovementEntity(
                    date = order.date,
                    type = "STOCK_OUT",
                    productId = item.productId,
                    productCode = item.productCode,
                    productName = item.productName,
                    bagSizeLabel = item.bagSizeLabel,
                    bagWeightKg = item.bagWeightKg,
                    quantityBags = item.quantityBags,
                    totalKg = item.totalKg,
                    totalMt = item.totalMt,
                    purchasePricePerBag = item.purchasePriceSnapshot,
                    supplierName = order.dealerName,
                    invoiceNumber = order.invoiceNumber,
                    relatedOrderId = savedOrderId,
                    remarks = "Auto Stock-Out from Order ${order.orderNumber} (${order.status})"
                )
            }
            insertStockMovements(stockOutMovements)
        }

        // If this is a brand new order with an initial payment received > 0, record payment in ledger
        if (!isExisting && createPaymentRecord && order.paidAmount > 0.0 && order.status != "Cancelled" && order.status != "Draft") {
            insertPayment(
                PaymentEntity(
                    paymentNumber = "PAY-${order.orderNumber.removePrefix("ORD-")}",
                    date = order.date,
                    dealerId = order.dealerId,
                    dealerName = order.dealerName,
                    amount = order.paidAmount,
                    paymentMethod = paymentMethodForInitialPaid,
                    referenceNumber = order.invoiceNumber,
                    remarks = "Advance/Spot payment against Order ${order.orderNumber}"
                )
            )
        }

        insertAuditLog(
            AuditLogEntity(
                actorRole = order.createdBy,
                actionType = if (isExisting) "UPDATE" else "CREATE",
                module = "Sales Order",
                recordCode = order.orderNumber,
                summary = "${order.dealerName}: ${order.totalBags} bags (${order.totalMt} MT), Net ${order.netAmount}, Status: ${order.status}"
            )
        )
        return savedOrderId
    }
}
