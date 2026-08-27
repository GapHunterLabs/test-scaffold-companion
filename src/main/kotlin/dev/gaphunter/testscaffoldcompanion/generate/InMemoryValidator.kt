package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * The diff between this plugin and every free competitor (see
 * DEVELOPMENT_PLAN.md section 0/1.1): generated text is parsed into a
 * throwaway, never-persisted [com.intellij.psi.PsiFile] and checked for
 * syntax errors BEFORE it's ever offered to the user as something to
 * write to disk. Uses [PsiFileFactory.createFileFromText] -- the exact
 * "sandbox PSI" mechanism already proven in refactor-simulator: the
 * file this creates is never added to a
 * [com.intellij.psi.PsiDirectory], so it carries zero risk to the real
 * project regardless of what it contains.
 *
 * Layer 1 check only: syntax validity (no [PsiErrorElement] anywhere in
 * the tree). Reference resolution (does the detected framework's import
 * actually resolve, does every generated symbol resolve) is a Layer 2
 * concern once real assertions/mocks are being generated -- a Layer 1
 * skeleton's only external reference is the `@Test` import already
 * proven to resolve by [dev.gaphunter.testscaffoldcompanion.detect.TestFrameworkDetector]
 * before generation even started, so re-checking it here would be
 * redundant, not defense in depth.
 */
object InMemoryValidator {

    /**
     * Null return means "safe to write, no syntax errors found". A
     * non-null return is the first error's own human-readable text, for
     * surfacing to the user as the honest "could not generate safely
     * here" outcome DEVELOPMENT_PLAN.md section 1.3 requires -- never
     * silently write text that failed this check.
     *
     * **Real bug found live: `PsiManager.dropPsiCaches()` requires the
     * EDT specifically, not just a read action.** It was called here as
     * an (unnecessary) attempt to make the freshly-created `PsiFile`
     * "settle" before walking it -- but [PsiFileFactory.createFileFromText]
     * already returns a fully-parsed tree; nothing needs a global
     * cache invalidation to read a `PsiErrorElement` off a file that
     * was never added to any project structure in the first place.
     * Removing the call was the fix, not moving it to the EDT (that
     * would defeat the whole point of running this off the EDT).
     * Confirmed via a real `runIde` crash
     * (`RuntimeExceptionWithAttachments: Access is allowed from Event
     * Dispatch Thread (EDT) only`).
     */
    fun findFirstSyntaxError(project: Project, generatedText: String, fileName: String): String? {
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(fileName, KotlinLanguage.INSTANCE, generatedText, /* eventSystemEnabled = */ false, /* markAsCopy = */ true)
        val firstError = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)
        return firstError?.errorDescription
    }
}
