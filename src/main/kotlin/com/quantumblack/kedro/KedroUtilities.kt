package com.quantumblack.kedro

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.util.castSafelyTo
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.impl.PyCallExpressionImpl

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


        val isKedroNodeCall: Boolean = element
            ?.parents
            ?.firstOrNull { it.elementType == PyElementTypes.CALL_EXPRESSION }
            .castSafelyTo<PyCallExpressionImpl>()
            ?.callee
            ?.name
            ?.contains(other = "node", ignoreCase = true) ?: false

        if (isKedroNodeCall) {


            val argListObject: PsiElement? = element
                ?.parents
                ?.firstOrNull { it.elementType == PyElementTypes.ARGUMENT_LIST }

            if (argListObject != null) {


                // to do detect first element within iterable within arg
                val isInputOutputArg: Boolean = element // Positionally 2nd, 3rd argument
                    .siblings(forward = false)
                    .toList()
                    .filter { it.elementType == PyTokenTypes.COMMA }
                    .size in arrayOf(1, 2)


                val isInputOutputKwargs: Boolean = try {

                    element // Detect input/output kwarg, possibly out of order
                        .parents
                        .withIndex()
                        .firstOrNull {   // Kwarg up to levels above  (one level for string, two for iterable)
                            (it.index < 2) && (it.value.elementType == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION)
                        }
                        ?.value
                        ?.reference
                        ?.canonicalText
                        ?.contains(Regex(pattern = "(in|out)puts")) ?: false
                } catch (e: IllegalAccessException) {
                    false
                }

                return (isInputOutputArg || isInputOutputKwargs)
            }
        }

        return false
    }
}
