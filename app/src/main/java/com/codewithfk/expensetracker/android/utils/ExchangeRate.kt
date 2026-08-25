package com.codewithfk.expensetracker.android.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * USD→KRW 환율 제공자.
 * open.er-api.com 의 공개 API(키 불필요)를 사용하며, 결과를 메모리에 캐싱한다.
 * 네트워크 실패 시 마지막으로 성공한 값(없으면 FALLBACK_USD_KRW)을 반환한다.
 */
object ExchangeRate {
    private const val API_URL = "https://open.er-api.com/v6/latest/USD"
    const val FALLBACK_USD_KRW = 1350.0
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedRate: Double = FALLBACK_USD_KRW

    @Volatile
    private var cachedAtMs: Long = 0L

    suspend fun getUsdKrw(): Double {
        if (System.currentTimeMillis() - cachedAtMs < TTL_MS) return cachedRate
        return withContext(Dispatchers.IO) { fetchAndCache() }
    }

    private fun fetchAndCache(): Double {
        return try {
            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            val response = json.decodeFromString<LatestRatesResponse>(body)
            val rate = response.rates[KRW_CODE]
            if (response.result == "success" && rate != null && rate > 0) {
                cachedRate = rate
                cachedAtMs = System.currentTimeMillis()
                rate
            } else {
                cachedRate
            }
        } catch (e: Exception) {
            cachedRate
        }
    }

    @Serializable
    private data class LatestRatesResponse(
        val result: String? = null,
        val rates: Map<String, Double> = emptyMap()
    )

    private const val KRW_CODE = "KRW"
}
