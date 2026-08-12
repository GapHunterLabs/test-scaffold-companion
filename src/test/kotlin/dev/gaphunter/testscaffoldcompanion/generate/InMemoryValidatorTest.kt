package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.testscaffoldcompanion.detect.TestFramework

/**
 * The actual product claim under test: this plugin refuses to hand the
 * user a skeleton that doesn't parse, instead of silently writing one
 * (the documented #1 complaint against Squaretest/TestMe, see
 * DEVELOPMENT_PLAN.md section 0). Both directions matter equally --
 * a false "this is broken" on genuinely valid generated text would be
 * just as bad as a false negative letting broken text through.
 */
class InMemoryValidatorTest : BasePlatformTestCase() {

    fun testFindsNoErrorInAGenuinelyValidGeneratedSkeleton() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public int total(int a, int b) { return a + b; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val methods = PublicMethodCollector.collect(psiClass)
        val text = TestSkeletonWriter.render(psiClass, methods, TestFramework.JUNIT5, packageName = null)

        val error = InMemoryValidator.findFirstSyntaxError(project, text, "AcmeTest.kt")

        assertNull(error)
    }

    fun testFindsASyntaxErrorInDeliberatelyMalformedText() {
        val malformed = """
            class BrokenTest {
                fun testSomething( {
                    // unbalanced parenthesis above -- deliberately invalid
            }
        """.trimIndent()

        val error = InMemoryValidator.findFirstSyntaxError(project, malformed, "BrokenTest.kt")

        assertNotNull(error)
    }
}
