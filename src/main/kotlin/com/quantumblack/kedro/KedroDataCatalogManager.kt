package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nullable
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.impl.YAMLAliasImpl
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
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
)

// Provide Singleton object for the `KedroDataCatalogManager`
class KedroDataCatalogManager {

    companion object {

        /**
         * This function retrieves the Icon file at runtime
         *
         * @return
         */
        private fun getIcon(): Icon = IconLoader.getIcon("/icons/pluginIcon_dark.svg")
        private val log: Logger = Logger.getInstance("kedro")


        /**
         * This function collect a list of `KedroDataSets` for all catalog YAML files available in the project
         *
         * @return A list of `KedroDataSets` for all catalog YAML files available in the project
         */
        fun getKedroDataSets(project: Project): List<KedroDataSet> {

            val projectYamlFiles: Sequence<YAMLFile?> = getProjectCatalogYamlFiles(project)
            return projectYamlFiles
                .filterNotNull()
                .map { extractKedroYamlDataSet(it) }
                .filterNotNull()
                .toList()
                .flatten()

        }

        /**
         * This helper function quickly checks if a dataset name is present within the catalog
         *
         * @param nameToCheck The name to look up
         * @return A boolean True or False if present
         */
        fun isDataCatalogEntry(nameToCheck: String, project: Project): Boolean {
            return this.getKedroDataSets(project)
                .any { it.name == nameToCheck.replace(regex = Regex(pattern = "[\"']"), replacement = "") }
        }

        /**
         * If the project variable is available, this function retrieves all YAML files which fall within the
         * project configuration directory in a typical Kedro project
         *
         * @return This function provides an iterable object containing `VirtualFile` references
         */
        private fun getProjectCatalogYamlFiles(project: Project): Sequence<@Nullable YAMLFile?> {
            val isWithinProject: List<String> = arrayOf(project.basePath, "conf", "catalog").filterNotNull()
            val extensions: List<String> = listOf("yml", "yaml")

            val psiManager: PsiManager = PsiManager.getInstance(project)
            return extensions
                .asSequence()
                .map { FilenameIndex.getAllFilesByExt(project, it, GlobalSearchScope.projectScope(project)) }
                .flatten<VirtualFile?>()
                .filterNotNull()
                .filter { vf: VirtualFile -> isWithinProject.all { vf.path.contains(it) } }
                .mapNotNull { getVirtualFileAsYaml(psiManager, it) }

        }

        /**
         * This function safely retrieves a virtual file reference from the file system
         * (an exception is handled if this method is called during reindexing)
         *
         * @param psiManager The psiManager instantiated within the calling method
         * @param vf The file to load as a YamlFile object
         * @return YamlFile object if successful, null if not
         */
        private fun getVirtualFileAsYaml(
            psiManager: PsiManager, vf: VirtualFile
        ): YAMLFile? {
            return try {
                psiManager.findFile(vf).castSafelyTo<YAMLFile>()
            } catch (e: Exception) {
                log.error("Recorded issue retrieving catalog ${e.cause}")
                return null

            }
        }

        /**
         * This function takes a `VirtualFile` which has been successfully cast to a `YamlFile` object and
         * extracts the information relevant to constructing an instance of a `KedroDataSet` data class object.
         *
         * @param yamlFile The `YAMLFile` object to process
         * @return The list of `KedroDataSet` data class objects to work with
         */
        private fun extractKedroYamlDataSet(yamlFile: YAMLFile): List<KedroDataSet> {

            if (yamlFile.text.isEmpty()) return listOf() //escape route
            val psiDataSets: Map<String, YAMLKeyValue> = YAMLUtil.getTopLevelKeys(yamlFile).map { it.keyText to it }.toMap()
            val placeHolders: Collection<YAMLKeyValue> = psiDataSets.filterKeys { it.startsWith('_') }.values
            val potentialDataSets: Collection<YAMLKeyValue> = psiDataSets.filterKeys { !it.startsWith('_') }.values
            val resolvedDataSets: Map<String, Map<String, String>> = resolveYamlPlaceholders(placeHolders, potentialDataSets)

            return resolvedDataSets.map {
                KedroDataSet(
                    name = it.key,
                    type = it.value["type"].toString(),
                    location = psiDataSets[it.key]?.containingFile?.containingDirectory.toString(),
                    psiItem = psiDataSets[it.key] ?: yamlFile.firstChild as YAMLKeyValue,
                    layer = it.value["layer"]
                )
            }

        }

        private fun resolveYamlPlaceholders(
            placeHolders: Collection<YAMLKeyValue>,
            potentialDataSets: Collection<YAMLKeyValue>
        ): Map<String, Map<String, String>> {

            fun extractDataSetContent(container: Collection<YAMLKeyValue>): Map<String, Map<String, String>> {
                val containerCollection: Map<String, List<YAMLPsiElement>> = container.map {
                    it.keyText to (it.value?.yamlElements ?: listOf())
                        .filterNotNull()
                        .filterNot { e: YAMLPsiElement -> e.elementType == YAMLElementTypes.ANCHOR_NODE }
                }.toMap()
                val nestedMap: Map<String, Map<String, String>> = containerCollection.mapValues {
                    it.value
                        .filter { kv: YAMLPsiElement -> (kv.name in arrayOf("type", "layer")) }
                        .mapNotNull { element -> element.castSafelyTo<YAMLKeyValueImpl>() }
                        .map { element -> element.keyText to element.valueText }
                        .toMap()
                }

                return nestedMap.filterValues { it.isNotEmpty() }
            }

            fun interpolateDataSetContent(
                container: List<YAMLKeyValue>,
                lookup: Map<String, Map<String, String>>
            ): Map<String, Map<String, String>> {
                val containerCollection: Map<String, List<YAMLPsiElement>> = container.map {
                    it.keyText to (it.value?.yamlElements ?: listOf()).filterNotNull()
                }.toMap()

                val aliases: Map<String, Map<String, String>?> =
                    container.map { it.collectDescendantsOfType<YAMLAliasImpl>() }
                        .flatten()
                        .map {
                            it.parentsWithSelf
                                .mapNotNull { e: PsiElement -> e.castSafelyTo<YAMLKeyValueImpl>()?.keyText }
                                .filter { k -> k in containerCollection.keys }.last() to lookup['_' + it.aliasName]
                        }.toMap()

                val nonTemplateCollections: Map<String, Map<String, String>> = containerCollection.mapValues {
                    it.value
                        .filter { kv: YAMLPsiElement -> (kv.name in arrayOf("type", "layer")) }
                        .mapNotNull { element -> element.castSafelyTo<YAMLKeyValueImpl>() }
                        .map { element -> element.keyText to element.valueText }
                        .toMap()
                }

                return nonTemplateCollections.map {
                    it.key to it.value.plus(aliases[it.key] ?: mapOf())
                }.toMap()


            }

            val placeHolderMap: Map<String, Map<String, String>> = extractDataSetContent(placeHolders)

            val dataSetCategories: Pair<List<YAMLKeyValue>, List<YAMLKeyValue>> = potentialDataSets
                .partition { it.anyDescendantOfType<YAMLAliasImpl>() }

            val dataSetsExplicitExtracted: Map<String, Map<String, String>> =
                extractDataSetContent(dataSetCategories.second)
                    .map { it.key to it.value }.toMap()
            val dataSetsWithAliasesExtracted: Map<String, Map<String, String>> =
                interpolateDataSetContent(dataSetCategories.first, placeHolderMap)

            return dataSetsExplicitExtracted.plus(dataSetsWithAliasesExtracted.entries.map { it.toPair() })

        }


        /**
         * This function creates autocomplete suggestions for all datasets available
         *
         * @return A list of autocomplete suggestions
         */
        fun getKedroDataSetSuggestions(project: Project): List<LookupElementBuilder> {

            /**
             * This function creates an autocomplete suggestion given a KedroDataSet data class object
             *
             * @param dataSet The dataset object to suggest
             * @return A lookup element for the given dataset
             */
            fun createDataSetSuggestion(dataSet: KedroDataSet): LookupElementBuilder {

                val layer: String? = if (!dataSet.layer.isNullOrBlank()) " (${dataSet.layer})" else ""
                return LookupElementBuilder
                    .create("\"${dataSet.name}\"")
                    .bold()
                    .withPresentableText(dataSet.name)
                    .withCaseSensitivity(false)
                    .withIcon(getIcon())
                    .withTypeText(dataSet.type.split(delimiters = *charArrayOf('.')).last() + layer)
                    .withLookupString(dataSet.name)
                    .withLookupStrings(arrayListOf(dataSet.layer ?: "kedro", "kedro"))
            }


            return getKedroDataSets(project).map { createDataSetSuggestion(it) }

        }

        /**
         * This function will remove the the quotes that are present in the catalog dataset
         * string literal reference
         *
         * @param dataSet The dataSet object to check
         * @param name The name to look for
         * @return True if present
         */
        private fun isCatalogName(dataSet: KedroDataSet, name: String): Boolean =
            dataSet.name == name
                .replace(regex = Regex(pattern = "[\"']"), replacement = "")

        /**
         * This helper function retrieves a dataset object from the catalog
         *
         * @param name The name of the object to retrieve
         * @param project The project to scan
         */
        fun get(name: String, project: Project): KedroDataSet =
            getKedroDataSets(project).first { isCatalogName(it, name) }
    }
}