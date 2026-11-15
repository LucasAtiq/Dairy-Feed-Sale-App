package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class DairyRepository(private val dao: DairyDao) {

    val businessConfig: Flow<BusinessConfigEntity?> = dao.observeBusinessConfig()
    val allLookups: Flow<List<LookupItemEntity>> = dao.observeAllLookups()
    val allBagSizes: Flow<List<BagSizeEntity>> = dao.observeAllBagSizes()
    val allProducts: Flow<List<ProductEntity>> = dao.observeAllProducts()
    val priceHistory: Flow<List<PriceHistoryEntity>> = dao.observePriceHistory()
    val allDealers: Flow<List<DealerEntity>> = dao.observeAllDealers()
    val allFarmers: Flow<List<FarmerEntity>> = dao.observeAllFarmers()
    val allOrdersWithItems: Flow<List<OrderWithItems>> = dao.observeAllOrdersWithItems()
    val allStockMovements: Flow<List<StockMovementEntity>> = dao.observeAllStockMovements()
    val allPayments: Flow<List<PaymentEntity>> = dao.observeAllPayments()
    val allTrialsWithMilk: Flow<List<TrialWithMilkRecords>> = dao.observeAllTrialsWithMilk()
    val allComplaints: Flow<List<ComplaintEntity>> = dao.observeAllComplaints()
    val allTargets: Flow<List<SalesTargetEntity>> = dao.observeAllTargets()
    val allExpenses: Flow<List<ExpenseEntity>> = dao.observeAllExpenses()
    val allTravelLogs: Flow<List<TravelLogEntity>> = dao.observeAllTravelLogs()
    val allCustomFieldDefs: Flow<List<CustomFieldDefEntity>> = dao.observeAllCustomFieldDefs()
    val auditLogs: Flow<List<AuditLogEntity>> = dao.observeAuditLogs()

    suspend fun ensureInitialized() {
        SampleDataSeeder.ensureInitialized(dao)
    }

    suspend fun saveBusinessConfig(config: BusinessConfigEntity) {
        dao.saveBusinessConfig(config)
    }

    // --- Lookups ---
    suspend fun saveLookup(item: LookupItemEntity) {
        if (item.id > 0) dao.updateLookup(item) else dao.insertLookup(item)
    }

    suspend fun deleteLookup(item: LookupItemEntity) = dao.deleteLookup(item)

    // --- Bag Sizes ---
    suspend fun saveBagSize(bagSize: BagSizeEntity) {
        if (bagSize.id > 0) dao.updateBagSize(bagSize) else dao.insertBagSize(bagSize)
    }

    suspend fun deleteBagSize(bagSize: BagSizeEntity) = dao.deleteBagSize(bagSize)

    // --- Products & Price History ---
    suspend fun saveProduct(product: ProductEntity, priceChangeRemarks: String = "") {
        if (product.id > 0) {
            val old = dao.getProductById(product.id)
            if (old != null && (
                    old.purchasePrice != product.purchasePrice ||
                        old.dealerPrice != product.dealerPrice ||
                        old.salePrice != product.salePrice ||
                        old.retailPrice != product.retailPrice
                    )
            ) {
                dao.insertPriceHistory(
                    PriceHistoryEntity(
                        productId = product.id,
                        productCode = product.code,
                        productName = product.name,
                        oldPurchasePrice = old.purchasePrice,
                        newPurchasePrice = product.purchasePrice,
                        oldDealerPrice = old.dealerPrice,
                        newDealerPrice = product.dealerPrice,
                        oldSalePrice = old.salePrice,
                        newSalePrice = product.salePrice,
                        oldRetailPrice = old.retailPrice,
                        newRetailPrice = product.retailPrice,
                        remarks = priceChangeRemarks.ifBlank { "Updated product prices from Admin/Products" }
                    )
                )
                dao.insertAuditLog(
                    AuditLogEntity(
                        actorRole = product.updatedBy,
                        actionType = "PRICE_CHANGE",
                        module = "Products",
                        recordCode = product.code,
                        summary = "Sale price: ${old.salePrice} -> ${product.salePrice}, Purchase: ${old.purchasePrice} -> ${product.purchasePrice}"
                    )
                )
            }
            dao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
        } else {
            val newId = dao.insertProduct(product)
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = product.updatedBy,
                    actionType = "CREATE",
                    module = "Products",
                    recordCode = product.code,
                    summary = "Added product ${product.code} (${product.name}) at Sale Price ${product.salePrice}"
                )
            )
        }
    }

    suspend fun deleteProductSafely(product: ProductEntity): Boolean {
        val usedInOrders = dao.getAllOrderItemsOnce().any { it.productId == product.id }
        if (usedInOrders) {
            // Historical safety rule: archive instead of deleting if referenced in historical orders
            dao.updateProduct(product.copy(isArchived = true, isActive = false))
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = "Admin",
                    actionType = "ARCHIVE",
                    module = "Products",
                    recordCode = product.code,
                    summary = "Archived product ${product.code} to preserve historical sales records"
                )
            )
            return false
        } else {
            dao.deleteProduct(product)
            return true
        }
    }

    // --- Dealers ---
    suspend fun saveDealer(dealer: DealerEntity) {
        if (dealer.id > 0) {
            dao.updateDealer(dealer.copy(updatedAt = System.currentTimeMillis()))
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = dealer.updatedBy,
                    actionType = "UPDATE",
                    module = "Dealers",
                    recordCode = dealer.code,
                    summary = "Updated dealer ${dealer.businessName} (${dealer.status})"
                )
            )
        } else {
            dao.insertDealer(dealer)
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = dealer.updatedBy,
                    actionType = "CREATE",
                    module = "Dealers",
                    recordCode = dealer.code,
                    summary = "Created dealer ${dealer.businessName} in ${dealer.area}"
                )
            )
        }
    }

    suspend fun archiveOrDeleteDealer(dealer: DealerEntity, forceArchiveOnly: Boolean = true) {
        val hasHistory = dao.getAllOrdersOnce().any { it.dealerId == dealer.id } ||
            dao.getAllPaymentsOnce().any { it.dealerId == dealer.id }
        if (forceArchiveOnly || hasHistory) {
            dao.updateDealer(dealer.copy(isArchived = true, status = "Inactive", updatedAt = System.currentTimeMillis()))
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = "Admin",
                    actionType = "ARCHIVE",
                    module = "Dealers",
                    recordCode = dealer.code,
                    summary = "Archived dealer ${dealer.businessName} (historical records preserved)"
                )
            )
        } else {
            dao.deleteDealer(dealer)
            dao.insertAuditLog(
                AuditLogEntity(
                    actorRole = "Admin",
                    actionType = "DELETE",
                    module = "Dealers",
                    recordCode = dealer.code,
                    summary = "Deleted dealer ${dealer.businessName} with zero transaction history"
                )
            )
        }
    }

    // --- Farmers ---
    suspend fun saveFarmer(farmer: FarmerEntity) {
        if (farmer.id > 0) {
            dao.updateFarmer(farmer.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.insertFarmer(farmer)
        }
    }

    suspend fun archiveOrDeleteFarmer(farmer: FarmerEntity) {
        dao.updateFarmer(farmer.copy(isArchived = true, status = "Inactive", updatedAt = System.currentTimeMillis()))
    }

    // --- Orders ---
    suspend fun saveOrderTransaction(
        order: SaleOrderEntity,
        items: List<OrderItemEntity>,
        paymentMethodForInitialPaid: String = "Cash"
    ): Long {
        return dao.saveOrderTransaction(order, items, paymentMethodForInitialPaid, createPaymentRecord = true)
    }

    suspend fun cancelOrDeleteOrder(order: SaleOrderEntity) {
        // Mark Cancelled and remove auto stock-out movements so inventory & ledger stay consistent
        dao.deleteStockMovementsForOrder(order.id)
        dao.updateSaleOrder(order.copy(status = "Cancelled", updatedAt = System.currentTimeMillis()))
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = "Admin",
                actionType = "ARCHIVE",
                module = "Sales Order",
                recordCode = order.orderNumber,
                summary = "Cancelled order ${order.orderNumber} and reversed stock-out"
            )
        )
    }

    // --- Stock Movements ---
    suspend fun saveStockMovement(movement: StockMovementEntity) {
        dao.insertStockMovement(movement)
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = movement.createdBy,
                actionType = "CREATE",
                module = "Inventory",
                recordCode = movement.productCode,
                summary = "${movement.type}: ${movement.quantityBags} bags (${movement.bagSizeLabel}) of ${movement.productCode}"
            )
        )
    }

    suspend fun deleteStockMovement(movement: StockMovementEntity) {
        dao.deleteStockMovement(movement)
    }

    // --- Payments ---
    suspend fun savePayment(payment: PaymentEntity) {
        val isUpdate = payment.id > 0
        if (isUpdate) {
            dao.updatePayment(payment.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.insertPayment(payment)
        }
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = payment.createdBy,
                actionType = if (isUpdate) "UPDATE" else "CREATE",
                module = "Payments",
                recordCode = payment.paymentNumber,
                summary = "${payment.dealerName}: ${payment.amount} via ${payment.paymentMethod}"
            )
        )
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        dao.deletePayment(payment)
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = "Admin",
                actionType = "DELETE",
                module = "Payments",
                recordCode = payment.paymentNumber,
                summary = "Deleted payment ${payment.paymentNumber} (${payment.amount}) for ${payment.dealerName}"
            )
        )
    }

    // --- Feed Trials & Milk Records ---
    suspend fun saveFeedTrial(trial: FeedTrialEntity): Long {
        return if (trial.id > 0) {
            dao.updateFeedTrial(trial.copy(updatedAt = System.currentTimeMillis()))
            trial.id
        } else {
            dao.insertFeedTrial(trial)
        }
    }

    suspend fun deleteFeedTrial(trial: FeedTrialEntity) {
        dao.deleteMilkRecordsForTrial(trial.id)
        dao.deleteFeedTrial(trial)
    }

    suspend fun saveMilkRecord(record: MilkRecordEntity) {
        dao.insertMilkRecord(record)
    }

    suspend fun deleteMilkRecord(record: MilkRecordEntity) {
        dao.deleteMilkRecord(record)
    }

    // --- Complaints ---
    suspend fun saveComplaint(complaint: ComplaintEntity) {
        if (complaint.id > 0) {
            dao.updateComplaint(complaint.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.insertComplaint(complaint)
        }
    }

    suspend fun deleteComplaint(complaint: ComplaintEntity) = dao.deleteComplaint(complaint)

    // --- Targets ---
    suspend fun saveTarget(target: SalesTargetEntity) {
        if (target.id > 0) dao.updateTarget(target) else dao.insertTarget(target)
    }

    suspend fun deleteTarget(target: SalesTargetEntity) = dao.deleteTarget(target)

    // --- Expenses & Travel ---
    suspend fun saveExpense(expense: ExpenseEntity) {
        if (expense.id > 0) dao.updateExpense(expense) else dao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = dao.deleteExpense(expense)

    suspend fun saveTravelLog(log: TravelLogEntity) {
        if (log.id > 0) dao.updateTravelLog(log) else dao.insertTravelLog(log)
    }

    suspend fun deleteTravelLog(log: TravelLogEntity) = dao.deleteTravelLog(log)

    // --- Custom Fields ---
    suspend fun saveCustomFieldDef(def: CustomFieldDefEntity) {
        if (def.id > 0) dao.updateCustomFieldDef(def) else dao.insertCustomFieldDef(def)
    }

    // --- Sample Data & Backup/Restore ---
    suspend fun reloadSampleData() {
        SampleDataSeeder.clearAllTransactionalAndSampleData(dao)
        SampleDataSeeder.seedCoreConfigurableDefaults(dao)
        SampleDataSeeder.seedSampleRecords(dao)
        val cfg = dao.getBusinessConfigOnce() ?: BusinessConfigEntity()
        dao.saveBusinessConfig(cfg.copy(isSampleDataLoaded = true))
    }

    suspend fun clearSampleDataStartFresh() {
        SampleDataSeeder.clearAllTransactionalAndSampleData(dao)
    }

    suspend fun createBackupFile(context: Context): File {
        return DocumentAndExportManager.saveBackupToLocalFile(context, dao, isAutoBackup = false)
    }

    suspend fun runAutoBackupIfDue(context: Context) {
        DocumentAndExportManager.checkAndRunAutoBackupIfDue(context, dao)
    }

    suspend fun dispatchEmailBackup(context: Context, recipientEmail: String, isTest: Boolean = false): Pair<Boolean, String> {
        return DocumentAndExportManager.dispatchEmailBackup(context, dao, recipientEmail, isTest)
    }

    suspend fun restoreFromLatestBackup(context: Context, customJson: String? = null): Boolean {
        return DocumentAndExportManager.restoreFromBackupJson(context, dao, customJson)
    }

    suspend fun restoreFromLatestBackupWithValidation(context: Context, customJson: String? = null): Pair<Boolean, String> {
        return DocumentAndExportManager.restoreFromBackupJsonWithValidation(context, dao, customJson)
    }

    suspend fun syncToGoogleDrive(context: Context, folderUri: String): Pair<Boolean, String> {
        return DocumentAndExportManager.syncBackupToGoogleDriveFolder(context, dao, folderUri)
    }

    suspend fun restoreFromGoogleDrive(context: Context, folderUri: String): Pair<Boolean, String> {
        return DocumentAndExportManager.restoreFromGoogleDriveFolder(context, dao, folderUri)
    }
}
