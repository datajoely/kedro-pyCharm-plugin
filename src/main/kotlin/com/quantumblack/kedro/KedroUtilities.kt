package com.quantumblack.kedro

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.impl.PyArgumentListImpl
import org.jetbrains.annotations.Nullable

object KedroUtilities {
    fun isKedroNodeCatalogItem(element: @Nullable PsiElement?): Boolean {

        val nodeCall: Boolean = element?.parentOfType<PyCallExpression>()
            ?.callee
            ?.name
            ?.contains(other = "node", ignoreCase = true) ?: false

        val inputOutputKwargs: Boolean = element?.parent
            ?.parent
            ?.reference
            ?.canonicalText
            ?.contains(Regex(pattern = "(in|out)puts")) ?: false

        val inputOutputArgs: Boolean = element?.parent.castSafelyTo<PyArgumentListImpl>()
            ?.arguments
            ?.map { it.text }
            ?.indexOf(element?.text) in arrayOf(1, 2)

        return nodeCall && (inputOutputKwargs || inputOutputArgs)
    }
}
