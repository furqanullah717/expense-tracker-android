package com.smartspend.ai.utils

import com.smartspend.ai.R
import com.smartspend.ai.ai.gateway.AiModelCatalog
import com.smartspend.ai.data.model.ExpenseEntity
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object Utils {

    fun formatDateToHumanReadableForm(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        return dateFormatter.format(dateInMillis)
    }

    fun formatDateForChart(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd-MMM", Locale.US)
        return dateFormatter.format(dateInMillis)
    }

    fun formatCurrency(amount: Double, locale: Locale = Locale.KOREA): String {
        val currencyFormatter = NumberFormat.getCurrencyInstance(locale)
        return currencyFormatter.format(amount)
    }

    fun formatDayMonthYear(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return dateFormatter.format(dateInMillis)
    }

    fun formatDayMonth(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("dd/MMM", Locale.US)
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
        if (dateFormat.isNullOrBlank()) return System.currentTimeMillis()
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        return try {
            formatter.parse(dateFormat.trim())?.time ?: System.currentTimeMillis()
        } catch (e: ParseException) {
            e.printStackTrace()
            System.currentTimeMillis()
        }
    }

    fun getItemIcon(item: ExpenseEntity): Int {
        return when {
            item.title.contains("?îÍ∏â") || item.title.contains("Î≥¥ÎÑà??) || item.title.contains("?òÏûÖ") || item.title.contains("?±Í≥ºÍ∏?) -> R.drawable.ic_paypal
            item.title.contains("?µÏã†") || item.title.contains("?∏Í∞ï") || item.title.contains("?∑ÌîåÎ¶?ä§") || item.title.contains("Íµ¨ÎèÖ") -> R.drawable.ic_netflix
            item.title.contains("Ïπ¥Ìéò") || item.title.contains("?ùÎπÑ") || item.title.contains("?∏Ïãù") || item.title.contains("?•Î≥¥Í∏?) || item.title.contains("ÎßàÌä∏") -> R.drawable.ic_starbucks
            else -> R.drawable.ic_upwork
        }
    }

    fun formatDateToKorean(dateInMillis: Long): String {
        val dateFormatter = SimpleDateFormat("yy??M??d??, Locale.KOREA)
        return dateFormatter.format(dateInMillis)
    }

    fun formatHistoryTitle(startDateMillis: Long, endDateMillis: Long): String {
        val startStr = formatDateToKorean(startDateMillis)
        val endStr = formatDateToKorean(endDateMillis)
        return "$startStr ~ $endStr Î∂ÑÏÑù Í∏∞Î°ù"
    }

    fun formatDurationMs(durationMs: Long): String {
        val seconds = durationMs / 1000.0
        return if (seconds < 60) {
            String.format(Locale.getDefault(), "%.1fÏ¥?, seconds)
        } else {
            val mins = (seconds / 60).toInt()
            val remSecs = (seconds % 60).toInt()
            "${mins}Î∂?${remSecs}Ï¥?
        }
    }

    fun formatTimestampToDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        return formatter.format(Date(timestamp))
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getNetworkType(context: android.content.Context): String {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return "Unknown"
        val activeNetwork = cm.activeNetwork ?: return "Offline"
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Offline"
        
        return when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                if (tm != null) {
                    try {
                        val networkType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            tm.dataNetworkType
                        } else {
                            @Suppress("DEPRECATION")
                            tm.networkType
                        }
                        
                        when (networkType) {
                            android.telephony.TelephonyManager.NETWORK_TYPE_GPRS,
                            android.telephony.TelephonyManager.NETWORK_TYPE_EDGE,
                            android.telephony.TelephonyManager.NETWORK_TYPE_CDMA,
                            android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT,
                            android.telephony.TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                            
                            android.telephony.TelephonyManager.NETWORK_TYPE_UMTS,
                            android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0,
                            android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A,
                            android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
                            android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
                            android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
                            android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B,
                            android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD,
                            android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                            
                            android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G/LTE"
                            
                            // NETWORK_TYPE_NR (5G) constant is 20
                            20 -> "5G"
                            
                            else -> "Cellular"
                        }
                    } catch (_: SecurityException) {
                        "Cellular"
                    }
                } else {
                    "Cellular"
                }
            }
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    fun getDeviceModel(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    fun getOsVersion(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"


    fun calculateCostUsd(
        modelName: String,
        promptTokens: Int,
        candidatesTokens: Int,
        thoughtsTokens: Int = 0
    ): Double {
        return AiModelCatalog.calculateCostUsd(
            modelName = modelName,
            promptTokens = promptTokens,
            candidatesTokens = candidatesTokens,
            thoughtsTokens = thoughtsTokens
        )
    }

    suspend fun calculateCostKrw(costUsd: Double): Double {
        return costUsd * ExchangeRate.getUsdKrw()
    }

    fun formatCost(costKrw: Double): String {
        return if (costKrw < 0.01) {
            String.format(Locale.getDefault(), "??.4f", costKrw)
        } else {
            String.format(Locale.getDefault(), "??.2f", costKrw)
        }
    }

    val incomeCategories = listOf("?îÍ∏â", "?ÑÎ¶¨?úÏÑú", "?¨Ïûê", "Î≥¥ÎÑà??, "?ÑÎ? ?òÏûÖ", "Í∏∞Ì? ?òÏûÖ")
    val expenseCategories = listOf(
        "?ùÎπÑ", "?∑ÌîåÎ¶?ä§", "?îÏÑ∏", "Ïπ¥Ìéò/?§Ì?Î≤ÖÏä§", "?ºÌïë", "ÍµêÌÜµÎπ?, "Í≥µÍ≥ºÍ∏?, 
        "?∏Ïãù", "Î¨∏Ìôî?ùÌôú", "?òÎ£å/Í±¥Í∞ï", "Î≥¥Ìóò", "?µÏã†/Íµ¨ÎèÖ", "ÍµêÏú°", 
        "?ÄÏ∂??ÅÌôò", "?†Î¨º/Í∏∞Î?", "?¨Ìñâ", "Í∏∞Ì? ÏßÄÏ∂?
    )

    /**
     * AI ?µÎ? Ï§?Mermaid ÏΩîÎìú Î∏îÎ°ù??Í∞êÏ??òÏó¨ mermaid.ink ?¥Î?ÏßÄ URLÎ°?Î≥Ä?òÌï©?àÎã§.
     */
    fun processMermaidDiagrams(content: String): String {
        val mermaidRegex = """```mermaid\s*([\s\S]*?)\s*```""".toRegex()
        return mermaidRegex.replace(content) { matchResult ->
            val diagramCode = matchResult.groupValues[1].trim()
            val encodedCode = try {
                // Mermaid.ink expects standard Base64 encoding (not URL safe)
                android.util.Base64.encodeToString(
                    diagramCode.toByteArray(Charsets.UTF_8),
                    android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP
                )
            } catch (e: Exception) {
                ""
            }
            if (encodedCode.isNotEmpty()) {
                // Using .svg endpoint for better compatibility with SvgDecoder
                "\n![Mermaid Diagram](https://mermaid.ink/svg/$encodedCode)\n"
            } else {
                matchResult.value
            }
        }
    }
}
