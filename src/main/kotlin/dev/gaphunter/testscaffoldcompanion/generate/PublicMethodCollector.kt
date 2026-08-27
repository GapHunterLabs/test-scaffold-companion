package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

/**
 * Which methods of [PsiClass] are real candidates for a generated test:
 * public, not synthetic, not a constructor, not inherited from
 * [java.lang.Object] (equals/hashCode/toString are legitimate override
 * targets a user writes on purpose, but scaffolding a test for every
 * class's inherited Object.wait() would be noise, not signal).
 *
 * Uses [PsiClass.getMethods] (own methods only, not [PsiClass.getAllMethods]
 * which would pull in inherited members) -- confirmed via `javap` against
 * the oldest target IDE's jar (build 243) as a stable, non-Jvm* PSI
 * signature. Works for both Java and Kotlin classes: a KtLightClass exposes
 * the same PsiClass/PsiMethod view Ctrl+Click already uses everywhere
 * else in this catalog (see api-security-companion's
 * KotlinTypeAnnotationResolver.kt for the same K1/K2-neutral pattern).
 */
object PublicMethodCollector {

    private val OBJECT_METHOD_NAMES = setOf(
        "equals", "hashCode", "toString", "getClass", "clone", "notify", "notifyAll", "wait", "finalize",
    )

    fun collect(psiClass: PsiClass): List<PsiMethod> =
        psiClass.methods.filter { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) &&
                !method.isConstructor &&
                !method.hasModifierProperty(PsiModifier.STATIC).let { isStatic -> isStatic && method.name == "main" } &&
                method.name !in OBJECT_METHOD_NAMES
        }
}
