package com.appblish.jgallery.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import java.util.EnumSet

/**
 * Enforces JGallery spec §1.6: ALL device file/media access must go through `:core:storage`.
 *
 * Fires when any module OTHER than the storage boundary references raw platform file/media APIs.
 * This detector is only wired into modules whose convention plugin adds it as a `lintChecks`
 * dependency — every module except `:core:storage` (see [AndroidStorageConventionPlugin]). The
 * module boundary is therefore structural, and this check makes leaks build-failing rather than
 * a review convention.
 */
class RawStorageAccessDetector : Detector(), SourceCodeScanner {

    // 1) Raw file handles / streams — the classic "reach past the abstraction" smell.
    override fun getApplicableConstructorTypes(): List<String> = FLAGGED_CONSTRUCTORS

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val type = constructor.containingClass?.qualifiedName ?: return
        report(context, node, "Constructs `$type` directly")
    }

    // 2) Environment / ContentResolver file-IO entry points.
    override fun getApplicableMethodNames(): List<String> = FLAGGED_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val owner = method.containingClass?.qualifiedName ?: return
        if (owner in FLAGGED_METHOD_OWNERS) {
            report(context, node, "Calls `$owner.${method.name}(...)`")
        }
    }

    // 3) MediaStore class references and the MANAGE_EXTERNAL_STORAGE permission constant.
    override fun getApplicableReferenceNames(): List<String> = FLAGGED_REFERENCES

    override fun visitReference(
        context: JavaContext,
        reference: org.jetbrains.uast.UReferenceExpression,
        referenced: com.intellij.psi.PsiElement,
    ) {
        when (referenced) {
            is PsiClass -> {
                val qn = referenced.qualifiedName ?: return
                if (qn == MEDIA_STORE || qn.startsWith("$MEDIA_STORE.")) {
                    report(context, reference, "References `$qn`")
                }
            }

            is PsiField -> {
                val owner = referenced.containingClass?.qualifiedName
                if (owner == PERMISSION_CLASS && referenced.name == MANAGE_EXTERNAL_STORAGE) {
                    report(context, reference, "References `MANAGE_EXTERNAL_STORAGE`")
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UElement, what: String) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "$what outside `:core:storage`. Route all file/media access through the " +
                "storage-access abstraction (spec §1.6) so the permission model can be swapped " +
                "without touching feature code.",
        )
    }

    companion object {
        private const val MEDIA_STORE = "android.provider.MediaStore"
        private const val PERMISSION_CLASS = "android.Manifest.permission"
        private const val MANAGE_EXTERNAL_STORAGE = "MANAGE_EXTERNAL_STORAGE"

        private val FLAGGED_CONSTRUCTORS = listOf(
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.RandomAccessFile",
        )

        private val FLAGGED_METHODS = listOf(
            "getExternalStorageDirectory",
            "getExternalStoragePublicDirectory",
            "openInputStream",
            "openOutputStream",
            "openFileDescriptor",
            "openAssetFileDescriptor",
        )

        private val FLAGGED_METHOD_OWNERS = setOf(
            "android.os.Environment",
            "android.content.ContentResolver",
            "android.content.ContentProviderClient",
        )

        private val FLAGGED_REFERENCES = listOf("MediaStore", MANAGE_EXTERNAL_STORAGE)

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "RawStorageAccess",
            briefDescription = "Raw file/media access outside :core:storage",
            explanation = """
                JGallery uses All Files Access today but must be able to migrate to media \
                permissions or SAF without rewriting features (spec §1.6, §9). To make that \
                possible, EVERY file and media read/write goes through the single `:core:storage` \
                abstraction. Referencing `java.io.File`, `Environment`, `MediaStore`, \
                `ContentResolver` file IO, or `MANAGE_EXTERNAL_STORAGE` anywhere else scatters the \
                permission model into feature code and breaks the escape hatch. Inject and use a \
                `StorageAccess` / repository from `:core:storage` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                RawStorageAccessDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}
