package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.testscaffoldcompanion.detect.TestFramework

class TestSkeletonWriterTest : BasePlatformTestCase() {

    fun testRendersOneTestMethodPerPublicMethodWithJUnit5Import() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public int total(int a, int b) { return a + b; }
                public void reset() {}
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val methods = PublicMethodCollector.collect(psiClass)

        val text = TestSkeletonWriter.render(psiClass, methods, TestFramework.JUNIT5, packageName = "com.acme")

        assertTrue(text.contains("package com.acme"))
        assertTrue(text.contains("import org.junit.jupiter.api.Test"))
        assertTrue(text.contains("class AcmeTest {"))
        assertTrue(text.contains("fun testTotal()"))
        assertTrue(text.contains("fun testReset()"))
        // Neither method has a Layer 2 safe default (int is primitive,
        // void has nothing to assert on) -- both stay honest TODOs, and
        // the assertNotNull import must NOT be pulled in for nothing.
        assertTrue(text.contains("TODO(test-scaffold)"))
        assertFalse(text.contains("assertNotNull"))
        assertFalse(text.contains("import org.junit.jupiter.api.Assertions.assertNotNull"))
    }

    fun testGeneratesARealAssertNotNullForAStringReturningStaticMethod() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public static String greet() { return "hi"; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val methods = PublicMethodCollector.collect(psiClass)

        val text = TestSkeletonWriter.render(psiClass, methods, TestFramework.JUNIT5, packageName = null)

        assertTrue(text.contains("import org.junit.jupiter.api.Assertions.assertNotNull"))
        assertTrue(text.contains("val result = Acme.greet()"))
        assertTrue(text.contains("assertNotNull(result)"))
        assertFalse(text.contains("TODO(test-scaffold)"))
    }

    // Regression (2026-10-01): `instance.greet()` was generated without any
    // declaration of `instance`, so the test file didn't compile.
    fun testDeclaresTheInstanceItCallsWithATodoToAssignIt() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public String greet() { return "hi"; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val methods = PublicMethodCollector.collect(psiClass)

        val text = TestSkeletonWriter.render(psiClass, methods, TestFramework.JUNIT5, packageName = null)

        assertTrue(text.contains("TODO(test-scaffold): assign a real or mocked Acme to `instance`"))
        assertTrue(text.contains("    private lateinit var instance: Acme"))
        assertTrue(text.contains("val result = instance.greet()"))
        assertTrue(text.indexOf("private lateinit var instance") < text.indexOf("instance.greet()"))
    }

    // Regression (2026-10-01): a double parameter got the placeholder `0`,
    // which Kotlin doesn't accept for a Double.
    fun testPlaceholdersAreTypedSoTheCallCompilesInKotlin() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public static String format(double amount, float rate, long id, char sep, int n, boolean b, String s) { return ""; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val text = TestSkeletonWriter.render(psiClass, PublicMethodCollector.collect(psiClass), TestFramework.JUNIT5, packageName = null)

        assertTrue(text, text.contains("val result = Acme.format(0.0, 0.0f, 0L, ' ', 0, false, \"\")"))
    }

    fun testAnObjectParameterIsNeverExecutedWithNull() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            import java.util.List;
            class Acme {
                public String label(List<String> parts) { return ""; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val text = TestSkeletonWriter.render(psiClass, PublicMethodCollector.collect(psiClass), TestFramework.JUNIT5, packageName = null)

        assertFalse(text, text.contains("val result = instance.label(null)"))
        assertTrue(text, text.contains("// TODO(test-scaffold): call instance.label(null) with real arguments"))
    }

    fun testAGenericClassGetsNoRawInstanceDeclaration() {
        val file = myFixture.configureByText(
            "Box.java",
            """
            class Box<T> {
                public String name() { return ""; }
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val text = TestSkeletonWriter.render(psiClass, PublicMethodCollector.collect(psiClass), TestFramework.JUNIT5, packageName = null)

        assertFalse(text, text.contains("lateinit var instance"))
        assertFalse(text, text.contains("val result = instance."))
        assertTrue(text, text.contains("Box is generic"))
    }

    fun testOmitsPackageDeclarationWhenClassHasNoPackage() {
        val file = myFixture.configureByText(
            "Acme.java",
            """
            class Acme {
                public void run() {}
            }
            """.trimIndent(),
        )
        val psiClass = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
        val methods = PublicMethodCollector.collect(psiClass)

        val text = TestSkeletonWriter.render(psiClass, methods, TestFramework.JUNIT4, packageName = null)

        assertFalse(text.contains("package "))
        assertTrue(text.contains("import org.junit.Test"))
    }
}
