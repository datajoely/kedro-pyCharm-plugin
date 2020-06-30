package com.quantumblack.kedro

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.impl.TextRangeInterval
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyElementTypes
import com.quantumblack.kedro.KedroPsiUtilities.determineActiveProject


class KedroDataCatalogAnnotation : Annotator {


    private val project: Project = determineActiveProject()
    private val service: KedroYamlCatalogService = KedroYamlCatalogService.getInstance(project)

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (KedroPsiUtilities.isKedroNodeCatalogParam(element, autoCompletePotential = false)) {


            if (element.elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
                val kedroDataSet: KedroDataSet? = service.getDataSetByName(element.text)

                if (kedroDataSet != null) {

                    val layer: String = if (kedroDataSet.layer != null) " (${kedroDataSet.layer})" else ""
                    holder.newAnnotation(HighlightSeverity.INFORMATION, "Catalog reference")
                        .tooltip("${kedroDataSet.type}$layer")
                        .textAttributes(DefaultLanguageHighlighterColors.METADATA)
                        .range(
                            TextRangeInterval(
                                element.textRange.startOffset + 1,
                                element.textRange.endOffset - 1
                            )
                        )
                        .create()
                }
            }
        }
    }
}
