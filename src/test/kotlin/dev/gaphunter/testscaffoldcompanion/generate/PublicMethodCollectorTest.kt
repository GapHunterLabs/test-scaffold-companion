package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PublicMethodCollectorTest : BasePlatformTestCase() {

    fun testCollectsOnlyPublicMethodsFromAJavaClass() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public int total(int a, int b) { return a + b; }
                private int helper() { return 0; }
                protected void log() {}
                public Acme() {}
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtilFindClass(file)
        val methods = PublicMethodCollector.collect(psiClass)

        assertEquals(1, methods.size)
        assertEquals("total", methods.first().name)
    }

    fun testExcludesObjectMethodOverrides() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public int total() { return 1; }
                @Override
                public String toString() { return "Acme"; }
                @Override
                public boolean equals(Object o) { return this == o; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtilFindClass(file)
        val methods = PublicMethodCollector.collect(psiClass)

        assertEquals(1, methods.size)
        assertEquals("total", methods.first().name)
    }

    fun testCollectsPublicMethodsFromAKotlinClass() {
        val file = myFixture.configureByText(
            "Acme.kt",
            """
            class Acme {
                fun total(a: Int, b: Int): Int = a + b
                private fun helper(): Int = 0
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtilFindClass(file)
        val methods = PublicMethodCollector.collect(psiClass)

        assertEquals(1, methods.size)
        assertEquals("total", methods.first().name)
    }

    // Works for both Java (PsiJavaFile) and Kotlin (KtFile) -- both
    // implement PsiClassOwner.getClasses(), the same stable PSI-level
    // view Ctrl+Click/Go to Declaration already relies on everywhere
    // else in this catalog. PsiTreeUtil.findChildOfType(PsiClass) does
    // NOT find a Kotlin class directly in the raw PSI tree (a KtClass
    // isn't a PsiClass by itself -- that's a light-class view built on
    // top), confirmed the hard way when this test failed with a real
    // NPE before switching to this mechanism.
    private fun PsiTreeUtilFindClass(file: com.intellij.psi.PsiFile): PsiClass =
        (file as com.intellij.psi.PsiClassOwner).classes.first()
}
