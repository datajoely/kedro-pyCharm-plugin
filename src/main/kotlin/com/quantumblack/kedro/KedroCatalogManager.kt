package com.quantumblack.kedro

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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

        invokeAfterPsiEvents {
            val yamlFiles: List<YAMLFile> = KedroDataCatalogUtilities.getKedroCatalogYamlFiles(
                project = project,
                psiManager = PsiManager.getInstance(project)
            )
            updateDataSets(yamlFiles, dataCatalogService)
        }

        VirtualFileManager.getInstance().addAsyncFileListener(
            { events: MutableList<out VFileEvent> -> eventHandler(project, events, dataCatalogService) },
            { }
        )
    }

    private fun eventHandler(project: Project, events: MutableList<out VFileEvent>, service: KedroYamlCatalogService):
            AsyncFileListener.ChangeApplier {
        return object : AsyncFileListener.ChangeApplier {

            override fun beforeVfsChange() {
                if (project.isDisposed) return
                try {

                    val projectFiles: Sequence<VirtualFile> = getProjectFiles(events, project)
                    if (projectFiles.count() == 0) return


                    invokeAfterPsiEvents {
                        val psiManager: PsiManager = PsiManager.getInstance(project)
                        val changedYamlFiles: List<YAMLFile> = projectFiles
                            .asSequence()
                            .filter { KedroDataCatalogUtilities.isKedroYamlFile(it, project) }
                            .mapNotNull { psiManager.findFile(it) }
                            .mapNotNull { it.castSafelyTo<YAMLFile>() }
                            .toList()

                        updateDataSets(
                            changedYamlFiles = changedYamlFiles,
                            service = service
                        )
                        removeOldDataSets(
                            changedYamlFiles = changedYamlFiles,
                            service = service
                        )


                    }
                } catch (e: AlreadyDisposedException) {
                }
            }
        }
    }

    private fun getProjectFiles(events: MutableList<out VFileEvent>, project: Project): Sequence<VirtualFile> {
        try {
            return events
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
        } catch (e: Exception) {
            println(e)
            return sequenceOf()
        }
    }


    private fun updateDataSets(changedYamlFiles: List<YAMLFile>, service: KedroYamlCatalogService) {
        invokeAfterPsiEvents {
            val newKedroDataSets: Map<String, KedroDataSet> = KedroDataCatalogUtilities.getDataSets(changedYamlFiles)
            for (newDataSet: KedroDataSet in newKedroDataSets.values) service.addOrReplaceDataSet(dataSet = newDataSet)
        }
    }


    private fun removeOldDataSets(changedYamlFiles: List<YAMLFile>, service: KedroYamlCatalogService) {
        invokeAfterPsiEvents {
            val newKeysForChangedYamlFiles: Map<String, List<String>> = changedYamlFiles
                .map {
                    it.containingFile.name to YAMLUtil
                        .getTopLevelKeys(it)
                        .toList()
                        .map { k: YAMLKeyValue -> k.keyText }
                }
                .toMap()

            val toDelete: List<KedroDataSet> =
                newKeysForChangedYamlFiles.map { (catalogName: String, currentDataSetsInYaml: List<String>) ->
                    val existingDataSetsForYaml: List<String> = service.getDataSetsByYaml(catalogName).map { it.name }
                    val delta: List<String> = (existingDataSetsForYaml - currentDataSetsInYaml)
                    delta.mapNotNull { service.getDataSetByName(it) }
                }.flatten()

            for (oldDataSet: KedroDataSet in toDelete) service.removeDataSet(dataSet = oldDataSet)

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
        ApplicationManager.getApplication().invokeLater(wrapper, ModalityState.defaultModalityState())
    }

}

