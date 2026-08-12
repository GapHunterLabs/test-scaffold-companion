package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AssertionInferrerTest : BasePlatformTestCase() {

    fun testInfersNotNullForStringReturnType() {
        assertEquals(InferredAssertion.NotNull, inferFor("public String greet() { return \"hi\"; }"))
    }

    fun testInfersNotNullForListReturnType() {
        assertEquals(InferredAssertion.NotNull, inferFor("public java.util.List<String> names() { return null; }"))
    }

    fun testReturnsNullForVoidReturnType() {
        assertNull(inferFor("public void run() {}"))
    }

    fun testReturnsNullForAPrimitiveReturnType() {
        // A primitive can never be null, so a generic NotNull default
        // would be trivially true -- this must stay unresolved, not
        // silently generate a meaningless assertion (DEVELOPMENT_PLAN.md
        // section 1.3).
        assertNull(inferFor("public int total() { return 0; }"))
    }

    fun testReturnsNullForAnUnknownUserDefinedType() {
        assertNull(inferFor("public Acme build() { return null; }"))
    }

    private fun inferFor(methodSource: String): InferredAssertion? {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                $methodSource
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val method = PublicMethodCollector.collect(psiClass).first()
        return AssertionInferrer.inferAssertion(method)
    }
}
