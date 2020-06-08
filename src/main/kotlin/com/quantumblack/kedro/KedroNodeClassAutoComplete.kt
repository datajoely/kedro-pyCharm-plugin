package com.quantumblack.kedro

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyCallExpression
import org.jetbrains.annotations.NotNull

class KedroNodeClassAutoComplete : CompletionContributor() {
    init {

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {

                override fun addCompletions(
                    parameters: @NotNull CompletionParameters,
                    context: @NotNull ProcessingContext,
                    resultSet: @NotNull CompletionResultSet
                ) {

                    PsiTreeUtil.getParentOfType(
                        parameters.originalPosition,
                        PyCallExpression::class.java
                    )
                    if (KedroUtilities.isKedroNodeCatalogItem(parameters.originalPosition)) {
                        resultSet.addAllElements(KedroDataCatalogManager.getKedroDataSetSuggestions())
                    }
                }
            }
        )
    }
}
