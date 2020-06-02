package com.quantumblack.kedro

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.IconLoader
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl
import javax.swing.Icon
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class KedroNodeClassAutoComplete : CompletionContributor() {
    init {
        val icon: Icon = IconLoader.getIcon("/icons/kedroIcon_dark.svg")
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {

                override fun addCompletions(
                    parameters: @NotNull CompletionParameters,
                    context: @NotNull ProcessingContext,
                    resultSet: @NotNull CompletionResultSet
                ) {

                    val call: @Nullable PyCallExpression? = PsiTreeUtil.getParentOfType(
                        parameters.originalPosition,
                        PyCallExpression::class.java
                    )
                    if (call != null) {

                        val calleeExpression: @Nullable PyExpression? = call.callee
                        if (calleeExpression is PyReferenceExpression) {

                            val isKedroNodeInputOutputExpression: Boolean =
                                detectKedroNodeInputOutputKwargs(parameters) ||
                                            detectKedroNodeInputOutputArgs(call, parameters)

                            if (isKedroNodeCall(calleeExpression) && isKedroNodeInputOutputExpression) {
                                val catalogManager = KedroDataCatalogManager(call.project, icon)
                                resultSet.addAllElements(catalogManager.getKedroDataSetSuggestions())
                            }
                        }
                    }
                }

                private fun isKedroNodeCall(calleeExpression: PyReferenceExpression): Boolean {
                    return calleeExpression.reference.canonicalText.contains(
                        other = "Node",
                        ignoreCase = true
                    )
                }

                private fun detectKedroNodeInputOutputKwargs(
                    parameters: @NotNull CompletionParameters
                ): Boolean {

                    val expressions: List<Class<out PyExpression>> = listOf(
                        PyExpression::class.java,
                        PyReferenceExpressionImpl::class.java, // No kwargs, just args
                        PyReferenceExpression::class.java, // Before writing
                        PyStringLiteralExpression::class.java, // Writing a string
                        PyStringLiteralExpressionImpl::class.java, // Writing a string
                        PyListLiteralExpression::class.java // Writing within a list
                    )

                    return expressions.any { expr: Class<out PyExpression> ->
                        // Within a Class or Function expression
                        PsiTreeUtil.getParentOfType(parameters.position, expr)
                            ?.parent // If possible, get argument name
                            ?.reference // Get the the parameter reference
                            ?.canonicalText // Get the  text equivalent
                            ?.contains(Regex(pattern = "inputs|outputs")) ?: false
                    }
                }

                private fun detectKedroNodeInputOutputArgs(call: PyCallExpression, parameters: CompletionParameters):
                        Boolean {
                    val txtArgs: List<String> = call.arguments.mapNotNull { it.text }
                    val currentArg: String? = parameters.originalPosition?.text?.trim()
                    val argIndex: Int = txtArgs.indexOf(currentArg)
                    val isWithinArgIndex: Boolean = argIndex in arrayOf(1, 2)
                    val isEmptyArgString: Boolean = ( // Case where arg not typed out yet
                            currentArg == "" && call.argumentList?.arguments?.size in arrayOf(1,2)
                            )
                    return isWithinArgIndex || isEmptyArgString

                }
            }
        )
    }
}
