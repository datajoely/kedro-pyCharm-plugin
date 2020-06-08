package com.quantumblack.kedro

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import org.jetbrains.annotations.NotNull

/**
 * This class adds Kedro catalog items to the IDE
 */
class KedroNodeClassAutoComplete : CompletionContributor() {
    init {

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {

                /**
                 * This function checks if the `parameters` is part of a Kedro constructor/function
                 * call and adds the Kedro data catalog suggestions to the autocomplete results set
                 *
                 * @param parameters The current element the user is working on in the IDE
                 * @param context The processing context in question
                 * @param resultSet The results set to add suggestions to
                 */
                override fun addCompletions(
                    parameters: @NotNull CompletionParameters,
                    context: @NotNull ProcessingContext,
                    resultSet: @NotNull CompletionResultSet
                ) {
                    if (KedroUtilities.isKedroNodeCatalogParam(parameters.originalPosition)) {
                        resultSet.addAllElements(KedroDataCatalogManager.getKedroDataSetSuggestions())
                    }
                }
            }
        )
    }
}
