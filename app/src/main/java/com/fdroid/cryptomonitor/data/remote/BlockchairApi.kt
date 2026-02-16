package com.fdroid.cryptomonitor.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface BlockchairApi {

    @GET("{chain}/dashboards/address/{address}")
    suspend fun getAddress(
        @Path("chain") chain: String,
        @Path("address") address: String
    ): BlockchairResponse
}

data class BlockchairResponse(
    val data: Map<String, BlockchairAddressContainer>
)

data class BlockchairAddressContainer(
    val address: BlockchairAddressInfo
)

data class BlockchairAddressInfo(
    val balance: Double?
)
