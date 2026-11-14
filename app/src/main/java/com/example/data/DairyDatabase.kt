package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        LookupItemEntity::class,
        BagSizeEntity::class,
        ProductEntity::class,
        PriceHistoryEntity::class,
        DealerEntity::class,
        FarmerEntity::class,
        SaleOrderEntity::class,
        OrderItemEntity::class,
        StockMovementEntity::class,
        PaymentEntity::class,
        FeedTrialEntity::class,
        MilkRecordEntity::class,
        ComplaintEntity::class,
        SalesTargetEntity::class,
        ExpenseEntity::class,
        TravelLogEntity::class,
        CustomFieldDefEntity::class,
        AuditLogEntity::class,
        BusinessConfigEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class DairyDatabase : RoomDatabase() {
    abstract fun dairyDao(): DairyDao

    companion object {
        @Volatile
        private var INSTANCE: DairyDatabase? = null

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_config ADD COLUMN autoBackupFrequency TEXT NOT NULL DEFAULT 'Daily'")
                db.execSQL("ALTER TABLE business_config ADD COLUMN lastBackupStatus TEXT NOT NULL DEFAULT 'Ready'")
                db.execSQL("ALTER TABLE business_config ADD COLUMN nextBackupAt TEXT NOT NULL DEFAULT 'Daily at 07:30 PM'")
                db.execSQL("ALTER TABLE business_config ADD COLUMN emailBackupEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE business_config ADD COLUMN backupEmailAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_config ADD COLUMN emailBackupFrequency TEXT NOT NULL DEFAULT 'Daily'")
                db.execSQL("ALTER TABLE business_config ADD COLUMN emailBackupTime TEXT NOT NULL DEFAULT '07:30 PM'")
                db.execSQL("ALTER TABLE business_config ADD COLUMN emailWebhookUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): DairyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DairyDatabase::class.java,
                    "dairy_feed_business.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object SampleDataSeeder {

    fun todayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun currentMonthPrefix(): String {
        return SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    fun daysOffset(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    suspend fun ensureInitialized(dao: DairyDao) {
        val config = dao.getBusinessConfigOnce()
        if (config != null) return

        // 1. Save default Business Config (clean state, no sample data)
        dao.saveBusinessConfig(BusinessConfigEntity(id = 1, isSampleDataLoaded = false))

        // 2. Seed core configurable settings (Products, Bag Sizes, Lookups, Custom Fields)
        seedCoreConfigurableDefaults(dao)

        // 3. Sample records seeding disabled - app starts completely fresh & clean
    }

    suspend fun seedCoreConfigurableDefaults(dao: DairyDao) {
        if (dao.getAllBagSizesOnce().isEmpty()) {
            dao.insertBagSizes(
                listOf(
                    BagSizeEntity(label = "20 KG", weightKg = 20.0, isActive = true),
                    BagSizeEntity(label = "40 KG", weightKg = 40.0, isActive = true)
                )
            )
        }

        if (dao.getAllLookupsOnce().isEmpty()) {
            val lookups = mutableListOf<LookupItemEntity>()
            listOf("Sahiwal North", "Okara Central", "Kasur East", "Pakpattan South", "Lahore Dairy Belt", "Faisalabad West")
                .forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "AREA", name = name, sortOrder = i)) }

            listOf("Wholesale Distributor", "Sub-Dealer", "Direct Farm Agency", "Key Account Dealer")
                .forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "DEALER_TYPE", name = name, sortOrder = i)) }

            listOf("Cash", "Bank Transfer", "Online", "Cheque")
                .forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "PAYMENT_METHOD", name = name, sortOrder = i)) }

            listOf(
                "Milk Yield Variation",
                "Palatability / Feed Refusal",
                "Pellet Fines / Dust",
                "Moisture / Caking",
                "Bag Stitching Issue",
                "Delivery Delay"
            ).forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "COMPLAINT_TYPE", name = name, sortOrder = i)) }

            listOf("Fuel", "Transport", "Food", "Hotel", "Ticket", "Other")
                .forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "EXPENSE_CATEGORY", name = name, sortOrder = i)) }

            listOf(
                "High Yield Pelleted Feed",
                "Standard Milking Concentrate",
                "Buffalo Special Mash",
                "Bypass Protein Ration",
                "Calf & Heifer Starter"
            ).forEachIndexed { i, name -> lookups.add(LookupItemEntity(categoryType = "CATEGORY", name = name, sortOrder = i)) }

            dao.insertLookups(lookups)
        }

        if (dao.getAllProductsOnce().isEmpty()) {
            dao.insertProducts(
                listOf(
                    ProductEntity(
                        id = 1,
                        code = "26P",
                        name = "26P High-Yield Dairy Pellets",
                        category = "High Yield Pelleted Feed",
                        description = "18% CP balanced energy pellet for early-to-mid lactation crossbred & exotic cows.",
                        defaultBagWeightKg = 40.0,
                        purchasePrice = 3150.0,
                        dealerPrice = 3500.0,
                        salePrice = 3500.0,
                        retailPrice = 3680.0,
                        specialPrice = 3440.0,
                        minStockBags = 0,
                        openingStockBags = 0
                    ),
                    ProductEntity(
                        id = 2,
                        code = "28P",
                        name = "28P Super Peak Lactation Pellets",
                        category = "High Yield Pelleted Feed",
                        description = "21% CP bypass protein + rumen buffer formula for 25+ liter high-producing cows.",
                        defaultBagWeightKg = 40.0,
                        purchasePrice = 3420.0,
                        dealerPrice = 3800.0,
                        salePrice = 3800.0,
                        retailPrice = 3980.0,
                        specialPrice = 3740.0,
                        minStockBags = 0,
                        openingStockBags = 0
                    ),
                    ProductEntity(
                        id = 3,
                        code = "26M",
                        name = "26M Dairy Gold Coarse Mash",
                        category = "Buffalo Special Mash",
                        description = "High-fat palatability coarse mash tailored for Nili-Ravi buffaloes & mixed herds.",
                        defaultBagWeightKg = 40.0,
                        purchasePrice = 3080.0,
                        dealerPrice = 3420.0,
                        salePrice = 3420.0,
                        retailPrice = 3580.0,
                        specialPrice = 3360.0,
                        minStockBags = 0,
                        openingStockBags = 0
                    ),
                    ProductEntity(
                        id = 4,
                        code = "55M",
                        name = "55M Elite Fat-Booster Mash",
                        category = "Bypass Protein Ration",
                        description = "Concentrated energy & SNF/fat booster ration for commercial dairy farms.",
                        defaultBagWeightKg = 40.0,
                        purchasePrice = 3550.0,
                        dealerPrice = 3950.0,
                        salePrice = 3950.0,
                        retailPrice = 4150.0,
                        specialPrice = 3890.0,
                        minStockBags = 0,
                        openingStockBags = 0
                    ),
                    ProductEntity(
                        id = 5,
                        code = "CP Mamni",
                        name = "CP Mamni Calf & Heifer Starter",
                        category = "Calf & Heifer Starter",
                        description = "22% digestible protein starter for rapid rumen papillae development & early weaning.",
                        defaultBagWeightKg = 20.0,
                        purchasePrice = 1850.0,
                        dealerPrice = 2100.0,
                        salePrice = 2100.0,
                        retailPrice = 2250.0,
                        specialPrice = 2050.0,
                        minStockBags = 0,
                        openingStockBags = 0
                    )
                )
            )
        }

        if (dao.getAllCustomFieldDefsOnce().isEmpty()) {
            dao.insertCustomFieldDefs(
                listOf(
                    CustomFieldDefEntity(
                        entityType = "DEALER",
                        fieldName = "Monthly Potential Bags",
                        fieldType = "Number",
                        isRequired = false,
                        isVisible = true
                    ),
                    CustomFieldDefEntity(
                        entityType = "DEALER",
                        fieldName = "Godown Capacity (Bags)",
                        fieldType = "Number",
                        isRequired = false,
                        isVisible = true
                    ),
                    CustomFieldDefEntity(
                        entityType = "FARMER",
                        fieldName = "Breed",
                        fieldType = "Dropdown",
                        dropdownOptions = "Holstein Friesian,Jersey Cross,Sahiwal,Nili-Ravi Buffalo,Mixed Herd",
                        isRequired = false,
                        isVisible = true
                    ),
                    CustomFieldDefEntity(
                        entityType = "TRIAL",
                        fieldName = "Special Observation",
                        fieldType = "Long Text",
                        isRequired = false,
                        isVisible = true
                    )
                )
            )
        }
    }

    suspend fun seedSampleRecords(dao: DairyDao) {
        val today = todayDate()
        val yesterday = daysOffset(-1)
        val threeDaysAgo = daysOffset(-3)
        val fiveDaysAgo = daysOffset(-5)
        val sevenDaysAgo = daysOffset(-7)
        val twoDaysAhead = daysOffset(2)
        val tomorrow = daysOffset(1)
        val monthPrefix = currentMonthPrefix()

        // Seed Dealers
        val dealers = listOf(
            DealerEntity(
                id = 1,
                code = "DLR-001",
                name = "Chaudhry Tariq Mehmood",
                businessName = "Al-Madina Dairy Feeds & Grains",
                mobile = "+923004128901",
                whatsapp = "+923004128901",
                address = "Main Grain Market, Shop #14",
                village = "Chak 89/6-R",
                city = "Sahiwal",
                area = "Sahiwal North",
                dealerType = "Wholesale Distributor",
                openingBalance = 45000.0,
                creditLimit = 800000.0,
                assignedSalesPerson = "Regional Sales Manager",
                dateAdded = sevenDaysAgo,
                status = "Active",
                notes = "Top volume distributor in Sahiwal North; orders every Tuesday and Saturday.",
                customFieldsJson = """{"Monthly Potential Bags":"600","Godown Capacity (Bags)":"800"}"""
            ),
            DealerEntity(
                id = 2,
                code = "DLR-002",
                name = "Haji Ghulam Mustafa",
                businessName = "Mustafa Zarai & Wanda Agency",
                mobile = "+923217654320",
                whatsapp = "+923217654320",
                address = "Depalpur Chowk Bypass Road",
                village = "Renala Khurd",
                city = "Okara",
                area = "Okara Central",
                dealerType = "Wholesale Distributor",
                openingBalance = 120000.0,
                creditLimit = 600000.0,
                assignedSalesPerson = "Regional Sales Manager",
                dateAdded = sevenDaysAgo,
                status = "Active",
                notes = "Strong commercial farm customer base for 55M and 28P.",
                customFieldsJson = """{"Monthly Potential Bags":"450","Godown Capacity (Bags)":"500"}"""
            ),
            DealerEntity(
                id = 3,
                code = "DLR-003",
                name = "Rana Zahid Iqbal",
                businessName = "Chenab Livestock Nutrition",
                mobile = "+923338901234",
                whatsapp = "+923338901234",
                address = "Kacha Pakka Stop, Kasur Road",
                village = "Kot Radha Kishan",
                city = "Kasur",
                area = "Kasur East",
                dealerType = "Sub-Dealer",
                openingBalance = 0.0,
                creditLimit = 350000.0,
                assignedSalesPerson = "Regional Sales Manager",
                dateAdded = fiveDaysAgo,
                status = "Active",
                notes = "Growing sub-dealer transitioning 12 progressive farms to 26P.",
                customFieldsJson = """{"Monthly Potential Bags":"250","Godown Capacity (Bags)":"300"}"""
            ),
            DealerEntity(
                id = 4,
                code = "DLR-004",
                name = "Malik Asif Nawaz",
                businessName = "Sutlej Dairy Traders",
                mobile = "+923456112233",
                whatsapp = "+923456112233",
                address = "Arifwala Road Adda",
                village = "Chak 34/SP",
                city = "Pakpattan",
                area = "Pakpattan South",
                dealerType = "Potential",
                openingBalance = 0.0,
                creditLimit = 300000.0,
                assignedSalesPerson = "Regional Sales Manager",
                dateAdded = threeDaysAgo,
                status = "Potential",
                notes = "Interested in exclusive agency for CP Mamni and 26M.",
                customFieldsJson = """{"Monthly Potential Bags":"300","Godown Capacity (Bags)":"400"}"""
            )
        )
        dao.insertDealers(dealers)

        // Seed Farmers
        val farmers = listOf(
            FarmerEntity(
                id = 1,
                code = "FRM-001",
                name = "Mian Kashif Nadeem",
                mobile = "+923015557890",
                whatsapp = "+923015557890",
                farmName = "Green Acres Holstein Dairy",
                address = "2-KM Harrapa Link Road",
                village = "Chak 92/6-R",
                city = "Sahiwal",
                area = "Sahiwal North",
                animalCount = 85,
                currentFeed = "28P",
                monthlyConsumptionBags = 140,
                dealerId = 1,
                dealerName = "Al-Madina Dairy Feeds & Grains",
                dateAdded = sevenDaysAgo,
                status = "Active",
                notes = "Automated parlor farm; running 55M trial on high-yield group.",
                customFieldsJson = """{"Breed":"Holstein Friesian"}"""
            ),
            FarmerEntity(
                id = 2,
                code = "FRM-002",
                name = "Sardar Sikandar Hayat",
                mobile = "+923029988776",
                whatsapp = "+923029988776",
                farmName = "Hayat Model Buffalo Farm",
                address = "Canal Bank Road",
                village = "32/2-L",
                city = "Okara",
                area = "Okara Central",
                animalCount = 60,
                currentFeed = "26M",
                monthlyConsumptionBags = 95,
                dealerId = 2,
                dealerName = "Mustafa Zarai & Wanda Agency",
                dateAdded = fiveDaysAgo,
                status = "Active",
                notes = "Nili-Ravi herd supplying chiller milk; focused on fat % improvement.",
                customFieldsJson = """{"Breed":"Nili-Ravi Buffalo"}"""
            ),
            FarmerEntity(
                id = 3,
                code = "FRM-003",
                name = "Chaudhry Bilal Virk",
                mobile = "+923341122445",
                whatsapp = "+923341122445",
                farmName = "Virk Progressive Dairy",
                address = "Pattoki-Kasur Border Road",
                village = "Halla",
                city = "Kasur",
                area = "Kasur East",
                animalCount = 42,
                currentFeed = "Competitor A Pellet",
                monthlyConsumptionBags = 65,
                dealerId = 3,
                dealerName = "Chenab Livestock Nutrition",
                dateAdded = threeDaysAgo,
                status = "Potential",
                notes = "Started 14-day feed trial comparing 26P vs Competitor A.",
                customFieldsJson = """{"Breed":"Jersey Cross"}"""
            )
        )
        dao.insertFarmers(farmers)

        // Seed Stock-In Records
        dao.insertStockMovements(
            listOf(
                StockMovementEntity(
                    date = sevenDaysAgo,
                    type = "STOCK_IN",
                    productId = 1,
                    productCode = "26P",
                    productName = "26P High-Yield Dairy Pellets",
                    bagSizeLabel = "40 KG",
                    bagWeightKg = 40.0,
                    quantityBags = 200,
                    totalKg = 8000.0,
                    totalMt = 8.0,
                    purchasePricePerBag = 3150.0,
                    supplierName = "Central Feed Mill Plant #1",
                    invoiceNumber = "MILL-IN-8821",
                    remarks = "Weekly plant dispatch batch #A14"
                ),
                StockMovementEntity(
                    date = fiveDaysAgo,
                    type = "STOCK_IN",
                    productId = 4,
                    productCode = "55M",
                    productName = "55M Elite Fat-Booster Mash",
                    bagSizeLabel = "40 KG",
                    bagWeightKg = 40.0,
                    quantityBags = 150,
                    totalKg = 6000.0,
                    totalMt = 6.0,
                    purchasePricePerBag = 3550.0,
                    supplierName = "Central Feed Mill Plant #1",
                    invoiceNumber = "MILL-IN-8840",
                    remarks = "Fresh batch for Okara & Sahiwal demand"
                )
            )
        )

        // Seed Orders (Using saveOrderTransaction for atomic stock-out & ledger sync!)
        // Order 1: Today - Delivered multi-product order for Al-Madina
        val order1Items = listOf(
            OrderItemEntity(
                orderId = 0,
                productId = 1,
                productCode = "26P",
                productName = "26P High-Yield Dairy Pellets",
                bagSizeLabel = "40 KG",
                bagWeightKg = 40.0,
                quantityBags = 60,
                totalKg = 2400.0,
                totalMt = 2.4,
                purchasePriceSnapshot = 3150.0,
                pricePerBag = 3500.0,
                discountPerBag = 20.0,
                grossAmount = 210000.0,
                discountTotal = 1200.0,
                netAmount = 208800.0
            ),
            OrderItemEntity(
                orderId = 0,
                productId = 4,
                productCode = "55M",
                productName = "55M Elite Fat-Booster Mash",
                bagSizeLabel = "40 KG",
                bagWeightKg = 40.0,
                quantityBags = 40,
                totalKg = 1600.0,
                totalMt = 1.6,
                purchasePriceSnapshot = 3550.0,
                pricePerBag = 3950.0,
                discountPerBag = 0.0,
                grossAmount = 158000.0,
                discountTotal = 0.0,
                netAmount = 158000.0
            )
        )
        dao.saveOrderTransaction(
            order = SaleOrderEntity(
                orderNumber = "ORD-2026-101",
                invoiceNumber = "INV-2026-101",
                date = today,
                dealerId = 1,
                dealerName = "Al-Madina Dairy Feeds & Grains",
                dealerAddress = "Main Grain Market, Shop #14, Sahiwal",
                farmerId = 1,
                farmerName = "Mian Kashif Nadeem",
                location = "Sahiwal Grain Market",
                area = "Sahiwal North",
                totalBags = 100,
                totalKg = 4000.0,
                totalMt = 4.0,
                grossAmount = 368000.0,
                discountAmount = 1200.0,
                netAmount = 366800.0,
                paidAmount = 220000.0,
                outstandingAmount = 146800.0,
                estimatedProfit = 35800.0,
                status = "Delivered",
                expectedDeliveryDate = today,
                remarks = "Dispatched via Mazda LHR-4412, unloaded at Sahiwal godown.",
                isSampleData = true
            ),
            items = order1Items,
            paymentMethodForInitialPaid = "Bank Transfer",
            createPaymentRecord = true
        )

        // Order 2: Today - Pending delivery order for Mustafa Zarai
        val order2Items = listOf(
            OrderItemEntity(
                orderId = 0,
                productId = 2,
                productCode = "28P",
                productName = "28P Super Peak Lactation Pellets",
                bagSizeLabel = "40 KG",
                bagWeightKg = 40.0,
                quantityBags = 50,
                totalKg = 2000.0,
                totalMt = 2.0,
                purchasePriceSnapshot = 3420.0,
                pricePerBag = 3800.0,
                discountPerBag = 0.0,
                grossAmount = 190000.0,
                discountTotal = 0.0,
                netAmount = 190000.0
            ),
            OrderItemEntity(
                orderId = 0,
                productId = 3,
                productCode = "26M",
                productName = "26M Dairy Gold Coarse Mash",
                bagSizeLabel = "40 KG",
                bagWeightKg = 40.0,
                quantityBags = 30,
                totalKg = 1200.0,
                totalMt = 1.2,
                purchasePriceSnapshot = 3080.0,
                pricePerBag = 3420.0,
                discountPerBag = 20.0,
                grossAmount = 102600.0,
                discountTotal = 600.0,
                netAmount = 102000.0
            )
        )
        dao.saveOrderTransaction(
            order = SaleOrderEntity(
                orderNumber = "ORD-2026-102",
                invoiceNumber = "INV-2026-102",
                date = today,
                dealerId = 2,
                dealerName = "Mustafa Zarai & Wanda Agency",
                dealerAddress = "Depalpur Chowk Bypass Road, Okara",
                farmerId = 2,
                farmerName = "Sardar Sikandar Hayat",
                location = "Renala Khurd, Okara",
                area = "Okara Central",
                totalBags = 80,
                totalKg = 3200.0,
                totalMt = 3.2,
                grossAmount = 292600.0,
                discountAmount = 600.0,
                netAmount = 292000.0,
                paidAmount = 100000.0,
                outstandingAmount = 192000.0,
                estimatedProfit = 28600.0,
                status = "Pending",
                expectedDeliveryDate = tomorrow,
                remarks = "Priority morning loading required for Renala route.",
                isSampleData = true
            ),
            items = order2Items,
            paymentMethodForInitialPaid = "Online",
            createPaymentRecord = true
        )

        // Order 3: 3 Days Ago - Delivered order for Chenab Livestock
        val order3Items = listOf(
            OrderItemEntity(
                orderId = 0,
                productId = 1,
                productCode = "26P",
                productName = "26P High-Yield Dairy Pellets",
                bagSizeLabel = "40 KG",
                bagWeightKg = 40.0,
                quantityBags = 45,
                totalKg = 1800.0,
                totalMt = 1.8,
                purchasePriceSnapshot = 3150.0,
                pricePerBag = 3500.0,
                discountPerBag = 0.0,
                grossAmount = 157500.0,
                discountTotal = 0.0,
                netAmount = 157500.0
            ),
            OrderItemEntity(
                orderId = 0,
                productId = 5,
                productCode = "CP Mamni",
                productName = "CP Mamni Calf & Heifer Starter",
                bagSizeLabel = "20 KG",
                bagWeightKg = 20.0,
                quantityBags = 20,
                totalKg = 400.0,
                totalMt = 0.4,
                purchasePriceSnapshot = 1850.0,
                pricePerBag = 2100.0,
                discountPerBag = 0.0,
                grossAmount = 42000.0,
                discountTotal = 0.0,
                netAmount = 42000.0
            )
        )
        dao.saveOrderTransaction(
            order = SaleOrderEntity(
                orderNumber = "ORD-2026-100",
                invoiceNumber = "INV-2026-100",
                date = threeDaysAgo,
                dealerId = 3,
                dealerName = "Chenab Livestock Nutrition",
                dealerAddress = "Kacha Pakka Stop, Kasur Road, Kasur",
                farmerId = 3,
                farmerName = "Chaudhry Bilal Virk",
                location = "Kot Radha Kishan, Kasur",
                area = "Kasur East",
                totalBags = 65,
                totalKg = 2200.0,
                totalMt = 2.2,
                grossAmount = 199500.0,
                discountAmount = 0.0,
                netAmount = 199500.0,
                paidAmount = 150000.0,
                outstandingAmount = 49500.0,
                estimatedProfit = 20750.0,
                status = "Delivered",
                expectedDeliveryDate = threeDaysAgo,
                remarks = "Delivered on time; 20 bags CP Mamni for calf rearing program.",
                isSampleData = true
            ),
            items = order3Items,
            paymentMethodForInitialPaid = "Cash",
            createPaymentRecord = true
        )

        // Additional standalone payment collection today
        dao.insertPayment(
            PaymentEntity(
                paymentNumber = "PAY-2026-501",
                date = today,
                dealerId = 2,
                dealerName = "Mustafa Zarai & Wanda Agency",
                amount = 65000.0,
                paymentMethod = "Cash",
                referenceNumber = "RCPT-9042",
                bankAccount = "Field Cash Recovery",
                remarks = "Partial clearance of opening balance during routine visit"
            )
        )

        // Seed Feed Trials + Daily Milk Production Records
        val trial1Id = dao.insertFeedTrial(
            FeedTrialEntity(
                trialCode = "TRL-2026-001",
                farmerId = 1,
                farmerName = "Mian Kashif Nadeem",
                dealerId = 1,
                dealerName = "Al-Madina Dairy Feeds & Grains",
                farmName = "Green Acres Holstein Dairy",
                location = "Chak 92/6-R, Sahiwal",
                animalCount = 3,
                currentFeed = "26P High-Yield Dairy Pellets",
                trialFeed = "28P Super Peak Lactation Pellets",
                currentFeedCode = "26P",
                trialFeedCode = "28P",
                startDate = sevenDaysAgo,
                transitionDays = 5,
                endDate = twoDaysAhead,
                status = "Under Observation",
                remarks = "Testing 28P on 3 early-lactation Holsteins to measure peak yield boost.",
                customFieldsJson = """{"Special Observation":"Zero acidosis signs; glossy coat and higher cud chewing."}"""
            )
        )

        dao.insertMilkRecords(
            listOf(
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-101",
                    recordDate = sevenDaysAgo,
                    startingMilkLiters = 22.0,
                    dailyMilkLiters = 22.0,
                    feedQuantityKg = 8.0,
                    remarks = "Day 1 baseline"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-102",
                    recordDate = sevenDaysAgo,
                    startingMilkLiters = 20.5,
                    dailyMilkLiters = 20.5,
                    feedQuantityKg = 8.0,
                    remarks = "Day 1 baseline"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-103",
                    recordDate = sevenDaysAgo,
                    startingMilkLiters = 23.0,
                    dailyMilkLiters = 23.0,
                    feedQuantityKg = 8.5,
                    remarks = "Day 1 baseline"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-101",
                    recordDate = fiveDaysAgo,
                    startingMilkLiters = 22.0,
                    dailyMilkLiters = 23.2,
                    feedQuantityKg = 8.5,
                    remarks = "50% transition complete"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-102",
                    recordDate = fiveDaysAgo,
                    startingMilkLiters = 20.5,
                    dailyMilkLiters = 21.6,
                    feedQuantityKg = 8.5,
                    remarks = "Good intake"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-103",
                    recordDate = fiveDaysAgo,
                    startingMilkLiters = 23.0,
                    dailyMilkLiters = 24.1,
                    feedQuantityKg = 9.0,
                    remarks = "Strong response"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-101",
                    recordDate = threeDaysAgo,
                    startingMilkLiters = 22.0,
                    dailyMilkLiters = 24.4,
                    feedQuantityKg = 9.0,
                    remarks = "100% on 28P"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-102",
                    recordDate = threeDaysAgo,
                    startingMilkLiters = 20.5,
                    dailyMilkLiters = 22.8,
                    feedQuantityKg = 8.5,
                    remarks = "Steady increase"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-103",
                    recordDate = threeDaysAgo,
                    startingMilkLiters = 23.0,
                    dailyMilkLiters = 25.3,
                    feedQuantityKg = 9.0,
                    remarks = "+2.3L over baseline"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-101",
                    recordDate = today,
                    startingMilkLiters = 22.0,
                    dailyMilkLiters = 25.6,
                    feedQuantityKg = 9.0,
                    remarks = "Morning + Evening verified"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-102",
                    recordDate = today,
                    startingMilkLiters = 20.5,
                    dailyMilkLiters = 23.5,
                    feedQuantityKg = 8.5,
                    remarks = "+3.0L gain"
                ),
                MilkRecordEntity(
                    trialId = trial1Id,
                    animalIdTag = "HF-103",
                    recordDate = today,
                    startingMilkLiters = 23.0,
                    dailyMilkLiters = 26.2,
                    feedQuantityKg = 9.0,
                    remarks = "Peak performance"
                )
            )
        )

        val trial2Id = dao.insertFeedTrial(
            FeedTrialEntity(
                trialCode = "TRL-2026-002",
                farmerId = 2,
                farmerName = "Sardar Sikandar Hayat",
                dealerId = 2,
                dealerName = "Mustafa Zarai & Wanda Agency",
                farmName = "Hayat Model Buffalo Farm",
                location = "32/2-L, Okara",
                animalCount = 2,
                currentFeed = "Traditional Cottonseed Cake",
                trialFeed = "55M Elite Fat-Booster Mash",
                currentFeedCode = "CSC",
                trialFeedCode = "55M",
                startDate = fiveDaysAgo,
                transitionDays = 7,
                endDate = twoDaysAhead,
                status = "Transition",
                remarks = "Buffalo fat % and volume comparison against traditional khal.",
                customFieldsJson = """{"Special Observation":"Fat test rose from 6.1% to 6.8% within 5 days."}"""
            )
        )

        dao.insertMilkRecords(
            listOf(
                MilkRecordEntity(
                    trialId = trial2Id,
                    animalIdTag = "BUF-21",
                    recordDate = fiveDaysAgo,
                    startingMilkLiters = 11.5,
                    dailyMilkLiters = 11.5,
                    feedQuantityKg = 6.0,
                    remarks = "Baseline on Khal"
                ),
                MilkRecordEntity(
                    trialId = trial2Id,
                    animalIdTag = "BUF-22",
                    recordDate = fiveDaysAgo,
                    startingMilkLiters = 12.0,
                    dailyMilkLiters = 12.0,
                    feedQuantityKg = 6.0,
                    remarks = "Baseline on Khal"
                ),
                MilkRecordEntity(
                    trialId = trial2Id,
                    animalIdTag = "BUF-21",
                    recordDate = today,
                    startingMilkLiters = 11.5,
                    dailyMilkLiters = 13.4,
                    feedQuantityKg = 6.0,
                    remarks = "+1.9L with higher cream"
                ),
                MilkRecordEntity(
                    trialId = trial2Id,
                    animalIdTag = "BUF-22",
                    recordDate = today,
                    startingMilkLiters = 12.0,
                    dailyMilkLiters = 13.9,
                    feedQuantityKg = 6.0,
                    remarks = "+1.9L increase"
                )
            )
        )

        // Seed Complaints
        dao.insertComplaints(
            listOf(
                ComplaintEntity(
                    complaintCode = "CMP-2026-001",
                    date = threeDaysAgo,
                    partnerType = "Dealer",
                    partnerId = 2,
                    partnerName = "Mustafa Zarai & Wanda Agency",
                    productId = 3,
                    productName = "26M Dairy Gold Coarse Mash",
                    complaintType = "Bag Stitching Issue",
                    description = "4 bags in bottom layer had loose top thread stitching during unloading.",
                    milkChange = "No milk impact",
                    animalDetails = "N/A - Warehouse level",
                    investigation = "Checked batch code B-1102; sewing machine tension at Bagging Line #2 was adjusted.",
                    actionTaken = "Replaced 4 outer PP bags on the spot and informed Plant QA manager.",
                    followUpDate = tomorrow,
                    status = "Under Investigation",
                    resolution = "QA credit note approved for 4 replacement bags.",
                    remarks = "Verify stitching on tomorrow's 80-bag dispatch."
                )
            )
        )

        // Seed Monthly Target
        dao.insertTarget(
            SalesTargetEntity(
                periodLabel = monthPrefix,
                periodType = "Monthly",
                targetBags = 1000,
                targetMt = 40.0,
                targetSalesValue = 3600000.0,
                targetCollection = 3000000.0,
                notes = "November Regional Target across Sahiwal, Okara, Kasur & Pakpattan"
            )
        )

        // Seed Expenses
        dao.insertExpenses(
            listOf(
                ExpenseEntity(
                    date = today,
                    category = "Fuel",
                    amount = 4800.0,
                    paymentMethod = "Cash",
                    location = "PSO Filling Station Sahiwal Bypass",
                    description = "Field vehicle petrol for Sahiwal & Okara dealer route",
                    receiptReference = "PSO-7741"
                ),
                ExpenseEntity(
                    date = yesterday,
                    category = "Food",
                    amount = 1650.0,
                    paymentMethod = "Cash",
                    location = "Pakpattan",
                    description = "Refreshments during dealer onboarding meeting with Malik Asif",
                    receiptReference = "EXP-7720"
                ),
                ExpenseEntity(
                    date = threeDaysAgo,
                    category = "Transport",
                    amount = 1200.0,
                    paymentMethod = "Cash",
                    location = "Motorway / GT Road Toll Plazas",
                    description = "Toll taxes and parking during Kasur farm visits",
                    receiptReference = "TOLL-119"
                )
            )
        )

        // Seed Travel / Odometer Logs
        dao.insertTravelLogs(
            listOf(
                TravelLogEntity(
                    date = today,
                    startingOdometer = 48210.0,
                    endingOdometer = 48345.0,
                    startingLocation = "Sahiwal Regional Office",
                    destination = "Chak 89/6-R -> Green Acres Farm -> Renala Khurd",
                    totalKm = 135.0,
                    workingKm = 125.0,
                    personalKm = 10.0,
                    purpose = "Dealer collections, order booking & Trial TRL-2026-001 milk check",
                    remarks = "Smooth route, 2 orders booked."
                ),
                TravelLogEntity(
                    date = yesterday,
                    startingOdometer = 48095.0,
                    endingOdometer = 48210.0,
                    startingLocation = "Sahiwal Regional Office",
                    destination = "Arifwala Road -> Pakpattan South",
                    totalKm = 115.0,
                    workingKm = 115.0,
                    personalKm = 0.0,
                    purpose = "Prospecting new dealers in Pakpattan South",
                    remarks = "Met Sutlej Dairy Traders."
                )
            )
        )
    }

    suspend fun clearAllTransactionalAndSampleData(dao: DairyDao, keepConfigurableSettings: Boolean = true) {
        dao.clearAllOrderItems()
        dao.clearAllOrders()
        dao.clearAllStockMovements()
        dao.clearAllPayments()
        dao.clearAllMilkRecords()
        dao.clearAllTrials()
        dao.clearAllComplaints()
        dao.clearAllExpenses()
        dao.clearAllTravelLogs()
        dao.clearAllDealers()
        dao.clearAllFarmers()
        dao.clearAllTargets()

        val config = dao.getBusinessConfigOnce() ?: BusinessConfigEntity()
        dao.saveBusinessConfig(config.copy(isSampleDataLoaded = false))
        dao.insertAuditLog(
            AuditLogEntity(
                actorRole = config.activeRole,
                actionType = "DELETE",
                module = "System",
                recordCode = "RESET",
                summary = "Cleared sample/transactional records to start fresh"
            )
        )
    }
}
