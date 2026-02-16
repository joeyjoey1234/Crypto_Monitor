package com.fdroid.cryptomonitor.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.fdroid.cryptomonitor.update.GitHubReleaseApi
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiFactory {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", "fdroid-crypto-monitor")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(defaultClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val coinGeckoApi: CoinGeckoApi = retrofit("https://api.coingecko.com/")
        .create(CoinGeckoApi::class.java)

    val blockchairApi: BlockchairApi = retrofit("https://api.blockchair.com/")
        .create(BlockchairApi::class.java)

    val gitHubReleaseApi: GitHubReleaseApi = retrofit("https://api.github.com/")
        .create(GitHubReleaseApi::class.java)
}
