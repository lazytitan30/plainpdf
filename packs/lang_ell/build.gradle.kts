plugins {
    id("com.android.asset-pack")
}

// One Tesseract language, delivered by Google Play when the user asks for it in Settings.
assetPack {
    packName.set("lang_ell")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
