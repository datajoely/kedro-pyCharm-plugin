package com.quantumblack.kedro

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.impl.PyArgumentListImpl

/**
 * This class exposes a way of detecting Kedro catalog params
 */
object KedroUtilities {

    /**
     * This function confirms that the string is within a Kedro node input/output paramter
     *
     * @param element The element scanned by the IDE
     * @return True if element within Node/node call and the relevant parameters
     */
    fun isKedroNodeCatalogParam(element: PsiElement?): Boolean {
        /**
         * This function detects if the element passed in refers to either the
         * `input` or `output` param
         *
         * @param referenceElement the element which is likely within the Node constructor or
         * node function
         * @return True if part of the input/output parameter within function/constructor call
         */
        fun isInputOutputKwarg(referenceElement: PsiElement?): Boolean {

            return referenceElement?.reference
                ?.canonicalText
                ?.contains(Regex(pattern = "(in|out)puts")) ?: false
        }

        val nodeCall: Boolean = element?.parentOfType<PyCallExpression>()
            ?.callee
            ?.name
            ?.contains(other = "node", ignoreCase = true) ?: false

        val inputOutputKwargsIterable: Boolean = isInputOutputKwarg(element?.parent?.parent)
        val inputOutputKwargsLiteral: Boolean = isInputOutputKwarg(element?.parent)

        val inputOutputArgs: Boolean = element?.parent.castSafelyTo<PyArgumentListImpl>()
            ?.arguments
            ?.map { it.text }
            ?.indexOf(element?.text) in arrayOf(1, 2)

        return nodeCall && (inputOutputKwargsIterable || inputOutputKwargsLiteral || inputOutputArgs)
    }
}
