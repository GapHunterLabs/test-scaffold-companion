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

    fun testMarksAnInstanceMethodCallWithATodoForInstanceAssignment() {
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
        assertTrue(text.contains("instance.greet()"))
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
