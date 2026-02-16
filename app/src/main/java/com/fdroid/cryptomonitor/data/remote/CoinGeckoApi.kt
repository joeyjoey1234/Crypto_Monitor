package com.fdroid.cryptomonitor.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinGeckoApi {

    @GET("api/v3/coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("ids") ids: String,
        @Query("price_change_percentage") changeWindow: String = "24h",
        @Query("sparkline") sparkline: Boolean = true
    ): List<MarketCoinDto>

    @GET("api/v3/coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int = 7,
        @Query("interval") interval: String = "hourly"
    ): MarketChartDto

    @GET("api/v3/coins/list")
    suspend fun getCoinList(
        @Query("include_platform") includePlatform: Boolean = true
    ): List<CoinListItemDto>
}

data class MarketCoinDto(
    val id: String,
    val symbol: String,
    val name: String,
    val current_price: Double,
    val price_change_percentage_24h: Double?,
    val sparkline_in_7d: SparklineDto?
)

data class SparklineDto(
    val price: List<Double>
)

data class CoinListItemDto(
    val id: String,
    val symbol: String,
    val name: String,
    val platforms: Map<String, String>?
)

data class MarketChartDto(
    val prices: List<List<Double>>
)
