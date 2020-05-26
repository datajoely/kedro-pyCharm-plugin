package com.quantumblack.kedro

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.annotations.NotNull





class DetectKedroAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        detectKedroProjectFiles()
    }

    private fun detectKedroProjectFiles() {
        val p: @NotNull Project = ProjectManager.getInstance().openProjects[0]
        val pythonFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(p, "py")
        val yamlFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(p, "yml")
        val setupPy: VirtualFile? = pythonFiles.findLast { it.name == "setup.py" }
        val kedroYml : VirtualFile? = yamlFiles.findLast { it.name == ".kedro.yml" }
       if (setupPy == null || kedroYml == null){
           throw InstantiationException("Unable to find expected files, is this a Kedro project?")
       }


    }
}