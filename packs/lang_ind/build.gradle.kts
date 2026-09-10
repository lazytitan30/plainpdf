plugins {
    id("com.android.asset-pack")
}

// One Tesseract language, delivered by Google Play when the user asks for it in Settings.
assetPack {
    packName.set("lang_ind")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
