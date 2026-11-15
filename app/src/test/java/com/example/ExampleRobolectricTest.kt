package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: DairyDatabase
    private lateinit var dao: DairyDao
    private lateinit var repository: DairyRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DairyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dairyDao()
        repository = DairyRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `verify WandaBook Pro branding strings`() {
        val appName = context.getString(R.string.app_name)
        val subtitle = context.getString(R.string.app_subtitle)
        val developer = context.getString(R.string.app_developer)
        assertEquals("WandaBook Pro", appName)
        assertEquals("Dairy Feed Sales & Management", subtitle)
        assertEquals("Developed by Attique ur Rehman", developer)
    }

    @Test
    fun `backup validation rejects corrupted JSON and accepts valid format`() {
        val (badOk, _) = DocumentAndExportManager.validateBackupJson("{ invalid json")
        assertFalse(badOk)

        val validJson = """
            {
              "appName": "WandaBook Pro",
              "backupVersion": 2,
              "products": [],
              "dealers": [],
              "orders": [],
              "payments": []
            }
        """.trimIndent()
        val (goodOk, err) = DocumentAndExportManager.validateBackupJson(validJson)
        assertTrue("Expected valid backup JSON, got: $err", goodOk)
    }

    @Test
    fun `verify order creation editing payment backup and restore workflow`() = runBlocking {
        repository.ensureInitialized()

        // 1. Add a new dealer
        val newDealer = DealerEntity(
            code = "DLR-900",
            name = "Chaudhry Aslam",
            businessName = "Aslam Dairy Feeds",
            mobile = "03009998877",
            whatsapp = "03009998877",
            address = "Main Mandi Road",
            village = "Chak 90",
            city = "Sahiwal",
            area = "Sahiwal North",
            dealerType = "Wholesale",
            openingBalance = 10000.0,
            creditLimit = 500000.0,
            assignedSalesPerson = "Attique ur Rehman",
            dateAdded = "2026-11-14",
            status = "Active"
        )
        repository.saveDealer(newDealer)
        val savedDealer = dao.getAllDealersOnce().first { it.code == "DLR-900" }

        // 2. Create an order for 26P (50 bags x 40 KG = 2000 KG = 2.0 MT)
        val prod26P = dao.getAllProductsOnce().first { it.code == "26P" }
        val order = SaleOrderEntity(
            orderNumber = "ORD-TEST-01",
            invoiceNumber = "INV-TEST-01",
            date = "2026-11-14",
            dealerId = savedDealer.id,
            dealerName = savedDealer.businessName,
            dealerAddress = savedDealer.address,
            location = savedDealer.city,
            area = savedDealer.area,
            totalBags = 50,
            totalKg = 2000.0,
            totalMt = 2.0,
            grossAmount = 175000.0,
            discountAmount = 5000.0,
            netAmount = 170000.0,
            paidAmount = 70000.0,
            outstandingAmount = 100000.0,
            estimatedProfit = 12500.0,
            status = "Delivered",
            expectedDeliveryDate = "2026-11-14"
        )
        val item = OrderItemEntity(
            orderId = 0,
            productId = prod26P.id,
            productCode = prod26P.code,
            productName = prod26P.name,
            bagSizeLabel = "40 KG",
            bagWeightKg = 40.0,
            quantityBags = 50,
            totalKg = 2000.0,
            totalMt = 2.0,
            purchasePriceSnapshot = prod26P.purchasePrice,
            pricePerBag = 3500.0,
            grossAmount = 175000.0,
            discountTotal = 5000.0,
            netAmount = 170000.0
        )
        val orderId = repository.saveOrderTransaction(order, listOf(item), "Cash")

        val savedOrder = dao.getAllOrdersOnce().first { it.id == orderId }
        val savedItems = dao.getAllOrderItemsOnce().filter { it.orderId == orderId }
        assertEquals(50, savedOrder.totalBags)
        assertEquals(
            "Partial",
            BusinessCalculations.getOrderPaymentStatus(
                savedOrder.paidAmount,
                savedOrder.netAmount,
                savedOrder.outstandingAmount
            )
        )

        // 3. Edit the order to 60 bags
        val editedOrder = savedOrder.copy(
            totalBags = 60,
            totalKg = 2400.0,
            totalMt = 2.4,
            grossAmount = 210000.0,
            netAmount = 205000.0,
            outstandingAmount = 135000.0
        )
        val editedItem = savedItems.first().copy(
            quantityBags = 60,
            totalKg = 2400.0,
            totalMt = 2.4,
            grossAmount = 210000.0,
            netAmount = 205000.0
        )
        repository.saveOrderTransaction(editedOrder, listOf(editedItem), "Cash")
        val updatedOrder = dao.getAllOrdersOnce().first { it.id == orderId }
        assertEquals(60, updatedOrder.totalBags)

        // 4. Record a payment
        repository.savePayment(
            PaymentEntity(
                paymentNumber = "PAY-TEST-01",
                date = "2026-11-14",
                dealerId = savedDealer.id,
                dealerName = savedDealer.businessName,
                amount = 35000.0,
                paymentMethod = "Bank Transfer",
                referenceNumber = "TXN-999"
            )
        )

        // 5. Create Backup JSON, validate it, and restore safely
        val backupFile = repository.createBackupFile(context)
        assertTrue(backupFile.name.startsWith("WandaBookPro_Backup_"))
        val jsonContent = backupFile.readText()
        val (isValid, validationError) = DocumentAndExportManager.validateBackupJson(jsonContent)
        assertTrue("Backup JSON must be valid: $validationError", isValid)

        val (restoreOk, restoreMsg) = repository.restoreFromLatestBackupWithValidation(context, jsonContent)
        assertTrue("Restore should succeed: $restoreMsg", restoreOk)

        // Verify restored data still contains our dealer and edited 60-bag order
        val dealersAfterRestore = dao.getAllDealersOnce()
        assertTrue(dealersAfterRestore.any { it.code == "DLR-900" })
        val ordersAfterRestore = dao.getAllOrdersOnce()
        assertEquals(60, ordersAfterRestore.first { it.orderNumber == "ORD-TEST-01" }.totalBags)
    }

    @Test
    fun `verify dashboard monthly calculations update dynamically on new order and payment`() = runBlocking {
        repository.ensureInitialized()
        val today = SampleDataSeeder.todayDate()
        val monthPrefix = SampleDataSeeder.currentMonthPrefix()

        val initialMonthOrders = dao.getAllOrdersOnce()
            .filter { it.status != "Cancelled" && it.status != "Draft" && it.date.startsWith(monthPrefix) && it.date <= today }
        val initialBags = initialMonthOrders.sumOf { it.totalBags }
        val initialPayments = dao.getAllPaymentsOnce()
            .filter { it.date.startsWith(monthPrefix) && it.date <= today }
            .sumOf { it.amount }

        assertTrue("Initial seeded monthly bags should be positive", initialBags > 0)
        assertTrue("Initial seeded monthly payments should be positive", initialPayments > 0.0)

        // Add a new order today for 40 bags (1.60 MT) with 50,000 paid
        val dealer = dao.getAllDealersOnce().first()
        val prod = dao.getAllProductsOnce().first { it.code == "26P" }
        repository.saveOrderTransaction(
            order = SaleOrderEntity(
                orderNumber = "ORD-DASH-99",
                invoiceNumber = "INV-DASH-99",
                date = today,
                dealerId = dealer.id,
                dealerName = dealer.businessName,
                dealerAddress = dealer.address,
                location = dealer.city,
                area = dealer.area,
                totalBags = 40,
                totalKg = 1600.0,
                totalMt = 1.6,
                grossAmount = 140000.0,
                discountAmount = 0.0,
                netAmount = 140000.0,
                paidAmount = 50000.0,
                outstandingAmount = 90000.0,
                estimatedProfit = 14000.0,
                status = "Delivered",
                expectedDeliveryDate = today
            ),
            items = listOf(
                OrderItemEntity(
                    orderId = 0,
                    productId = prod.id,
                    productCode = prod.code,
                    productName = prod.name,
                    bagSizeLabel = "40 KG",
                    bagWeightKg = 40.0,
                    quantityBags = 40,
                    totalKg = 1600.0,
                    totalMt = 1.6,
                    purchasePriceSnapshot = prod.purchasePrice,
                    pricePerBag = 3500.0,
                    grossAmount = 140000.0,
                    discountTotal = 0.0,
                    netAmount = 140000.0
                )
            ),
            paymentMethodForInitialPaid = "Cash"
        )

        // Add an additional standalone payment today of Rs. 25,000
        repository.savePayment(
            PaymentEntity(
                paymentNumber = "PAY-DASH-99",
                date = today,
                dealerId = dealer.id,
                dealerName = dealer.businessName,
                amount = 25000.0,
                paymentMethod = "Cash"
            )
        )

        val updatedMonthOrders = dao.getAllOrdersOnce()
            .filter { it.status != "Cancelled" && it.status != "Draft" && it.date.startsWith(monthPrefix) && it.date <= today }
        val updatedBags = updatedMonthOrders.sumOf { it.totalBags }
        val updatedPayments = dao.getAllPaymentsOnce()
            .filter { it.date.startsWith(monthPrefix) && it.date <= today }
            .sumOf { it.amount }

        assertEquals(initialBags + 40, updatedBags)
        assertEquals(initialPayments + 50000.0 + 25000.0, updatedPayments, 0.01)
    }
}
