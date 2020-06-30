package com.quantumblack.kedro

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.util.castSafelyTo
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyCallExpressionImpl
import com.jetbrains.python.psi.impl.PyKeywordArgumentImpl
import java.awt.Window

/**
 * This class exposes a way of detecting Kedro catalog params
 */
object KedroPsiUtilities {

    // Corresponding to the node(func=f,inputs={1},outputs={2})
    private val NODE_CATALOG_ARGS: Array<Int> = arrayOf(1, 2)

    /**
     * This function confirms that the string is within a Kedro node input/output parameter
     *
     * @param element The element scanned by the IDE
     * @return True if element within Node/node call and the relevant parameters
     */
    fun isKedroNodeCatalogParam(element: PsiElement, autoCompletePotential: Boolean = false): Boolean {

        val callableExpression: PyCallExpressionImpl? = getCallingExpressionAncestor(element)
        val isWithinKedroNode: Boolean = isKedroNodeCallable(callableExpression) && isKedroNodeImport(element)

        if (isWithinKedroNode) {

            val isKwarg: Boolean = isKwarg(element)
            val isArg: Boolean = !isKwarg
            val is2nd3rdParam: Boolean = is2nd3rdParam(callableExpression, element)
            val isInputOutputKwarg: Boolean = isInputOutputKwarg(element)
            val isPotential2nd3rdParam: Boolean = isPotential2nd3rdParam(element)

            return if ((isKwarg && isInputOutputKwarg) || (isArg && is2nd3rdParam)) {
                true
            } else
                (isPotential2nd3rdParam && autoCompletePotential) // Used to predictive autocomplete
        }
        return false
    }

    /**
     * This function is used to detect when the user is in a place where autocorrect may be relevant,
     * but is not a real reference yet. This is used for autocomplete, but not the other context such as
     * reference.
     *
     * This is achieved by counting the number of preceding COMMA tokens observed within the
     * node function/constructor call
     *
     * @param element the element to detect
     * @return True if the user is in a valid position
     */
    private fun isPotential2nd3rdParam(element: PsiElement): Boolean {

        val isParentArgList: Boolean = element.parent == PyElementTypes.ARGUMENT_LIST

        val lastChildBeforeArguments: PsiElement? = element.parents
            .takeWhile { it.elementType != PyElementTypes.ARGUMENT_LIST }
            .lastOrNull() ?: if (isParentArgList) element else null

        val countOfPrecedingCommas: Int = lastChildBeforeArguments
            ?.siblings(forward = false)
            ?.filter { it.elementType == PyTokenTypes.COMMA }
            ?.count() ?: 0

        return countOfPrecedingCommas in NODE_CATALOG_ARGS
    }

    /**
     * This function is used to check if the element is within a Python Keyword Argument tree.
     * We bubble up the PsiTree and count the number of instances of `PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION`
     * we observe
     *
     * @param element
     * @return True if element is within a kwarg
     */
    private fun isKwarg(element: PsiElement): Boolean {
        return element
            .parents
            .filter { it.elementType == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION }
            .count() > 0
    }

    /**
     * This function is will assert that the keyword argument is one of the two DataCatalog
     * entry points: "input" or "output"
     * @param element The element to analyse
     * @return True if element within `input` or `output` keyword
     */
    private fun isInputOutputKwarg(element: PsiElement): Boolean {
        return element
            .parents
            .filter { it.elementType == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION }
            .firstOrNull()
            .castSafelyTo<PyKeywordArgumentImpl>()
            ?.keyword
            ?.contains(regex = Regex("(in|out)put"))
            ?: false
    }

    /**
     * This function establishes if an expression is within the inputs or outputs
     * arguments from position alone i.e. Kwarg is not used. This works by getting the argument
     * PsiElements which fall within the NODE_CATALOG_ARGS indices and working out if the element in
     * question is a child or not
     *
     * @param callableExpression The function/class constructor call
     * @param element to process
     * @return Boolean if the expression identified is positionally within the
     * two DataCatalog arguments
     */
    private fun is2nd3rdParam(
        callableExpression: PyCallExpressionImpl?,
        element: PsiElement
    ): Boolean {
        return (callableExpression
            ?.arguments
            ?.filterIndexed { index: Int, _: PyExpression -> index in NODE_CATALOG_ARGS }
            ?.any { it.isAncestor(element) }
            ?: false)
    }

    /**
     * This function analyses the current file and checks that the `node` function or the `Node`
     * class have been imported from the `kedro` library. This is because the name `node` is common
     * and this stops the plugin kicking in the wrong place
     *
     * @param element The element in question, in which the `import` / `from` statements are extracted
     * @return Boolean if confirmed Kedro node/Node call
     */
    private fun isKedroNodeImport(element: PsiElement): Boolean {

        return element.containingFile?.children?.filter {
            it.elementType in arrayOf(
                PyElementTypes.IMPORT_STATEMENTS,
                PyElementTypes.IMPORT_STATEMENT,
                PyElementTypes.FROM_IMPORT_STATEMENT
            )
        }?.any {
            it.text.contains(other = "kedro") && it.text.contains(other = "node", ignoreCase = true)
        } == true
    }

    /**
     * This function is used to extract the calling expression of the element as part of the
     * check to see if the user is indeed within a Kedro Node constructor or node function call
     *
     * @param element The element bubble up from
     * @return True if there is a calling ancestor
     */
    private fun getCallingExpressionAncestor(element: PsiElement?): PyCallExpressionImpl? {
        return element
            ?.parents
            ?.filter { it.elementType == PyElementTypes.CALL_EXPRESSION }
            ?.lastOrNull()
            .castSafelyTo<PyCallExpressionImpl>()
    }

    /**
     * This function checks if the calling expression is called Node or node
     *
     * @param callableExpression The calling expression detected
     * @return True if called Node or node
     */
    private fun isKedroNodeCallable(callableExpression: PyCallExpressionImpl?): Boolean {
        return callableExpression
            ?.callee
            ?.name
            ?.contains(other = "node", ignoreCase = true) ?: false
    }

    fun determineActiveProject(): Project {
        val projects: Array<Project> = ProjectManager.getInstance().openProjects
        val activeWindow: Pair<Int, Window?> = projects.withIndex()
            .map { (i: Int, p: Project) -> i to WindowManager.getInstance().suggestParentWindow(p) }
            .first { (_: Int, p: Window?) -> p != null }
        return projects[activeWindow.first]
    }
}
