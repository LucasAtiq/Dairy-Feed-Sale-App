package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Dynamic Lookup / Admin Configurable Options:
 * Types: AREA, DEALER_TYPE, PAYMENT_METHOD, COMPLAINT_TYPE, EXPENSE_CATEGORY, CATEGORY
 */
@Entity(
    tableName = "lookup_items",
    indices = [Index(value = ["categoryType", "name"])]
)
data class LookupItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryType: String,
    val name: String,
    val description: String = "",
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Dynamic Bag Sizes (Initial: 20 KG, 40 KG, editable by Admin)
 */
@Entity(tableName = "bag_sizes")
data class BagSizeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String, // e.g., "40 KG"
    val weightKg: Double, // e.g., 40.0
    val isActive: Boolean = true,
    val isArchived: Boolean = false
)

/**
 * Dynamic Products (Initial: 26P, 28P, 26M, 55M, CP Mamni, editable by Admin)
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["code"]), Index(value = ["name"])]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val category: String,
    val description: String,
    val defaultBagWeightKg: Double = 40.0,
    val purchasePrice: Double,
    val dealerPrice: Double,
    val salePrice: Double,
    val retailPrice: Double,
    val specialPrice: Double = 0.0,
    val defaultDiscount: Double = 0.0,
    val minStockBags: Int = 50,
    val openingStockBags: Int = 0,
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "Admin"
)

/**
 * Price History log so every price adjustment is audited while old transactions keep their snapshotted prices
 */
@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productCode: String,
    val productName: String,
    val oldPurchasePrice: Double,
    val newPurchasePrice: Double,
    val oldDealerPrice: Double,
    val newDealerPrice: Double,
    val oldSalePrice: Double,
    val newSalePrice: Double,
    val oldRetailPrice: Double,
    val newRetailPrice: Double,
    val changedAt: Long = System.currentTimeMillis(),
    val changedBy: String = "Admin",
    val remarks: String = ""
)

/**
 * Dealer Profile
 * Status: Active, Inactive, Potential, Blocked
 */
@Entity(
    tableName = "dealers",
    indices = [Index(value = ["code"]), Index(value = ["name"]), Index(value = ["area"])]
)
data class DealerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String, // e.g., DLR-001
    val name: String,
    val businessName: String,
    val mobile: String,
    val whatsapp: String,
    val address: String,
    val village: String,
    val city: String,
    val area: String,
    val dealerType: String,
    val openingBalance: Double = 0.0,
    val creditLimit: Double = 500000.0,
    val assignedSalesPerson: String = "Regional Sales Manager",
    val dateAdded: String, // YYYY-MM-DD
    val status: String = "Active", // Active, Inactive, Potential, Blocked
    val notes: String = "",
    val customFieldsJson: String = "{}", // Key-value JSON of custom fields
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "Admin"
)

/**
 * Farmer Profile
 */
@Entity(
    tableName = "farmers",
    indices = [Index(value = ["code"]), Index(value = ["name"]), Index(value = ["dealerId"])]
)
data class FarmerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String, // e.g., FRM-001
    val name: String,
    val mobile: String,
    val whatsapp: String,
    val farmName: String,
    val address: String,
    val village: String,
    val city: String,
    val area: String,
    val animalCount: Int,
    val currentFeed: String,
    val monthlyConsumptionBags: Int,
    val dealerId: Long? = null,
    val dealerName: String = "",
    val dateAdded: String, // YYYY-MM-DD
    val status: String = "Active", // Active, Potential, Inactive
    val notes: String = "",
    val customFieldsJson: String = "{}",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Sales Order Header
 * Status: Draft, Confirmed, Pending, In Transit, Delivered, Partially Delivered, Cancelled
 */
@Entity(
    tableName = "sale_orders",
    indices = [Index(value = ["orderNumber"]), Index(value = ["invoiceNumber"]), Index(value = ["dealerId"]), Index(value = ["date"])]
)
data class SaleOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String, // e.g., ORD-2026-001
    val invoiceNumber: String, // e.g., INV-2026-001
    val date: String, // YYYY-MM-DD
    val dealerId: Long,
    val dealerName: String,
    val dealerAddress: String = "",
    val farmerId: Long? = null,
    val farmerName: String = "",
    val location: String,
    val area: String,
    val totalBags: Int,
    val totalKg: Double,
    val totalMt: Double,
    val grossAmount: Double,
    val discountAmount: Double,
    val netAmount: Double,
    val paidAmount: Double,
    val outstandingAmount: Double,
    val estimatedProfit: Double,
    val status: String, // Draft, Confirmed, Pending, In Transit, Delivered, Partially Delivered, Cancelled
    val expectedDeliveryDate: String,
    val remarks: String = "",
    val isSampleData: Boolean = false,
    val createdBy: String = "Sales Manager",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Sales Order Line Item - stores exact transaction price so future price changes never alter historical orders!
 */
@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"]), Index(value = ["productId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productCode: String,
    val productName: String,
    val bagSizeLabel: String, // e.g., "40 KG"
    val bagWeightKg: Double,  // e.g., 40.0
    val quantityBags: Int,
    val totalKg: Double,      // quantityBags * bagWeightKg
    val totalMt: Double,      // totalKg / 1000.0
    val purchasePriceSnapshot: Double, // Snapshotted cost per bag at time of sale
    val pricePerBag: Double,           // Snapshotted sale price per bag at time of sale
    val discountPerBag: Double = 0.0,
    val grossAmount: Double,           // quantityBags * pricePerBag
    val discountTotal: Double,         // quantityBags * discountPerBag
    val netAmount: Double              // grossAmount - discountTotal
)

data class OrderWithItems(
    @Embedded val order: SaleOrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

/**
 * Transaction-based Stock Movement
 * type: OPENING, STOCK_IN, STOCK_OUT, ADJUSTMENT
 */
@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["productId"]), Index(value = ["date"]), Index(value = ["relatedOrderId"])]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val type: String, // OPENING, STOCK_IN, STOCK_OUT, ADJUSTMENT
    val productId: Long,
    val productCode: String,
    val productName: String,
    val bagSizeLabel: String = "40 KG",
    val bagWeightKg: Double = 40.0,
    val quantityBags: Int, // Positive for IN/OPENING, Positive for OUT (handled by type)
    val totalKg: Double,
    val totalMt: Double,
    val purchasePricePerBag: Double = 0.0,
    val supplierName: String = "",
    val invoiceNumber: String = "",
    val relatedOrderId: Long? = null,
    val remarks: String = "",
    val createdBy: String = "Sales Manager",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Payment Entry
 */
@Entity(
    tableName = "payments",
    indices = [Index(value = ["dealerId"]), Index(value = ["date"]), Index(value = ["paymentNumber"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentNumber: String, // PAY-2026-001
    val date: String,          // YYYY-MM-DD
    val dealerId: Long,
    val dealerName: String,
    val amount: Double,
    val paymentMethod: String, // Cash, Bank Transfer, Online, Cheque
    val referenceNumber: String = "",
    val bankAccount: String = "",
    val remarks: String = "",
    val createdBy: String = "Sales Manager",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Feed Trial Entity
 * Status: Planned, Started, Transition, Under Observation, Completed, Successful, Unsuccessful, Cancelled
 */
@Entity(
    tableName = "feed_trials",
    indices = [Index(value = ["trialCode"]), Index(value = ["farmerId"]), Index(value = ["dealerId"])]
)
data class FeedTrialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trialCode: String, // TRL-2026-001
    val farmerId: Long,
    val farmerName: String,
    val dealerId: Long? = null,
    val dealerName: String = "",
    val farmName: String,
    val location: String,
    val animalCount: Int,
    val currentFeed: String,
    val trialFeed: String,
    val currentFeedCode: String,
    val trialFeedCode: String,
    val startDate: String, // YYYY-MM-DD
    val transitionDays: Int = 7,
    val endDate: String,   // YYYY-MM-DD
    val status: String,    // Planned, Started, Transition, Under Observation, Completed, Successful, Unsuccessful, Cancelled
    val remarks: String = "",
    val customFieldsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Milk Production Tracking per Animal inside a Feed Trial
 */
@Entity(
    tableName = "milk_records",
    indices = [Index(value = ["trialId"]), Index(value = ["recordDate"])]
)
data class MilkRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trialId: Long,
    val animalIdTag: String, // e.g. "Tag #104 (Holstein)"
    val recordDate: String,  // YYYY-MM-DD
    val startingMilkLiters: Double,
    val dailyMilkLiters: Double,
    val feedQuantityKg: Double,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class TrialWithMilkRecords(
    @Embedded val trial: FeedTrialEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trialId"
    )
    val milkRecords: List<MilkRecordEntity>
)

/**
 * Complaint Management
 * Status: Open, Under Investigation, Follow-up, Resolved, Closed
 */
@Entity(
    tableName = "complaints",
    indices = [Index(value = ["complaintCode"]), Index(value = ["date"])]
)
data class ComplaintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val complaintCode: String, // CMP-2026-001
    val date: String,
    val partnerType: String, // Dealer or Farmer
    val partnerId: Long,
    val partnerName: String,
    val productId: Long? = null,
    val productName: String,
    val complaintType: String,
    val description: String,
    val milkChange: String = "",
    val animalDetails: String = "",
    val investigation: String = "",
    val actionTaken: String = "",
    val followUpDate: String = "",
    val status: String = "Open", // Open, Under Investigation, Follow-up, Resolved, Closed
    val resolution: String = "",
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Sales Targets (Monthly / Weekly)
 */
@Entity(tableName = "sales_targets")
data class SalesTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodLabel: String, // e.g., "2026-11" or "2026-W46"
    val periodType: String = "Monthly", // Monthly, Weekly
    val targetBags: Int,
    val targetMt: Double,
    val targetSalesValue: Double,
    val targetCollection: Double,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Expense Management
 */
@Entity(
    tableName = "expenses",
    indices = [Index(value = ["date"]), Index(value = ["category"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val category: String, // Fuel, Transport, Food, Hotel, Ticket, Other
    val amount: Double,
    val paymentMethod: String,
    val location: String,
    val description: String,
    val receiptReference: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Travel / Odometer Tracking
 */
@Entity(
    tableName = "travel_logs",
    indices = [Index(value = ["date"])]
)
data class TravelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val startingOdometer: Double,
    val endingOdometer: Double,
    val startingLocation: String,
    val destination: String,
    val totalKm: Double,   // endingOdometer - startingOdometer
    val workingKm: Double,
    val personalKm: Double,
    val purpose: String,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Dynamic Custom Field Definition
 * entityType: DEALER, FARMER, TRIAL
 * fieldType: Text, Number, Decimal, Date, Dropdown, Checkbox, Phone, Long Text
 */
@Entity(tableName = "custom_field_defs")
data class CustomFieldDefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String, // DEALER, FARMER, TRIAL
    val fieldName: String,
    val fieldType: String,  // Text, Number, Decimal, Date, Dropdown, Checkbox, Phone, Long Text
    val dropdownOptions: String = "", // Comma-separated if Dropdown
    val isRequired: Boolean = false,
    val isVisible: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Audit Log for sensitive financial/business changes
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actorRole: String,
    val actionType: String, // CREATE, UPDATE, ARCHIVE, DELETE, PRICE_CHANGE, BACKUP, RESTORE
    val module: String,
    val recordCode: String,
    val summary: String
)

/**
 * Business & Security Configuration (Singleton row id = 1)
 */
@Entity(tableName = "business_config")
data class BusinessConfigEntity(
    @PrimaryKey val id: Int = 1,
    // Company / Business Profile
    val companyName: String = "GreenPasture Dairy Feeds (Pvt) Ltd.",
    val businessTitle: String = "Premium Dairy Nutrition & Field Operations",
    val contactPhone: String = "+92 300 8451290",
    val contactEmail: String = "sales@greenpasturefeeds.com",
    val companyAddress: String = "7-KM Sahiwal-Okara GT Road, Industrial Estate, Sahiwal",
    val currencySymbol: String = "Rs.",
    val taxRegistrationNumber: String = "NTN-4829104-7",
    val invoicePrefix: String = "INV-2026-",

    // User / Personal Profile (Separate from Company)
    val userName: String = "Attique ur Rehman",
    val userDesignation: String = "Region Sale Manager",
    val userPhone: String = "0300-1234567",
    val userEmail: String = "attiqueurrehman41@gmail.com",
    val userTerritory: String = "Sahiwal / Faisalabad Division",

    val activeRole: String = "Administrator", // Administrator, Regional Sales Manager
    val pinLockEnabled: Boolean = false,
    val securityPin: String = "1234",
    val sessionTimeoutMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val notifyPaymentDue: Boolean = true,
    val notifyFollowUpDue: Boolean = true,
    val notifyTrialFollowUp: Boolean = true,
    val notifyPendingDelivery: Boolean = true,
    val notifyLowStock: Boolean = true,
    val notifyTargetProgress: Boolean = true,
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val isSampleDataLoaded: Boolean = false,
    val lastBackupAt: String = "Never",
    val autoBackupFrequency: String = "Daily", // OFF, Daily, Weekly
    val lastBackupStatus: String = "Ready",
    val nextBackupAt: String = "Daily at 07:30 PM",
    val emailBackupEnabled: Boolean = false,
    val backupEmailAddress: String = "",
    val emailBackupFrequency: String = "Daily", // Daily, Weekly
    val emailBackupTime: String = "07:30 PM",
    val emailWebhookUrl: String = ""
)
