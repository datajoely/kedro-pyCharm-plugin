package com.quantumblack.kedro

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.impl.TextRangeInterval
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyElementTypes

class KedroDataCatalogAnnotation : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        if (KedroPsiUtilities.isKedroNodeCatalogParam(element, autoCompletePotential = false)) {

            if (element.elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {

                val isDataSetCheck: Boolean = try {
                    KedroDataCatalogManager.isDataCatalogEntry(element.text, element.project)
                } catch (e: Exception) {
                    false
                }

                if (isDataSetCheck) {
                    val dataSetObject: KedroDataSet? = KedroDataCatalogManager.get(element.text, element.project)
                    if (dataSetObject != null) {
                        val layer: String = if (dataSetObject.layer != null) " (${dataSetObject.layer})" else ""
                        holder.newAnnotation(HighlightSeverity.INFORMATION, "Catalog reference")
                            .tooltip("${dataSetObject.type}$layer")
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
}
