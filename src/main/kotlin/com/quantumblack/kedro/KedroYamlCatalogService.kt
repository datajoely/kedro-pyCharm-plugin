package com.quantumblack.kedro

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "KedroYamlCatalogService", storages = [Storage("kedroCatalogStore.xml")])
class KedroYamlCatalogService : PersistentStateComponent<KedroYamlCatalogService>{
    var dataSets: MutableList<KedroDataSet> = mutableListOf()

    override fun getState(): KedroYamlCatalogService {
        return this
    }

    override fun loadState(state: KedroYamlCatalogService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {

        fun getInstance(project: Project?): KedroYamlCatalogService {
            return ServiceManager.getService(project!!, KedroYamlCatalogService::class.java)
        }
    }


}