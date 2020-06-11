package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.jetbrains.extensions.getQName
import org.jetbrains.annotations.NotNull
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLMappingImpl
import javax.swing.Icon

/**
 * This data class stores information relevant to each Kedro dataset entry
 *
 * @property name The key of the dataset in the YAML file
 * @property type The type of the dataset
 * @property location The path of the YAML file
 * @property psiItem The PSI element referring to the dataset top level key
 * @property layer The layer of dataset (if available)
 */
data class KedroDataSet(
    val name: String,
    val type: String,
    val location: String,
    val psiItem: YAMLKeyValue,
    val layer: String? = null
) {
    val formattedLocation: String = if (this.location.isNotEmpty()) " (${this.location})" else this.location
}

// Provide Singleton object for the `KedroDataCatalogManager`
object KedroDataCatalogManager {
    private val icon: Icon = IconLoader.getIcon("/icons/pluginIcon_dark.svg")
    private val project: Project? = PyCharmProjectManager.project

    /**
     * This function collect a list of `KedroDataSets` for all catalog YAML files available in the project
     *
     * @return A list of `KedroDataSets` for all catalog YAML files available in the project
     */
    fun getKedroDataSets(): List<KedroDataSet> {

        if (project != null) {
            val projectYamlFiles: Sequence<VirtualFile> = getProjectCatalogYamlFiles()
            return projectYamlFiles
                .map { PsiManager.getInstance(project).findFile(it) as? YAMLFile }
                .toList()
                .filterNotNull()
                .map { extractKedroYamlDataSet(it) }
                .flatten()
        }

        return listOf()
    }

    /**
     * This helper function quickly checks if a dataset name is present within the catalog
     *
     * @param name The name to look up
     * @return A boolean True or False if present
     */
    fun dataSetInCatalog(name: String): Boolean {
        return this.getKedroDataSets().any { it.name == name }
    }

    /**
     * If the project variable is available, this function retrieves all YAML files which fall within the
     * project configuration directory in a typical Kedro project
     *
     * @return This function provides an iterable object containing `VirtualFile` references
     */
    private fun getProjectCatalogYamlFiles(): Sequence<VirtualFile> {
        if (project != null) {

            val isWithinProject: List<String> = arrayOf(project.basePath, "conf", "catalog").filterNotNull()
            val extensions: List<String> = listOf("yml", "yaml")

            return extensions
                .asSequence()
                .map { FilenameIndex.getAllFilesByExt(project, it, PyCharmProjectManager.getYamlScope(project)) }
                .flatten<VirtualFile?>()
                .filterNotNull()
                .filter { vf: VirtualFile -> isWithinProject.all { vf.path.contains(it) } }
        }
        return sequenceOf()
    }

    /**
     * This function takes a `VirtualFile` which has been successfully cast to a `YamlFile` object and
     * extracts the information relevant to constructing an instance of a `KedroDataSet` data class object.
     * Any badly formed YAML catalog files will throw the `ClassCastException` and return zero references
     *
     * @param yml The `YAMLFile` object to process
     * @return The list of `KedroDataSet` data class objects to work with
     */
    private fun extractKedroYamlDataSet(yml: YAMLFile): List<KedroDataSet> {
        try {
            val dataSets: @NotNull MutableCollection<YAMLKeyValue> = YAMLUtil.getTopLevelKeys(yml)
            return dataSets.filter {
                !it.keyText.startsWith('_')
            }.map(transform = {
                val dataSet: YAMLMappingImpl = it.value as YAMLMappingImpl
                val layer: String? = dataSet
                    .keyValues
                    .firstOrNull { l: YAMLKeyValue -> l.keyText.startsWith("layer") }
                    ?.valueText
                KedroDataSet(
                    name = it.keyText,
                    type = dataSet.getKeyValueByKey("type")?.valueText.toString(),
                    layer = layer, // Return empty string if layer is not available
                    location = yml.containingDirectory.getQName().toString(),
                    psiItem = it
                )
            })
        } catch (e: java.lang.ClassCastException) {
            // If YAML is broken skip and return empty collection
            return listOf()
        }
    }

    /**
     * This function creates an autocomplete suggestion given a KedroDataSet data class object
     *
     * @param dataSet The dataset object to suggest
     * @return A lookup element for the given dataset
     */
    private fun createDataSetSuggestion(dataSet: KedroDataSet): LookupElementBuilder {

        val tailText: String = if (dataSet.layer != null) {
            "($dataSet.layer) ${dataSet.formattedLocation}"
        } else {
            dataSet.formattedLocation
        }

        return LookupElementBuilder
            .create("\"${dataSet.name}\"")
            .bold()
            .withPresentableText(dataSet.name)
            .withCaseSensitivity(false)
            .withIcon(icon)
            .withTypeText(dataSet.type.split(delimiters = *charArrayOf('.')).last())
            .withLookupString(dataSet.name)
            .withLookupStrings(arrayListOf(dataSet.layer ?: "kedro", "kedro"))
            .withTailText(tailText)
    }

    /**
     * This function creates autocomplete suggestions for all datasets available
     *
     * @return A list of autocomplete suggestions
     */
    fun getKedroDataSetSuggestions(): List<LookupElementBuilder> {
        return getKedroDataSets().map { createDataSetSuggestion(it) }
    }
}
