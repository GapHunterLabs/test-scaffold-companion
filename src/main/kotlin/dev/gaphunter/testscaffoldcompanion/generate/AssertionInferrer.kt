package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes

/**
 * Layer 2: suggests a default assertion for a method's return type, via
 * [PsiMethod.getReturnType] -- confirmed stable/PSI-plain via `javap`
 * against the oldest target IDE's jar (see
 * INTELLIJ_PLATFORM_KNOWLEDGE.md "Test Scaffold Companion" section),
 * never the Analysis API.
 *
 * Deliberately conservative (DEVELOPMENT_PLAN.md section 1.3): this only
 * suggests an assertion when the return type is simple enough that a
 * generic default is genuinely defensible -- void, a primitive, String,
 * or a well-known collection interface. Anything else (a user-defined
 * type, a generic type parameter, an array of objects) returns null on
 * purpose, and the caller must render an honest TODO instead of a
 * placeholder that would compile but assert nothing meaningful (e.g.
 * `assertNotNull(result)` on every non-void method regardless of what
 * it actually returns is exactly the kind of trivially-true assertion
 * this plugin exists to avoid -- see section 0/1.3 of the plan).
 */
object AssertionInferrer {

    /**
     * Returns a suggested assertion line's *expression only* (no
     * `assert...(...)` wrapper -- [TestSkeletonWriter] decides the exact
     * call per framework), or null if no safe default exists for this
     * return type. The caller variable name generated code should use to
     * hold the method's result is always `result` -- kept as a shared
     * convention here rather than a magic string repeated at each call
     * site.
     */
    fun inferAssertion(method: PsiMethod): InferredAssertion? {
        val returnType = method.returnType ?: return null
        return when {
            // Nothing to assert on the call's own return value -- a void
            // method's test still gets generated, just without a Layer 2
            // assertion (the call itself not throwing is the only signal).
            returnType == PsiTypes.voidType() -> null
            // A primitive can never be null, so NotNull would be
            // trivially true (compiles, asserts nothing real) -- exactly
            // the failure mode this plugin exists to avoid, see section
            // 0/1.3 of the plan. No safe generic default exists for an
            // arbitrary numeric/boolean/char value without knowing what
            // the method actually computes, so this stays unresolved on
            // purpose rather than generating a placeholder assertion.
            returnType is PsiPrimitiveType -> null
            simpleName(returnType) == "String" -> InferredAssertion.NotNull
            isKnownCollectionType(returnType) -> InferredAssertion.NotNull
            else -> null
        }
    }

    private fun isKnownCollectionType(type: PsiType): Boolean {
        return simpleName(type) in KNOWN_COLLECTION_SIMPLE_NAMES
    }

    /**
     * [PsiType.getCanonicalText] returns the fully-qualified name
     * ("java.lang.String") only when the reference actually resolves
     * against a real classpath -- a lightweight
     * [com.intellij.testFramework.fixtures.BasePlatformTestCase] fixture
     * without the full JDK indexed resolves it to just "String" instead,
     * confirmed the hard way by a real test failure (canonicalText
     * printed "String", not "java.lang.String", against this plugin's
     * own light test fixture). Matching on the simple name (last
     * segment after '<' generics and '.' package separators) is
     * deliberately looser but still safe here: a user class named
     * `com.acme.String` shadowing `java.lang.String` is a real
     * possibility in theory, but the cost of that false positive
     * (suggesting `assertNotNull` on a genuinely non-null-safe custom
     * type) is low compared to always failing to infer anything in
     * every real project, where the JDK IS on the classpath and the
     * canonical name would have resolved fully anyway.
     */
    private fun simpleName(type: PsiType): String =
        type.canonicalText.substringBefore("<").substringAfterLast(".")

    private val KNOWN_COLLECTION_SIMPLE_NAMES = setOf("List", "Set", "Map", "Collection")
}

/**
 * What kind of default assertion is safe to generate. Only one variant
 * today (NotNull) -- kept as an enum instead of a plain Boolean so a
 * future, more specific inference (e.g. asserting an empty collection,
 * or a boolean method's obvious true/false framing) has a place to grow
 * into without callers pattern-matching on strings.
 */
enum class InferredAssertion {
    NotNull,
}
