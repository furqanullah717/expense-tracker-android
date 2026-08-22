package com.codewithfk.expensetracker.android.utils

import com.codewithfk.expensetracker.android.R
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object Utils {

    fun formatDateToHumanReadableForm(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd/MM/YYYY", Locale.getDefault())
        return dateFormatter.format(dateInMillis)
    }

    fun formatDateForChart(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd-MMM", Locale.getDefault())
        return dateFormatter.format(dateInMillis)
    }

    fun formatCurrency(amount: Double, locale: Locale = Locale.KOREA): String {
        val currencyFormatter = NumberFormat.getCurrencyInstance(locale)
        return currencyFormatter.format(amount)
    }

    fun formatDayMonthYear(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormatter.format(dateInMillis)
    }

    fun formatDayMonth(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd/MMM", Locale.getDefault())
        return dateFormatter.format(dateInMillis)
    }

    fun formatToDecimalValue(d: Double): String {
        return String.format("%.2f", d)
    }

    fun formatStringDateToMonthDayYear(date: String): String {
        val millis = getMillisFromDate(date)
        return formatDayMonthYear(millis)
    }

    fun getMillisFromDate(date: String): Long {
        return getMilliFromDate(date)
    }

    fun getMilliFromDate(dateFormat: String?): Long {
        var date = Date()
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        try {
            date = formatter.parse(dateFormat)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        println("Today is $date")
        return date.time
    }

    fun getItemIcon(item: ExpenseEntity): Int {
        return when {
            item.title.contains("월급") || item.title.contains("보너스") || item.title.contains("수입") || item.title.contains("성과급") -> R.drawable.ic_paypal
            item.title.contains("통신") || item.title.contains("인강") || item.title.contains("넷플릭스") || item.title.contains("구독") -> R.drawable.ic_netflix
            item.title.contains("카페") || item.title.contains("식비") || item.title.contains("외식") || item.title.contains("장보기") || item.title.contains("마트") -> R.drawable.ic_starbucks
            else -> R.drawable.ic_upwork
        }
    }

    fun formatDateToKorean(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("yy년 M월 d일", Locale.KOREA)
        return dateFormatter.format(dateInMillis)
    }

    fun formatHistoryTitle(startDateMillis: Long, endDateMillis: Long): String {
        val startStr = formatDateToKorean(startDateMillis)
        val endStr = formatDateToKorean(endDateMillis)
        return "$startStr ~ $endStr 분석 기록"
    }

    fun formatDurationMs(durationMs: Long): String {
        val seconds = durationMs / 1000.0
        return if (seconds < 60) {
            String.format(Locale.getDefault(), "%.1f초", seconds)
        } else {
            val mins = (seconds / 60).toInt()
            val remSecs = (seconds % 60).toInt()
            "${mins}분 ${remSecs}초"
        }
    }

    fun formatTimestampToDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        return formatter.format(Date(timestamp))
    }

    fun getNetworkType(context: android.content.Context): String {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return "Unknown"
        val activeNetwork = cm.activeNetwork ?: return "Offline"
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Offline"
        return when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (5G/LTE)"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    fun getDeviceModel(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    fun getOsVersion(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

    fun calculateCostUsd(promptTokens: Int, candidatesTokens: Int): Double {
        // gemini-2.5-flash standard pricing:
        // Input: $0.075 / 1M tokens ($0.000000075 / token)
        // Output: $0.30 / 1M tokens ($0.00000030 / token)
        return (promptTokens * 0.000000075) + (candidatesTokens * 0.00000030)
    }

    fun calculateCostKrw(costUsd: Double, exchangeRate: Double = 1350.0): Double {
        return costUsd * exchangeRate
    }

    fun formatCost(costKrw: Double): String {
        return if (costKrw < 0.01) {
            String.format(Locale.getDefault(), "₩%.4f", costKrw)
        } else {
            String.format(Locale.getDefault(), "₩%.2f", costKrw)
        }
    }

}