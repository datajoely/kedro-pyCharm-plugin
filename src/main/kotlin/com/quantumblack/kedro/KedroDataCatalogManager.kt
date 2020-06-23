package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.anyDescendantOfType
import com.intellij.psi.util.collectDescendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentsWithSelf
import com.intellij.util.castSafelyTo
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
) {

    /**
     * Comparison of KedroDataSets is only done on name since this is the way it will work within the Kedro context
     *
     * @param other The other object to compare
     * @return True if both dataset names are equal
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KedroDataSet
        if (name != other.name) return false
        return true
    }

    /**
     * This function defines uniqueness by the hash of the name string
     *
     * @return The hashcode of the trimmed string
     */
    override fun hashCode(): Int {
        return name.trim().hashCode()
    }
}

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

            try {
                val dataSets: List<KedroDataSet> = getProjectCatalogYamlFiles(project)
                    .filterNotNull()
                    .map { extractKedroYamlDataSets(it) }
                    .filterNotNull()
                    .toList()
                    .flatten()

                val nonUnique: Map<String, Int> = dataSets.groupingBy { it.name }.eachCount().filterValues { it > 1 }
                return if (nonUnique.isNotEmpty()) {
                    val message = "There are multiple datasets named: ${nonUnique.keys.joinToString(separator = ",")}"
                    log.error(message)
                    WindowManager.getInstance().getStatusBar(project).info = message
                    listOf()
                } else {
                    dataSets
                }
            } catch (e: Exception) {
                log.error("Unable to scan YAML files during index")
                return listOf()
            }
        }

        /**
         * This helper function quickly checks if a dataset name is present within the catalog
         *
         * @param nameToCheck The name to look up
         * @return A boolean True or False if present
         */
        fun isDataCatalogEntry(nameToCheck: String, project: Project): Boolean {
            return try {
                getKedroDataSets(project)
                    .any { it.name == nameToCheck.replace(regex = Regex(pattern = "[\"']"), replacement = "") }
            } catch (e:Exception){
                false
            }

        }

        /**
         * If the project variable is available, this function retrieves all YAML files which fall within the
         * project configuration directory in a typical Kedro project
         *
         * @return This function provides an iterable object containing `VirtualFile` references
         */
        private fun getProjectCatalogYamlFiles(project: Project): Sequence<YAMLFile?> {
            val isWithinProject: List<String> = arrayOf(project.basePath, "conf", "catalog").filterNotNull()
            val extensions: List<String> = listOf("yml", "yaml")
            val virtualFiles = extensions
                .asSequence()
                .map { FilenameIndex.getAllFilesByExt(project, it, GlobalSearchScope.projectScope(project)) }
            val psiManager: PsiManager = PsiManager.getInstance(project)
            return virtualFiles
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
        private fun getVirtualFileAsYaml(psiManager: PsiManager, vf: VirtualFile): YAMLFile? {
            return try {
                psiManager.findFile(vf).castSafelyTo<YAMLFile>()
            } catch (e: Exception) {
                log.error("Recorded issue retrieving catalog ${e.stackTrace}")
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
        private fun extractKedroYamlDataSets(yamlFile: YAMLFile): List<KedroDataSet> {

            // Escape route if files are empty
            if (yamlFile.text.isEmpty()) return listOf()

            // Get top level keys per file
            val psiDataSets: Map<String, YAMLKeyValue> = YAMLUtil.getTopLevelKeys(yamlFile)
                .map { it.keyText to it }
                .toMap()

            // Get anchors placeholders e.g. `_csv`
            val placeHolders: Collection<YAMLKeyValue> = psiDataSets
                .filterKeys { it.startsWith('_') }
                .values

            // Get datasets which are not aliases, but may or many not interpolation
            val dataSetsToResolve: Collection<YAMLKeyValue> = psiDataSets
                .filterKeys { !it.startsWith('_') }
                .values

            // This function accepts the placeholder and dataset collection, placeholder aliases will be interpolated
            val resolvedDataSets: Map<String, Map<String, String>> = resolveYamlPlaceholders(
                potentialDataSets = dataSetsToResolve,
                placeHolders = placeHolders
            )

            // A list of KedroDataSet data class objects are created as a result
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

        /**
         * This function will process YAML Psi elements and prepare them in a format ready to extract as
         * KedroDataSet objects. This function also replaces `anchor` nodes with the relevant `alias` information.
         * This function is only interested in pulling out `type` and `layer` information in addition to the `name`
         * key
         *
         * @param potentialDataSets The datasets to convert, these may include alias nodes which require interpolation
         * @param placeHolders The anchor node information needed to perform any interpolation
         * @param keyDataSetAttributes The dataset attributes which we are interested in pulling out (default value)
         * @return A map including a sub map of key dataset attributes for all YAML datasets discovered in a given file
         */
        private fun resolveYamlPlaceholders(
            potentialDataSets: Collection<YAMLKeyValue>,
            placeHolders: Collection<YAMLKeyValue>,
            keyDataSetAttributes: Array<String> = arrayOf("type", "layer")
        ): Map<String, Map<String, String>> {

            /**
             * This function takes a list of YAML PsiElements and converts this into a native kotlin Map object
             *
             * @param entry The list of PsiElements to process
             * @return A nested map that is native kotlin
             */
            fun dataSetAsMap(entry: Map.Entry<String, List<YAMLPsiElement>>): Map<String, String> {
                return entry.value
                    .filter { it.name in keyDataSetAttributes }
                    .mapNotNull { it.castSafelyTo<YAMLKeyValueImpl>() }
                    .map { it.keyText to it.valueText }
                    .toMap()
            }

            /**
             * This function processes datasets known to not include any alias nodes
             *
             * @param keyValueList The list of YamlKeyValue items to process into a nested kotlin Map object
             * @return A nested map object limited to relevant keys
             */
            fun getDataSet(keyValueList: Collection<YAMLKeyValue>): Map<String, Map<String, String>> {
                val collection: Map<String, List<YAMLPsiElement>> = keyValueList
                    .map {
                        it.keyText to (it.value?.yamlElements ?: listOf())
                            .filterNotNull()
                            .filterNot { e: YAMLPsiElement -> e.elementType == YAMLElementTypes.ANCHOR_NODE }
                    }.toMap()
                return collection
                    .mapValues { dataSetAsMap(it) }
                    .filterValues { it.isNotEmpty() }
            }

            /**
             * This function accepts datasets known to have alias and interpolates the relevant placeholders and
             * constructs a full realised dataset
             *
             * @param keyValueList The list YamlKeyValues items to process into a nested Map Object (with interpolation)
             * @param lookup The dictionary of aliases to lookup
             * @return A nested map object limited to relevant keys (with interpolation appplied)
             */
            fun interpolateDataSet(
                keyValueList: List<YAMLKeyValue>,
                lookup: Map<String, Map<String, String>>
            ): Map<String, Map<String, String>> {

                val collection: Map<String, List<YAMLPsiElement>> = keyValueList
                    .map { it.keyText to (it.value?.yamlElements ?: listOf()).filterNotNull() }.toMap()

                val aliases: Map<String, Map<String, String>> =
                    keyValueList.map { it.collectDescendantsOfType<YAMLAliasImpl>() }
                        .flatten()
                        .map {
                            // Bubble up PsiTree of type YAMLKeyValueImpl and retrieve the top level dataset name
                            val dataSetName: String = (
                                    it.parentsWithSelf
                                        .mapNotNull { e: PsiElement -> e.castSafelyTo<YAMLKeyValueImpl>()?.keyText }
                                        .filter { k: String -> k in collection.keys }
                                        .last()
                                    )
                            // Retrieve _alias from lookup dictionary of placeholders e.g. _csv -> csv
                            val anchorAttributes: Map<String, String> = lookup['_' + it.aliasName] ?: mapOf()
                            dataSetName to anchorAttributes
                        }.toMap()

                // For each yaml object, attempt to convert to map and then merge in placeholder dictionary accordingly
                return collection
                    .mapValues { dataSetAsMap(it) }
                    .map { it.key to it.value.plus(aliases[it.key] ?: mapOf()) }
                    .toMap()
            }

            // Convert placeholder YAML objects to Kotlin map
            val placeHolderMap: Map<String, Map<String, String>> = getDataSet(placeHolders)

            // Get two sets of datasets (1) The documents which include an alias child (2) The documents which don't
            val dataSetCategories: Pair<List<YAMLKeyValue>, List<YAMLKeyValue>> = potentialDataSets
                .partition { it.anyDescendantOfType<YAMLAliasImpl>() }

            // Extract the datasets which are known to not include and aliases
            val dataSetsExplicitExtracted: Map<String, Map<String, String>> = getDataSet(dataSetCategories.second)
                .map { it.key to it.value }
                .toMap()

            // Extract the and interpolate datasets which are known to include aliases
            val dataSetsWithAliasesExtracted: Map<String, Map<String, String>> = interpolateDataSet(
                dataSetCategories.first,
                placeHolderMap
            )

            // Combine explicit and interpolated datasets as one map
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
        fun get(name: String, project: Project): KedroDataSet? =
            try {
                getKedroDataSets(project).firstOrNull { isCatalogName(it, name) }
            } catch (e:Exception){
                log.error("Unable to retrieve dataset during index")
                null
            }

    }
}
