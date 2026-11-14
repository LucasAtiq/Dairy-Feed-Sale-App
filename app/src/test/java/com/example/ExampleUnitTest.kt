package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun bagWeightAndMtCalculations_areCorrect() {
        // 100 bags x 40 KG = 4,000 KG = 4 MT
        val totalKg = BusinessCalculations.calculateTotalKg(100, 40.0)
        val totalMt = BusinessCalculations.calculateTotalMt(totalKg)
        assertEquals(4000.0, totalKg, 0.001)
        assertEquals(4.0, totalMt, 0.001)
    }

    @Test
    fun orderAndDealerPaymentStatuses_areCalculatedCorrectly() {
        assertEquals("Paid", BusinessCalculations.getOrderPaymentStatus(paidAmount = 50000.0, netAmount = 50000.0, outstandingAmount = 0.0))
        assertEquals("Partial", BusinessCalculations.getOrderPaymentStatus(paidAmount = 20000.0, netAmount = 50000.0, outstandingAmount = 30000.0))
        assertEquals("Unpaid", BusinessCalculations.getOrderPaymentStatus(paidAmount = 0.0, netAmount = 50000.0, outstandingAmount = 50000.0))

        assertEquals("Paid", BusinessCalculations.getDealerPaymentStatus(totalSales = 100000.0, totalPayments = 100000.0, outstandingBalance = 0.0))
        assertEquals("Partial", BusinessCalculations.getDealerPaymentStatus(totalSales = 100000.0, totalPayments = 40000.0, outstandingBalance = 60000.0))
        assertEquals("Unpaid", BusinessCalculations.getDealerPaymentStatus(totalSales = 100000.0, totalPayments = 0.0, outstandingBalance = 100000.0))
    }

    @Test
    fun stockFormula_openingPlusInMinusOut_isCorrect() {
        val prod = ProductEntity(
            id = 1,
            code = "26P",
            name = "26P Pellets",
            category = "Dairy",
            description = "",
            defaultBagWeightKg = 40.0,
            purchasePrice = 3150.0,
            dealerPrice = 3500.0,
            salePrice = 3500.0,
            retailPrice = 3650.0,
            minStockBags = 100,
            openingStockBags = 300
        )
        val movements = listOf(
            StockMovementEntity(
                date = "2026-11-10",
                type = "STOCK_IN",
                productId = 1,
                productCode = "26P",
                productName = "26P Pellets",
                quantityBags = 200,
                totalKg = 8000.0,
                totalMt = 8.0
            ),
            StockMovementEntity(
                date = "2026-11-12",
                type = "STOCK_OUT",
                productId = 1,
                productCode = "26P",
                productName = "26P Pellets",
                quantityBags = 120,
                totalKg = 4800.0,
                totalMt = 4.8
            )
        )
        val status = BusinessCalculations.computeProductStockStatuses(listOf(prod), movements).first()
        // 300 + 200 - 120 = 380 bags
        assertEquals(380, status.currentBags)
        assertFalse(status.isLowStock)
    }

    @Test
    fun dealerLedgerAndPriceHistory_preserveHistoricalPrices() {
        val dealer = DealerEntity(
            id = 1,
            code = "DLR-001",
            name = "Tariq",
            businessName = "Al-Madina",
            mobile = "03001234567",
            whatsapp = "03001234567",
            address = "Market",
            village = "",
            city = "Sahiwal",
            area = "Sahiwal North",
            dealerType = "Wholesale",
            openingBalance = 50000.0,
            dateAdded = "2026-11-01"
        )
        val oldOrder = OrderWithItems(
            order = SaleOrderEntity(
                id = 10,
                orderNumber = "ORD-1",
                invoiceNumber = "INV-1",
                date = "2026-11-10",
                dealerId = 1,
                dealerName = "Al-Madina",
                location = "Sahiwal",
                area = "Sahiwal North",
                totalBags = 10,
                totalKg = 400.0,
                totalMt = 0.4,
                grossAmount = 35000.0,
                discountAmount = 0.0,
                netAmount = 35000.0,
                paidAmount = 0.0,
                outstandingAmount = 35000.0,
                estimatedProfit = 3500.0,
                status = "Delivered",
                expectedDeliveryDate = "2026-11-10"
            ),
            items = listOf(
                OrderItemEntity(
                    id = 1,
                    orderId = 10,
                    productId = 1,
                    productCode = "26P",
                    productName = "26P",
                    bagSizeLabel = "40 KG",
                    bagWeightKg = 40.0,
                    quantityBags = 10,
                    totalKg = 400.0,
                    totalMt = 0.4,
                    purchasePriceSnapshot = 3150.0,
                    pricePerBag = 3500.0,
                    grossAmount = 35000.0,
                    discountTotal = 0.0,
                    netAmount = 35000.0
                )
            )
        )
        val payment = PaymentEntity(
            id = 5,
            paymentNumber = "PAY-1",
            date = "2026-11-11",
            dealerId = 1,
            dealerName = "Al-Madina",
            amount = 20000.0,
            paymentMethod = "Cash"
        )
        val summary = BusinessCalculations.computeDealerSummary(dealer, listOf(oldOrder), listOf(payment))
        // Opening 50,000 + Sale 35,000 - Payment 20,000 = 65,000
        assertEquals(65000.0, summary.outstandingBalance, 0.01)
    }
}
