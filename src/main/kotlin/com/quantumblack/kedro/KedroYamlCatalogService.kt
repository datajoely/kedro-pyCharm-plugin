package com.quantumblack.kedro

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "KedroYamlCatalogService", storages = [Storage("kedroCatalogStore.xml")])
class KedroYamlCatalogService : PersistentStateComponent<KedroYamlCatalogService> {
    private var dataSets: MutableList<KedroDataSet> = java.util.Collections.synchronizedList(mutableListOf())

    override fun getState(): KedroYamlCatalogService {
        return this
    }

    override fun loadState(state: KedroYamlCatalogService) {
        XmlSerializerUtil.copyBean(state, this)
    }



    fun addOrReplaceDataSet(dataSet: KedroDataSet) {
        if (dataSet.isNotNull()) {
            if (this.dataSets.contains(dataSet)) removeDataSet(dataSet)
            this.dataSets.add(dataSet)
        }

    }

    fun removeDataSet(dataSet: KedroDataSet) {
        this.dataSets.remove(dataSet)
    }

    fun getAllDataSets(): MutableList<KedroDataSet> {
        return this.dataSets
    }

    fun getDataSetsByYaml(yamlName: String): List<KedroDataSet> {
        return this.dataSets.filter { it.location == yamlName }
    }

    fun getDataSetByName(dataSetName:String) : KedroDataSet? {
        return getAllDataSets().firstOrNull { dataset: KedroDataSet -> dataset.nameEqual(dataSetName) }
    }

    companion object {

        fun getInstance(project: Project?): KedroYamlCatalogService {
            return ServiceManager.getService(project!!, KedroYamlCatalogService::class.java)
        }

    }


}