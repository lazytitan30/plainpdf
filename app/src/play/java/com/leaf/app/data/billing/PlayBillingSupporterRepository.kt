package com.leaf.app.data.billing

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.leaf.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Play flavour. DataStore is the UI's source of truth; billing only ever updates it. Every
 * purchase is acknowledged at once, otherwise Google refunds it after three days. Tips are
 * consumed at once so they can be given again; the big one also grants the pack, and that
 * grant is remembered on this phone because a consumed purchase leaves no record in Play.
 * No server, no receipt validation: this is a tip jar on an offline utility.
 */
class PlayBillingSupporterRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : SupporterRepository, PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    override val standing: StateFlow<SupporterStanding> = settings.settings
        .map { SupporterStanding(unlockOwned = it.supporterUnlockOwned, tipped = it.supporterTipped) }
        .stateIn(scope, SharingStarted.Eagerly, SupporterStanding())

    override val isSupporter: StateFlow<Boolean> = settings.settings
        .map { it.isSupporter }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _products = MutableStateFlow<List<SupporterProduct>>(emptyList())
    override val products: StateFlow<List<SupporterProduct>> = _products

    private val _problem = MutableStateFlow<BillingProblem?>(null)
    override val problem: StateFlow<BillingProblem?> = _problem

    private val _thanks = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val thanks: SharedFlow<String> = _thanks

    override val supportEntry: SupportEntry = SupportEntry.InApp

    private var details: Map<String, ProductDetails> = emptyMap()

    /** Purchases are processed one batch at a time, so a resume check cannot race the purchase callback. */
    private val processing = Mutex()

    /** Tokens already thanked, so a purchase seen by two checks is thanked once. */
    private val thanked = mutableSetOf<String>()

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        // On app start and every return to the foreground, re-check ownership.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    scope.launch { refresh() }
                }
            },
        )
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                            if (!ok) _problem.value = BillingProblem.NOT_CONNECTED
                            if (cont.isActive) cont.resume(ok)
                        }

                        override fun onBillingServiceDisconnected() {
                            _problem.value = BillingProblem.NOT_CONNECTED
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }
        }
    }

    override suspend fun refresh() {
        if (!connect()) return
        runCatching { loadProducts() }
        runCatching { reconcilePurchases() }
    }

    private suspend fun loadProducts() {
        val ids = listOf(SupporterProducts.UNLOCK) + SupporterProducts.TIPS
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        // The plain callback carries the unfetched-product list with per-product status codes,
        // which the Kotlin helper drops; that list is what says why a product is missing.
        val (billingResult, queryResult) = suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult?>> { cont ->
            client.queryProductDetailsAsync(params) { br, qr -> if (cont.isActive) cont.resume(br to qr) }
        }
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _problem.value = BillingProblem.NOT_CONNECTED
            return
        }
        val list = queryResult?.productDetailsList.orEmpty()
        val unfetched = queryResult?.unfetchedProductList.orEmpty()
        _problem.value = when {
            unfetched.any { it.statusCode == UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER } -> BillingProblem.NOT_IN_REGION
            unfetched.isNotEmpty() || list.isEmpty() -> BillingProblem.NOT_LISTED
            else -> null
        }
        details = list.associateBy { it.productId }
        _products.value = ids.mapNotNull { id ->
            val pd = details[id] ?: return@mapNotNull null
            SupporterProduct(
                id = id,
                title = pd.name,
                // Play's newer product model lists purchase options; the old single-offer
                // getter only sees an option flagged as backwards compatible. Read both.
                formattedPrice = pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                    ?: pd.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: "",
                consumable = id in SupporterProducts.TIPS,
            )
        }
    }

    /** Returns true when Google Play holds the pack for this account after the check. */
    private suspend fun reconcilePurchases(): Boolean = processing.withLock {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return standing.value.unlockOwned
        var unlockOwned = false
        for (purchase in result.purchasesList) if (handle(purchase) == Grant.PACK) unlockOwned = true
        settings.setSupporterUnlockOwned(unlockOwned)
        unlockOwned
    }

    private enum class Grant { NONE, PACK, GIFT }

    /**
     * Acknowledge or consume as appropriate. [Grant.PACK] means Play holds the unlock for this
     * account; [Grant.GIFT] means a tip was consumed, and the big one has been remembered.
     */
    private suspend fun handle(purchase: Purchase): Grant {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return Grant.NONE
        val tip = purchase.products.firstOrNull { it in SupporterProducts.TIPS }
        if (tip != null) {
            val firstSight = thanked.add(purchase.purchaseToken)
            if (firstSight && tip == SupporterProducts.TIP_GRANTS_PACK) settings.setSupporterTipped(true)
            // Consumed at once so it can be given again. If this fails, the purchase stays in
            // Play's list and the next check tries again.
            client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
            if (firstSight) _thanks.tryEmit(tip)
            return Grant.GIFT
        }
        if (SupporterProducts.UNLOCK in purchase.products) {
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
                if (thanked.add(purchase.purchaseToken)) _thanks.tryEmit(SupporterProducts.UNLOCK)
            }
            return Grant.PACK
        }
        return Grant.NONE
    }

    override fun purchase(activity: Activity, productId: String) {
        val pd = details[productId] ?: run {
            scope.launch { refresh() }
            return
        }
        // A product with purchase options needs the option's token; the old model has none.
        val offerToken = pd.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { list ->
                scope.launch {
                    processing.withLock {
                        var unlockOwned = false
                        for (p in list) if (handle(p) == Grant.PACK) unlockOwned = true
                        if (unlockOwned) settings.setSupporterUnlockOwned(true)
                    }
                }
            }
            // A tip that never got consumed (the phone went offline at the wrong moment)
            // blocks the next one. Consume it now so the next tap goes through.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { runCatching { reconcilePurchases() } }
            else -> Unit
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        if (!connect()) return RestoreResult.UNAVAILABLE
        val owned = runCatching { reconcilePurchases() }.getOrNull() ?: return RestoreResult.UNAVAILABLE
        return if (owned) RestoreResult.RESTORED else RestoreResult.NOTHING_FOUND
    }
}
