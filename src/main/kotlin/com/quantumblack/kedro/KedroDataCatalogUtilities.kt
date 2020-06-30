package com.quantumblack.kedro

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

object KedroDataCatalogUtilities {

    private val extensions: List<String> = listOf("yml", "yaml")

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
        return resolvedDataSets.mapNotNull {
            KedroDataSet(
                name = it.key,
                type = it.value["type"].toString(),
                location = psiDataSets[it.key]?.containingFile?.name.toString(),
                psiItem = psiDataSets[it.key] ?: yamlFile.firstChild as YAMLKeyValue,
                layer = it.value["layer"]
            )
        }
    }

    fun isKedroYamlFile(vf: VirtualFile, project: Project): Boolean {
        val isCorrectExtension: Boolean = vf.extension in extensions
        val isLocation: Boolean = arrayOf(project.basePath, "conf", "catalog")
            .filterNotNull()
            .all { directoryComponent: String -> vf.path.contains(directoryComponent) }
        return isCorrectExtension && isLocation


    }
    fun getKedroCatalogYamlFiles(project: Project, psiManager:PsiManager): List<YAMLFile> {

        return extensions
            .asSequence()
            .map { FilenameIndex.getAllFilesByExt(project, it, GlobalSearchScope.projectScope(project)) }
            .flatten<VirtualFile?>()
            .filterNotNull()
            .filter { vf: VirtualFile -> isKedroYamlFile(vf, project) }
            .mapNotNull { psiManager.findFile(it).castSafelyTo<YAMLFile>() }
            .toList()
    }


    fun getDataSets(yamlFiles : List<YAMLFile>): Map<String, KedroDataSet> {
        return yamlFiles
            .map { extractKedroYamlDataSets(it) }
            .flatten()
            .map { it.name to it }
            .toMap()

    }

}