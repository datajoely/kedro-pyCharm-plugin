package com.quantumblack.kedro

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.diagnostic.Logger

@State(name = "KedroYamlCatalogService", storages = [Storage("kedroCatalogStore.xml")])
class KedroYamlCatalogService : PersistentStateComponent<KedroYamlCatalogService> {
    private var dataSets: MutableList<KedroDataSet> = java.util.Collections.synchronizedList(mutableListOf())
    private val log: Logger = Logger.getInstance("kedro")


    override fun getState(): KedroYamlCatalogService {
        return this
    }

    override fun loadState(state: KedroYamlCatalogService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun addOrReplaceDataSet(dataSet: KedroDataSet) {

        if (dataSet.isNotNull()) {
            if (this.dataSets.contains(dataSet)) {
                removeDataSet(dataSet)
            }
            this.dataSets.add(dataSet)
            log.info("adding ${dataSet.name}")
        }

    }

    fun removeDataSet(dataSet: KedroDataSet) {
        log.info("removing ${dataSet.name}")
        this.dataSets.remove(dataSet)
    }

    fun getAllDataSets(): MutableList<KedroDataSet> {
        return this.dataSets
    }

    fun getDataSetsByYaml(yamlName: String): List<KedroDataSet> {
        log.info("Get by yaml $yamlName")
        return this.dataSets.filter { it.location == yamlName }
    }

    fun getDataSetByName(dataSetName: String): KedroDataSet? {
        val result: KedroDataSet? =
            getAllDataSets().firstOrNull { dataset: KedroDataSet -> dataset.nameEqual(dataSetName) }
        log.info("Get dataset directly: $dataSetName (${result?.type})")
        return result
    }

    companion object {
        fun getInstance(project: Project?): KedroYamlCatalogService {
            return ServiceManager.getService(project!!, KedroYamlCatalogService::class.java)
        }

    }


}