package com.quantumblack.kedro

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.NoAccessDuringPsiEvents
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.castSafelyTo
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

class KedroCatalogManager : StartupActivity {

    private fun initialiseCatalogLoader(project: Project) {
        val dataCatalogService: KedroYamlCatalogService = KedroYamlCatalogService.getInstance(project)
        val psiManager: PsiManager = PsiManager.getInstance(project)

        invokeAfterPsiEvents {
            val yamlFiles: List<YAMLFile> = KedroDataCatalogUtilities.getKedroCatalogItems(project = project)
            dataCatalogService.dataSets = KedroDataCatalogUtilities.getKedroDataSets(yamlFiles = yamlFiles)
        }

        VirtualFileManager.getInstance().addAsyncFileListener(

            { events: MutableList<out VFileEvent> ->
                object : AsyncFileListener.ChangeApplier {

                    override fun afterVfsChange() {
                        if (project.isDisposed) return
                        try {
                            val projectFiles: Sequence<VirtualFile> = events
                                .asSequence()
                                .filter {
                                    it is VFileContentChangeEvent ||
                                            it is VFileMoveEvent ||
                                            it is VFileCopyEvent ||
                                            it is VFileCreateEvent ||
                                            it is VFileDeleteEvent

                                }.mapNotNull { it.file }
                                .filter {
                                    try {
                                        ProjectFileIndex.getInstance(project).isInContent(it)
                                    } catch (e: AlreadyDisposedException) {
                                        false
                                    }
                                }
                            if (projectFiles.count() == 0) return
                            invokeAfterPsiEvents {
                                val changedYamlFiles: List<YAMLFile> = projectFiles
                                    .asSequence()
                                    .filter { KedroDataCatalogUtilities.isKedroYamlFile(it, project) }
                                    .mapNotNull { psiManager.findFile(it) }
                                    .mapNotNull { it.castSafelyTo<YAMLFile>() }
                                    .toList()

                                updateDataSets(
                                    changedYamlFiles = changedYamlFiles,
                                    service = dataCatalogService
                                )
                                removeOldDataSets(
                                    changedYamlFiles = changedYamlFiles,
                                    service = dataCatalogService
                                )

                            }
                        } catch (e: AlreadyDisposedException) {
                        }
                    }
                }
            },
            {}
        )
    }

    private fun updateDataSets(changedYamlFiles: List<YAMLFile>, service: KedroYamlCatalogService) {
        invokeAfterPsiEvents {
            val kedroDataSets: MutableMap<String, KedroDataSet> =
                KedroDataCatalogUtilities.getKedroDataSets(changedYamlFiles)

            //todo change to mutablelist since the iterator provides a way of adding
            val iterator: MutableIterator<MutableMap.MutableEntry<String, KedroDataSet>> = service.dataSets.iterator()
            while (iterator.hasNext()) {
                val item: MutableMap.MutableEntry<String, KedroDataSet> = iterator.next()
                service.dataSets[item.key] = item.value
            }
        }
    }

    private fun removeOldDataSets(changedYamlFiles: List<YAMLFile>, service: KedroYamlCatalogService) {
        invokeAfterPsiEvents {
            val yamlKeyMap: Map<String, List<String>> = changedYamlFiles
                .map {
                    it.containingFile.name to YAMLUtil
                        .getTopLevelKeys(it)
                        .toList()
                        .map { k: YAMLKeyValue -> k.keyText }
                }
                .toMap()

            val iterator: MutableIterator<MutableMap.MutableEntry<String, KedroDataSet>> = service.dataSets.iterator()
            while (iterator.hasNext()) {

                val item: MutableMap.MutableEntry<String, KedroDataSet> = iterator.next()
                val name: String = item.key
                val dataset: KedroDataSet = item.value

                if (dataset.location in yamlKeyMap.keys) {
                    val currentYamlContents: List<String> = yamlKeyMap[dataset.location] ?: listOf()
                    if (name !in currentYamlContents && name in service.dataSets.keys) {
                        iterator.remove()
                    }
                }
            }
        }

    }

    override fun runActivity(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (project.isDisposed) return
        DumbService.getInstance(project).smartInvokeLater {
            try {
                initialiseCatalogLoader(project)
            } catch (e: AlreadyDisposedException) {
            }
        }
    }

    private fun invokeAfterPsiEvents(runnable: () -> Unit) {
        val wrapper = {
            when {
                NoAccessDuringPsiEvents.isInsideEventProcessing() -> invokeAfterPsiEvents(runnable)
                else -> runnable()
            }
        }
        ApplicationManager.getApplication().invokeLater(wrapper, { false })
    }

}

