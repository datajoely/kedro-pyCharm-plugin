package com.quantumblack.kedro


import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.IconLoader
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import javax.swing.Icon

class KedroNodeClassAutoComplete : CompletionContributor() {
    init {
        extend(CompletionType.BASIC,

            PlatformPatterns.psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters?>() {
                override fun addCompletions(
                    parameters: @NotNull CompletionParameters,
                    context: @NotNull ProcessingContext,
                    resultSet: @NotNull CompletionResultSet
                ) {
                    val kedroIcon: Icon = getIconInCorrectColourScheme(parameters)

                    val call: @Nullable PyCallExpression? = PsiTreeUtil.getParentOfType(
                        parameters.originalPosition,
                        PyCallExpression::class.java
                    )
                    if (call != null) {

                        val calleeExpression: @Nullable PyExpression? = call.callee
                        if (calleeExpression is PyReferenceExpression) {

                            val isWithinNodeConstructor: Boolean = calleeExpression.reference.canonicalText.contains(
                                other = "Node",
                                ignoreCase = true
                            )
                            val isKedroNodeInputOutputExpression: Boolean = detectKedroNodeInputOutputExpression(
                                parameters,
                                listOf(
                                    PyReferenceExpression::class.java, // Before writing
                                    PyStringLiteralExpression::class.java, // Writing a string
                                    PyStringLiteralExpressionImpl::class.java, // Writing a string
                                    PyListLiteralExpression::class.java // Writing within a list
                                )
                            )

                            if (isWithinNodeConstructor && isKedroNodeInputOutputExpression) {
                                for (i in 1 until 10) {
                                    val datasetName = "DataSet$i"
                                    val datasetType = "Type$i"
                                    resultSet.addElement(
                                        LookupElementBuilder.create("\"$datasetName\"")
                                            .withPresentableText(datasetName)
                                            .bold()
                                            .withCaseSensitivity(false)
                                            .withIcon(kedroIcon)
                                            .withTypeText(datasetType)
                                            .withLookupString(datasetName)
                                    )
                                }

                            }

                        }
                    }


                }

                private fun detectKedroNodeInputOutputExpression(
                    parameters: @NotNull CompletionParameters,
                    expressions: List<Class<out PyExpression>>
                ): Boolean {
                    return expressions.any { expr: Class<out PyExpression> ->
                        PsiTreeUtil.getParentOfType(parameters.position, expr)
                            ?.parent?.reference?.canonicalText?.contains(Regex(pattern = "inputs|outputs")) ?: false
                    }
                }


                private fun getIconInCorrectColourScheme(parameters: @NotNull CompletionParameters): @NotNull Icon {
                    return if (parameters.editor.colorsScheme.displayName.contains(other = "dark", ignoreCase = true)) {
                        IconLoader.getIcon("/icons/kedroIcon.svg") // Use light icon if editor is light
                    } else {
                        IconLoader.getIcon("/icons/kedroIcon_dark.svg") // Use dark icon if editor is dark
                    }
                }


            })
    }
}