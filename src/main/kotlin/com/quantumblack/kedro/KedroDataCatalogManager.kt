package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.extensions.getQName
import org.jetbrains.annotations.NotNull
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLMappingImpl
import javax.swing.Icon

data class KedroDataSet(
    val name: String,
    val type: String,
    val location: String,
    val reference: YAMLKeyValue, // this.reference.navigateTo() will jump to reference
    val layer: String = ""
) {
    val formattedLocation: String
        get() {
            return if (this.location.isNotEmpty()) " (${this.location})" else this.location
        }
}

class KedroDataCatalogManager(
    private val project: Project,
    private val icon: Icon
) {

    private fun getKedroDataSets(): List<KedroDataSet> {

        val scope: GlobalSearchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.allScope(project),
            YAMLFileType.YML
        )
        val catalogChecks: List<String> = arrayOf(project.basePath, "conf", "catalog").filterNotNull()
        val extensions: List<String> = listOf("yml", "yaml")

        return extensions
            .asSequence()
            .map { FilenameIndex.getAllFilesByExt(project, it, scope) }
            .flatten<VirtualFile?>()
            .filterNotNull()
            .filter { vf: VirtualFile -> catalogChecks.all { vf.path.contains(it) } }
            .map { PsiManager.getInstance(project).findFile(it) as? YAMLFile }
            .toList()
            .filterNotNull()
            .map { extractKedroYamlDataSet(it) }
            .flatten()
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
            .withLookupString(dataSet.name)
            .withLookupString(dataSet.type)
            .withTailText(dataSet.formattedLocation)
    }

    fun getKedroDataSetSuggestions(): List<LookupElementBuilder> {
        return getKedroDataSets().map { createDataSetSuggestion(it) }
    }
}
