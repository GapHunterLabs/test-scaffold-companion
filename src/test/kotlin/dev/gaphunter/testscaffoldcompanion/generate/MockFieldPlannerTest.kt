package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The test module this plugin's own test suite runs in has no Mockito
 * on its classpath (deliberately -- see DEVELOPMENT_PLAN.md: never add
 * Mockito as a dependency of the plugin itself). That makes the
 * "Mockito not available" branch the one real, cheaply-testable path
 * here -- it's also the most important one to verify, since generating
 * a `Mockito.mock(...)` call that doesn't resolve would be exactly the
 * kind of broken-skeleton failure this whole plugin exists to prevent.
 * The positive "Mockito available -> mock fields generated" path is
 * covered by manual runIde verification against a real project with
 * Mockito on its classpath (see KNOWN_ISSUES.md once that pass runs),
 * not by a unit test that would need to fake a library classpath this
 * plugin's own build never carries.
 */
class MockFieldPlannerTest : BasePlatformTestCase() {

    fun testReturnsNoFieldsWhenMockitoIsNotOnTheClasspath() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                private final Collaborator collaborator;
                public Acme(Collaborator collaborator) { this.collaborator = collaborator; }
                public void run() {}
            }
            interface Collaborator {}
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!

        val plan = MockFieldPlanner.plan(psiClass)

        assertTrue(plan.fieldDeclarations.isEmpty())
    }

    fun testReturnsNoFieldsForAClassWithNoConstructorDependencies() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public void run() {}
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!

        val plan = MockFieldPlanner.plan(psiClass)

        assertTrue(plan.fieldDeclarations.isEmpty())
    }
}
