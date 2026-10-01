package dev.gaphunter.testscaffoldcompanion.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestLocationTest {

    private val main = "/p/src/main/java/com/acme/pricing"

    @Test
    fun `the mirrored package directory is used when it exists`() {
        val target = TestLocation.targetPath(main, "/p/src/main/java", listOf("/p/src/test/java"), "com.acme.pricing") { it == "/p/src/test/java/com/acme/pricing" }
        assertEquals("/p/src/test/java/com/acme/pricing", target)
    }

    // Regression (2026-10-01): with the test root present but not the
    // package directory, the test went to src/main (production code).
    @Test
    fun `the package directory under the test root is the target when it does not exist yet`() {
        val target = TestLocation.targetPath(main, "/p/src/main/java", listOf("/p/src/test/java"), "com.acme.pricing") { false }
        assertEquals("/p/src/test/java/com/acme/pricing", target)
    }

    @Test
    fun `the test root that mirrors the class's root is preferred`() {
        val target = TestLocation.targetPath(
            "/p/src/main/kotlin/com/acme", "/p/src/main/kotlin", listOf("/p/src/test/java", "/p/src/test/kotlin"), "com.acme",
        ) { false }
        assertEquals("/p/src/test/kotlin/com/acme", target)
    }

    @Test
    fun `a module without a test root has no target`() {
        assertNull(TestLocation.targetPath(main, "/p/src/main/java", emptyList(), "com.acme.pricing") { false })
    }

    @Test
    fun `the default package goes to the root itself`() {
        assertEquals("/p/test", TestLocation.targetPath("/p/src", "/p/src", listOf("/p/test"), "") { false })
    }
}
