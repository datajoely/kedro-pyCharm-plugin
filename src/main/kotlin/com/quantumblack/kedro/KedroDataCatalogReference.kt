package com.quantumblack.kedro

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.castSafelyTo
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * This class provides references to the PyCharm lookup
 */
class KedroDataCatalogReference : PsiReferenceContributor() {
    /**
     * Overriding this function scans elements which are PyStringLiteralExpressions and attempts
     * to add references to Kedro data catalog entries in the appropriate places
     *
     * @param registrar The custom reference provider
     */
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
            KedroReferenceProvider()
        )
    }
}

/**
 * This class creates a reference provider which detects if the reference is Kedro catalog entry
 * and returns the appropriate YAML KeyValue
 */
class KedroReferenceProvider : PsiReferenceProvider() {
    /**
     * Thus function analyses the element in question and provides an array of references to
     * the various YAML KeyValue that can map to each string
     *
     * @param element The element being scanned by the IDE
     * @param context The processing context at hand
     * @return An array of 0 or 1 `KedroYamlReference` objects that map to the
     * `PyStringLiteralExpression` being scanned
     */
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<KedroYamlReference> {

        if (KedroPsiUtilities.isKedroNodeCatalogParam(element, autoCompletePotential = false)) {
            val references: Array<KedroYamlReference> = arrayOf()
            if (references.isNotEmpty()) {
                return references
            }
        }
        return emptyArray()
    }
}

/**
 * This class retrieves the the Kedro data catalog items and provides a mechanism for resolving them
 * given the PSI element provided
 *
 * @constructor Is inherited from the PsIReferenceBase class
 * @param element The element to match against the Kedro data catalog datasets available
 */
class KedroYamlReference(element: PsiElement) : PsiReferenceBase<PsiElement>(element) {

    /**
     * This function retrieves the string contents of the `PyStringLiteralExpression` and compares
     * it to the list of data set names provided by the `KedroDataCatalogManager` singleton
     *
     * @return The PSI element of the `KedroDataSet` object in question
     */
    private fun getYamlDataSetReference(): PsiElement? {
        return try {
            val dataSetName: String? = element.castSafelyTo<PyStringLiteralExpression>()?.text
            val service: KedroYamlCatalogService = KedroYamlCatalogService.getInstance(project = element.project)
            val dataSet: KedroDataSet? = service.getDataSetByName(dataSetName = dataSetName ?: "?")
            if (dataSet != null) { dataSet.psiItem?.node?.psi }
            else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * This function resolves the `PyStringLiteralExpression` to the relevant YAML KeyValue
     *
     * @return The YAML KeyValue reference or null
     */
    override fun resolve(): PsiElement? {
        return getYamlDataSetReference()
    }
}
