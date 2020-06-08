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

data class KedroDataSet(
    val name: String,
    val type: String,
    val location: String,
    val reference: YAMLKeyValue, // this is used to map references
    val layer: String = ""
) {
    val formattedLocation: String
        get() {
            return if (this.location.isNotEmpty()) " (${this.location})" else this.location
        }
}

object KedroDataCatalogManager {
    private val icon: Icon = IconLoader.getIcon("/icons/pluginIcon_dark.svg")
    private val project: Project? = PyCharmProjectManager.project

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

    private fun getProjectCatalogYamlFiles(): Sequence<VirtualFile> {
        if (project != null) {

            val isWithinProject: List<String> = arrayOf(project.basePath, "conf", "catalog").filterNotNull()
            val extensions: List<String> = listOf("yml", "yaml")

            return extensions
                .asSequence()
                .map { FilenameIndex.getAllFilesByExt(project, it, PyCharmProjectManager.getScope(project)) }
                .flatten<VirtualFile?>()
                .filterNotNull()
                .filter { vf: VirtualFile -> isWithinProject.all { vf.path.contains(it) } }
        }
        return sequenceOf()
    }

    private fun extractKedroYamlDataSet(yml: YAMLFile): List<KedroDataSet> {
        try {
            val dataSets: @NotNull MutableCollection<YAMLKeyValue> = YAMLUtil.getTopLevelKeys(yml)
            return dataSets.filter {
                !it.keyText.startsWith('_')
            }.map {
                val dataSet: YAMLMappingImpl = it.value as YAMLMappingImpl
                val layer: YAMLKeyValue? =
                    (it.value as YAMLMappingImpl).keyValues.firstOrNull { a ->
                        a.keyText.startsWith("layer")
                    }
                KedroDataSet(
                    name = it.keyText,
                    type = dataSet.getKeyValueByKey("type")?.valueText.toString(),
                    layer = layer?.valueText ?: "", // Return empty string if layer is not available
                    location = yml.containingDirectory.getQName().toString(),
                    reference = it
                )
            }
        } catch (e: java.lang.ClassCastException) {
            // If YAML is broken skip and return empty collection
            return listOf()
        }
    }

    private fun createDataSetSuggestion(dataSet: KedroDataSet): LookupElementBuilder {
        return LookupElementBuilder.create("\"${dataSet.name}\"")
            .withPresentableText(dataSet.name)
            .bold()
            .withCaseSensitivity(false)
            .withIcon(this.icon)
            .withTypeText(dataSet.type)
            .withTypeText(dataSet.type.split(delimiters = *charArrayOf('.')).last())
            .withLookupString(dataSet.name)
            .withLookupString("kedro")
            .withTailText(dataSet.formattedLocation)
    }

    fun getKedroDataSetSuggestions(): List<LookupElementBuilder> {
        return getKedroDataSets().map { createDataSetSuggestion(it) }
    }
}
