package com.leaf.app.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** FOSS build: every cosmetic is free, and About shows a plain donate link. No billing code. */
class AlwaysUnlockedSupporterRepository(donateUrl: String) : SupporterRepository {

    override val isSupporter: StateFlow<Boolean> = MutableStateFlow(true)

    override val standing: StateFlow<SupporterStanding> = MutableStateFlow(SupporterStanding(unlockOwned = true))

    override val products: StateFlow<List<SupporterProduct>> = MutableStateFlow(emptyList())

    override val problem: StateFlow<BillingProblem?> = MutableStateFlow(null)

    override val thanks: SharedFlow<String> = MutableSharedFlow()

    override val supportEntry: SupportEntry = SupportEntry.DonateLink(donateUrl)

    override suspend fun refresh() = Unit

    override fun purchase(activity: Activity, productId: String) = Unit

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.UNAVAILABLE
}
