package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen Width Class categories based on 25+ real Android phone dimensions:
 * - COMPACT: < 360dp (Galaxy A01, A10e, budget devices or phones with Display Size = Largest)
 * - STANDARD: 360dp - 400dp (Galaxy A12/A14/A53/S21/S22/S23, Redmi Note 11/12, Vivo, Oppo - ~70% of phones)
 * - EXPANDED_PHONE: 401dp - 480dp (Galaxy S23/S24 Ultra, Pixel 7/8 Pro, OnePlus 11/12, flagship phablets)
 * - TABLET_OR_FOLDABLE: > 480dp (Galaxy Z Fold inner display, Android Tablets)
 */
enum class ScreenBucket {
    COMPACT,
    STANDARD,
    EXPANDED_PHONE,
    TABLET_OR_FOLDABLE
}

data class ResponsiveDimens(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val bucket: ScreenBucket,
    
    // Page Layout Paddings
    val pageHorizontalPadding: Dp,
    val sectionSpacing: Dp,
    
    // KPI Cards
    val kpiCardSpacing: Dp,
    val kpiCardPadding: Dp,
    val kpiTitleFontSize: TextUnit,
    val kpiTitleLineHeight: TextUnit,
    val kpiPrimaryValueFontSize: TextUnit,
    val kpiSecondaryValueFontSize: TextUnit,
    val kpiBadgeFontSize: TextUnit,
    
    // Monthly Target Hero Card
    val targetCardPadding: Dp,
    val targetIconSize: Dp,
    val targetBagsFontSize: TextUnit,
    val targetRemainingFontSize: TextUnit,
    val targetHeroImgWidth: Dp,
    val targetHeroImgHeight: Dp,
    
    // Quick Actions 6-Grid
    val quickActionHeight: Dp,
    val quickActionIconSize: Dp,
    val quickActionFontSize: TextUnit,
    val quickActionGridSpacing: Dp,
    
    // Bottom Navigation Bar
    val bottomNavHeight: Dp,
    val bottomNavIconSize: Dp,
    val bottomNavFontSize: TextUnit,
    
    // General Dialogs and Tables
    val tableCellPadding: Dp,
    val tableHeaderFontSize: TextUnit,
    val tableInputHeight: Dp
)

@Composable
fun rememberResponsiveDimens(): ResponsiveDimens {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    return remember(width, height) {
        val bucket = when {
            width < 360 -> ScreenBucket.COMPACT
            width <= 400 -> ScreenBucket.STANDARD
            width <= 480 -> ScreenBucket.EXPANDED_PHONE
            else -> ScreenBucket.TABLET_OR_FOLDABLE
        }

        when (bucket) {
            ScreenBucket.COMPACT -> ResponsiveDimens(
                screenWidthDp = width,
                screenHeightDp = height,
                bucket = bucket,
                pageHorizontalPadding = 10.dp,
                sectionSpacing = 10.dp,
                kpiCardSpacing = 6.dp,
                kpiCardPadding = 8.dp,
                kpiTitleFontSize = 9.5.sp,
                kpiTitleLineHeight = 12.sp,
                kpiPrimaryValueFontSize = 12.sp,
                kpiSecondaryValueFontSize = 9.5.sp,
                kpiBadgeFontSize = 8.5.sp,
                targetCardPadding = 11.dp,
                targetIconSize = 36.dp,
                targetBagsFontSize = 15.sp,
                targetRemainingFontSize = 10.sp,
                targetHeroImgWidth = 100.dp,
                targetHeroImgHeight = 95.dp,
                quickActionHeight = 68.dp,
                quickActionIconSize = 22.dp,
                quickActionFontSize = 10.sp,
                quickActionGridSpacing = 6.dp,
                bottomNavHeight = 56.dp,
                bottomNavIconSize = 20.dp,
                bottomNavFontSize = 8.5.sp,
                tableCellPadding = 4.dp,
                tableHeaderFontSize = 8.5.sp,
                tableInputHeight = 36.dp
            )

            ScreenBucket.STANDARD -> ResponsiveDimens(
                screenWidthDp = width,
                screenHeightDp = height,
                bucket = bucket,
                pageHorizontalPadding = 14.dp,
                sectionSpacing = 12.dp,
                kpiCardSpacing = 8.dp,
                kpiCardPadding = 10.dp,
                kpiTitleFontSize = 10.5.sp,
                kpiTitleLineHeight = 13.sp,
                kpiPrimaryValueFontSize = 13.5.sp,
                kpiSecondaryValueFontSize = 10.sp,
                kpiBadgeFontSize = 9.5.sp,
                targetCardPadding = 14.dp,
                targetIconSize = 42.dp,
                targetBagsFontSize = 18.sp,
                targetRemainingFontSize = 11.5.sp,
                targetHeroImgWidth = 125.dp,
                targetHeroImgHeight = 115.dp,
                quickActionHeight = 74.dp,
                quickActionIconSize = 24.dp,
                quickActionFontSize = 11.sp,
                quickActionGridSpacing = 8.dp,
                bottomNavHeight = 60.dp,
                bottomNavIconSize = 22.dp,
                bottomNavFontSize = 9.5.sp,
                tableCellPadding = 6.dp,
                tableHeaderFontSize = 9.5.sp,
                tableInputHeight = 40.dp
            )

            ScreenBucket.EXPANDED_PHONE -> ResponsiveDimens(
                screenWidthDp = width,
                screenHeightDp = height,
                bucket = bucket,
                pageHorizontalPadding = 16.dp,
                sectionSpacing = 14.dp,
                kpiCardSpacing = 10.dp,
                kpiCardPadding = 12.dp,
                kpiTitleFontSize = 11.5.sp,
                kpiTitleLineHeight = 14.sp,
                kpiPrimaryValueFontSize = 15.sp,
                kpiSecondaryValueFontSize = 11.sp,
                kpiBadgeFontSize = 10.5.sp,
                targetCardPadding = 16.dp,
                targetIconSize = 46.dp,
                targetBagsFontSize = 20.sp,
                targetRemainingFontSize = 12.5.sp,
                targetHeroImgWidth = 140.dp,
                targetHeroImgHeight = 125.dp,
                quickActionHeight = 82.dp,
                quickActionIconSize = 26.dp,
                quickActionFontSize = 11.5.sp,
                quickActionGridSpacing = 10.dp,
                bottomNavHeight = 64.dp,
                bottomNavIconSize = 24.dp,
                bottomNavFontSize = 10.5.sp,
                tableCellPadding = 8.dp,
                tableHeaderFontSize = 10.5.sp,
                tableInputHeight = 44.dp
            )

            ScreenBucket.TABLET_OR_FOLDABLE -> ResponsiveDimens(
                screenWidthDp = width,
                screenHeightDp = height,
                bucket = bucket,
                pageHorizontalPadding = 24.dp,
                sectionSpacing = 18.dp,
                kpiCardSpacing = 14.dp,
                kpiCardPadding = 16.dp,
                kpiTitleFontSize = 13.sp,
                kpiTitleLineHeight = 16.sp,
                kpiPrimaryValueFontSize = 17.sp,
                kpiSecondaryValueFontSize = 12.5.sp,
                kpiBadgeFontSize = 11.5.sp,
                targetCardPadding = 20.dp,
                targetIconSize = 52.dp,
                targetBagsFontSize = 24.sp,
                targetRemainingFontSize = 14.sp,
                targetHeroImgWidth = 170.dp,
                targetHeroImgHeight = 150.dp,
                quickActionHeight = 92.dp,
                quickActionIconSize = 30.dp,
                quickActionFontSize = 13.sp,
                quickActionGridSpacing = 14.dp,
                bottomNavHeight = 70.dp,
                bottomNavIconSize = 26.dp,
                bottomNavFontSize = 11.5.sp,
                tableCellPadding = 12.dp,
                tableHeaderFontSize = 12.sp,
                tableInputHeight = 48.dp
            )
        }
    }
}
