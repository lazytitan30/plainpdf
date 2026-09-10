package com.leaf.app.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Entitlement source for cosmetic unlocks. The UI only ever asks [isSupporter]; it does not
 * know whether the answer comes from Play Billing or from a build that unlocks everything.
 */
interface SupporterRepository {

    /** Source of truth for the UI. Backed by DataStore so it is correct offline. */
    val isSupporter: StateFlow<Boolean>

    /** What earned the unlock, so the screen can thank the right gesture. */
    val standing: StateFlow<SupporterStanding>

    /** Purchasable products with localised prices. Empty when there is nothing to buy. */
    val products: StateFlow<List<SupporterProduct>>

    /** Why Google Play returned no products, for the screen to explain; null when all is well. */
    val problem: StateFlow<BillingProblem?>

    /** The product ID of each completed purchase, once, so the screen can say thank you at that moment. */
    val thanks: SharedFlow<String>

    /** How this build lets people give back: an in-app purchase screen or a plain link. */
    val supportEntry: SupportEntry

    /** Re-query ownership. Called on app start and resume. Safe to call when offline. */
    suspend fun refresh()

    fun purchase(activity: Activity, productId: String)

    suspend fun restorePurchases(): RestoreResult
}

/**
 * [unlockOwned] is what Google Play holds for the account and can restore. [tipped] is
 * remembered on this phone only: the big tip is consumed so it can be given again, which
 * also means Play keeps no record of it.
 */
data class SupporterStanding(val unlockOwned: Boolean = false, val tipped: Boolean = false)

/** Why Google Play returned no products. The Supporter screen turns each into one plain sentence. */
enum class BillingProblem { NOT_CONNECTED, NOT_LISTED, NOT_IN_REGION }

data class SupporterProduct(
    val id: String,
    val title: String,
    val formattedPrice: String,
    val consumable: Boolean,
)

sealed interface SupportEntry {
    data object InApp : SupportEntry
    data class DonateLink(val url: String) : SupportEntry
}

enum class RestoreResult { RESTORED, NOTHING_FOUND, UNAVAILABLE }

object SupporterProducts {
    const val UNLOCK = "supporter_unlock"
    const val TIP_SMALL = "tip_small"
    const val TIP_MEDIUM = "tip_medium"
    const val TIP_LARGE = "tip_large"
    val TIPS = listOf(TIP_SMALL, TIP_MEDIUM, TIP_LARGE)

    /** The one tip generous enough to carry the Supporter pack with it. The smaller ones only say thank you. */
    const val TIP_GRANTS_PACK = TIP_LARGE
}
