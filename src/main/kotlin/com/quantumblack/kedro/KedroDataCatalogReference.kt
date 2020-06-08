package com.quantumblack.kedro

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

class KedroDataCatalogReference : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            KedroReferenceProvider()
        )
    }
}

class KedroReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<KedroYamlRef> {
        val isNodeCall = element.parentOfType<PyCallExpression>()
            ?.callee
            ?.name
            ?.contains("node", ignoreCase = true) ?: false // called by `Node` constructor or `node` function

        if (isNodeCall) {
            val references: Array<KedroYamlRef> =  arrayOf(KedroYamlRef(element))
            if (references.isNotEmpty()){
                return references
            }

        }
        return emptyArray()
    }
}

class KedroYamlRef(element: PsiElement) : PsiReferenceBase<PsiElement>(element) {


    private fun getYamlDataSetReference(): List<PsiElement> {
        val dataSetName: String = (element as PyStringLiteralExpression)
            .text.replace(Regex(pattern = "[\"']"),replacement = "") // remove quotes and get the YAML key
        val dataSets: List<KedroDataSet> = KedroDataCatalogManager.getKedroDataSets() // Get YAML references
        val references: List<KedroDataSet> =  dataSets.filter { it.name == dataSetName } // Match the catalog entry
        return references.map{it.reference.node.psi}
    }


    override fun resolve(): PsiElement? {
        return getYamlDataSetReference().firstOrNull()
    }
}
