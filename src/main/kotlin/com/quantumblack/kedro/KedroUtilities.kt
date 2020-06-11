package com.quantumblack.kedro

import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import com.jetbrains.python.psi.PyCallExpression

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

        val isKedroNodeCall: Boolean = element?.parentOfType<PyCallExpression>()
            ?.callee
            ?.name
            ?.contains(other = "node", ignoreCase = true) ?: false

        if (isKedroNodeCall) {

            val argListTypeString = "Py:ARGUMENT_LIST"
            val argListObject: PsiElement? = element
                ?.parents
                ?.takeWhile { it.elementType.toString() != argListTypeString }
                ?.lastOrNull()
                ?: if (element?.parent.elementType.toString() == argListTypeString) element else null

            if (argListObject != null) {

                val isInputOutputArg: Boolean = argListObject // Positionally 2nd, 3rd argument
                    .siblings(forward = false)
                    .toList()
                    .filter { it.elementType.toString() == "Py:COMMA" }
                    .size in arrayOf(1, 2)


                val isInputOutputKwargs: Boolean = element // Detect input/output kwarg, possibly out of order
                    ?.parents
                    ?.withIndex()
                    ?.filter{(i: Int,v: PsiElement) ->  // Kwarg up to levels above  (one level for string, two for iterable)
                        (i < 2)  && (v.elementType.toString() == "Py:KEYWORD_ARGUMENT_EXPRESSION")
                    }
                    ?.firstOrNull()
                    ?.value
                    ?.reference
                    ?.canonicalText
                    ?.contains(Regex(pattern = "(in|out)puts")) ?: false

                return isInputOutputArg || isInputOutputKwargs
            }
        }

        return false
    }
}
