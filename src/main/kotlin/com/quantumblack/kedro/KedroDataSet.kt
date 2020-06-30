package com.quantumblack.kedro

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.IconLoader
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.io.Serializable

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
    val name: String = "UNKNOWN_NAME",
    val type: String = "UNKNOWN_TYPE",
    val location: String = "UNKNOWN_LOCATION",
    val psiItem: YAMLKeyValue? = null,
    val layer: String? = null
) : Serializable {

    fun nameEqual(comparison:String): Boolean {
        val cleanComparison: String = comparison.replace(regex = Regex(pattern = "[\"']"), replacement = "")
        return cleanComparison == this.name

    }

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

    fun isNull(): Boolean {
       return name.trim() == "UNKNOWN_NAME"
    }

    fun isNotNull(): Boolean{
        return !isNull()
    }

    fun getAutoCompleteSuggestion(): LookupElementBuilder? {
        val layer: String? = if (!this.layer.isNullOrBlank()) " (${this.layer})" else ""

        return LookupElementBuilder
            .create("\"${this.name}\"")
            .bold()
            .withPresentableText(this.name)
            .withCaseSensitivity(true)
            .withIcon(IconLoader.getIcon("/icons/pluginIcon_dark.svg"))
            .withTypeText(this.type.split(delimiters = *charArrayOf('.')).last() + layer)
            .withLookupString(this.name)
            .withLookupStrings(arrayListOf(this.layer ?: "kedro", "kedro"))

    }
}