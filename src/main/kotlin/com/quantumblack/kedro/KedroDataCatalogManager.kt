package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.castSafelyTo
import com.jetbrains.extensions.getQName
import com.jetbrains.rd.util.first
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLMappingImpl
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
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
        private val yaml = Yaml()
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
                .toList()
                .flatten()

        }

        /**
         * This helper function quickly checks if a dataset name is present within the catalog
         *
         * @param dataSetName The name to look up
         * @return A boolean True or False if present
         */
        fun isDataCatalogEntry(dataSetName: String, project: Project): Boolean {
            return this.getKedroDataSets(project).any { it.name == dataSetName }
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
                .map { getVirtualFileAsYaml(psiManager, it) }

        }

        /**
         * This function parses the YAML file natively. Originally it was planned to use Intellij libraries
         * exclusively, however it turned out that the PsiFile parsed did not resolve YAML Aliases. Since
         * this is a key part of how Kedro catalog files are used, it was decided that each YAML file is parsed
         * twice: (1) For content in this context (2) Again to extract Psi references to the correct key values
         *
         * @param reader The buffered reader exposed by the VirtualFileSystem to be parsed
         * @return
         */
        private fun parseYamlFileNatively(reader: BufferedReader): Map<String, Map<String, String>> {

            // Read Yaml file via reader object into kotlin. Snake Yaml is used since it
            // resolves aliases and anchors
            val snakeYamlObject: Map<*, *>? =
                try {
                    yaml.loadAll(reader)
                        .toList()
                        .first()
                        .castSafelyTo<Map<String, Map<String, *>>>()
                } catch (e: Exception) {
                    log.error("Unable to parse yaml file with SnakeYaml ${e.stackTrace}")
                    return mutableMapOf()
                }

            fun asStringMap(dict: Map.Entry<Any?, Any?>): Map<String, Map<String, String>> {

                val workingMap: Map<String, String> = // Attempt to cast to String map
                    dict.value.castSafelyTo<MutableMap<String, String>>() ?: mutableMapOf()

                // Escape if YAML object does not contains "type" inner key
                if ("type" !in workingMap.keys) return mutableMapOf()

                // Limit inner values to relevant inner keys and copy name down a level
                val nonNullMap: Map<String, String> = workingMap
                    .plus(Pair("name", dict.key))
                    .filterKeys { it in arrayOf("name", "type", "layer") }
                    .castSafelyTo<Map<String, String>>() ?: mutableMapOf()

                // Return a new nested map
                return mapOf(dict.key.toString() to nonNullMap)
            }

            // Create list per nested map
            val listOfNestedMaps: List<Map<String, Map<String, String>>> = snakeYamlObject
                ?.filter { !it.key.toString().startsWith('_') }
                ?.map { asStringMap(it) }
                ?.dropWhile { it.isEmpty() } ?: emptyList()

            // Create nested map
            // Todo: See if possible to do without mutation
            val outputMap: MutableMap<String, Map<String, String>> = mutableMapOf()
            listOfNestedMaps.forEach {
                val pair = it.first().toPair()
                outputMap[pair.first] = pair.second
            }

            return outputMap


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
            psiManager: PsiManager,
            vf: VirtualFile
        ): YAMLFile? {
            return try {
                parseYamlFileNatively(vf.inputStream.bufferedReader())
                psiManager.findFile(vf).castSafelyTo<YAMLFile>()
            } catch (e: Exception) {
                log.error("Recorded issue retrieving catalog ${e.stackTrace}")
                return null

            }
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
                        layer = layer,
                        location = yml.containingDirectory.getQName().toString(),
                        psiItem = it
                    )
                })
            } catch (e: java.lang.ClassCastException) {
                // If YAML is broken skip and return empty collection
                log.error("Unable to create KedroDataSet ${e.stackTrace}")
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

            val layer: String? = dataSet.layer
            return LookupElementBuilder
                .create("\"${dataSet.name}\"")
                .bold()
                .withPresentableText(dataSet.name)
                .withCaseSensitivity(false)
                .withIcon(getIcon())
                .withTypeText(dataSet.type.split(delimiters = *charArrayOf('.')).last() + " ($layer)")
                .withLookupString(dataSet.name)
                .withLookupStrings(arrayListOf(dataSet.layer ?: "kedro", "kedro"))
        }

        /**
         * This function creates autocomplete suggestions for all datasets available
         *
         * @return A list of autocomplete suggestions
         */
        fun getKedroDataSetSuggestions(project: Project): List<LookupElementBuilder> {
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