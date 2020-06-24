package com.quantumblack.kedro

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
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
                    val element: PsiElement? = parameters.originalPosition
                    val project: Project? = parameters.editor.project

                    if (element != null) {

                        val service :KedroYamlCatalogService = KedroYamlCatalogService.getInstance(project!!)
                        if (KedroPsiUtilities.isKedroNodeCatalogParam(
                                element = element,
                                autoCompletePotential = true
                            )
                        ) {
                            resultSet.addAllElements(service.dataSets.map { it.getAutoCompleteSuggestion() })
                        }
                    }
                }
            }
        )
    }
}
