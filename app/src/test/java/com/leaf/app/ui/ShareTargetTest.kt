package com.leaf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTargetTest {

    @Test
    fun signAliasMapsToSign() {
        assertEquals(ShareTarget.SIGN, ShareTarget.fromClassName("com.leaf.app.share.SignTarget"))
        assertEquals(ShareTarget.SIGN, ShareTarget.fromClassName("com.plainpdf.app.share.SignTarget"))
    }

    @Test
    fun scanAliasMapsToScan() {
        assertEquals(ShareTarget.SCAN, ShareTarget.fromClassName("com.leaf.app.share.ScanTarget"))
        assertEquals(ShareTarget.SCAN, ShareTarget.fromClassName("com.plainpdf.app.share.ScanTarget"))
    }

    @Test
    fun mainActivityAndLauncherAliasesAreNotTargets() {
        assertNull(ShareTarget.fromClassName("com.leaf.app.MainActivity"))
        assertNull(ShareTarget.fromClassName("com.leaf.app.launcher.Default"))
        assertNull(ShareTarget.fromClassName("com.leaf.app.launcher.Night"))
    }

    @Test
    fun missingComponentIsNotATarget() {
        assertNull(ShareTarget.fromClassName(null))
        assertNull(ShareTarget.fromClassName(""))
    }

    @Test
    fun suffixMustBeAtTheEnd() {
        assertNull(ShareTarget.fromClassName("com.leaf.app.SignTargetHelper"))
        assertNull(ShareTarget.fromClassName("com.leaf.app.ScanTarget.Inner"))
    }
}
