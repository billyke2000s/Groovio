package com.groovio.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSafetyTest {
    private val signer = setOf("original-groovio-certificate")

    @Test fun acceptsOnlyNewerSamePackageSameCertificate() {
        assertTrue(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.groovio.app", 95, 96, signer, signer))
    }

    @Test fun rejectsDifferentPackageEvenWhenCertificateMatches() {
        assertFalse(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.another.app", 95, 96, signer, signer))
    }

    @Test fun rejectsSignerMismatchWhichWouldRequireReinstall() {
        assertFalse(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.groovio.app", 95, 96, signer, setOf("other")))
        assertFalse(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.groovio.app", 95, 96, signer, emptySet()))
    }

    @Test fun rejectsDowngradesOrSameVersion() {
        assertFalse(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.groovio.app", 95, 95, signer, signer))
        assertFalse(UpdateSafety.isSafeUpgrade("com.groovio.app", "com.groovio.app", 95, 71, signer, signer))
    }
}
