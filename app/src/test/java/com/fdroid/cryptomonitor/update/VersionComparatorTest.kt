package com.fdroid.cryptomonitor.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `detects newer semantic version`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.9"))
    }

    @Test
    fun `detects older semantic version`() {
        assertFalse(VersionComparator.isNewer("v1.2.0", "1.2.1"))
    }

    @Test
    fun `treats equal numeric parts as not newer`() {
        assertFalse(VersionComparator.isNewer("release-2.0.0", "2.0.0"))
    }
}
