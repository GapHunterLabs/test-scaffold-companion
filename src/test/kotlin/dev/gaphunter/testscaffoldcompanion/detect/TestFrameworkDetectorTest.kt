package dev.gaphunter.testscaffoldcompanion.detect

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression coverage for the real off-by-a-suffix bug documented on
 * [TestFrameworkDetector.testSiblingModuleName]: a Gradle
 * "separate module per source set" project names its two modules
 * `<base>.main` and `<base>.test`, and naively appending `.test` to
 * whichever one this plugin was already given produces a name
 * (`<base>.main.test`) that never exists.
 */
class TestFrameworkDetectorTest : BasePlatformTestCase() {

    fun testDerivesTheTestSiblingNameFromAMainModule() {
        assertEquals("acme-order-service.test", TestFrameworkDetector.testSiblingModuleName("acme-order-service.main"))
    }

    fun testDerivesItsOwnNameWhenAlreadyGivenTheTestModule() {
        assertEquals("acme-order-service.test", TestFrameworkDetector.testSiblingModuleName("acme-order-service.test"))
    }

    fun testAppendsPlainlyForASingleModuleProjectWithNoSourceSetSplit() {
        assertEquals("acme-order-service.test", TestFrameworkDetector.testSiblingModuleName("acme-order-service"))
    }
}
