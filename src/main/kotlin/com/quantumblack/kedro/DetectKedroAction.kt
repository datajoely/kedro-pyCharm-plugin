package com.quantumblack.kedro


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.jetbrains.extensions.python.toPsi
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyAssignmentStatementImpl
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File

class DetectKedroAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        detectKedroProjectFiles()
    }

    private fun detectKedroProjectFiles() {
        val proj: @NotNull Project = ProjectManager.getInstance().openProjects[0]
        val pythonFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(proj, "py")
        val yamlFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(proj, "yml")
        val setupPy: VirtualFile = pythonFiles.findLast { it.name == "setup.py" }!!
        val kedroYml: VirtualFile = yamlFiles.findLast { it.name == ".kedro.yml" }!!
        val contextPath: String = getContextLocation(kedroYml)
        val entryPoint: String = getEntryPoint(setupPy, proj)
        val runFile : VirtualFile = getRunFile(entryPoint, pythonFiles)
        // contextPath = "spaceflights.run.ProjectContext"
        // entryPoint = "spaceflights.run:main"

    }

    private fun getRunFile(entryPoint: String, pythonFiles:Collection<VirtualFile>): VirtualFile {
        val runFileName: String =  entryPoint.split(delimiters = *charArrayOf(':'))
            .first()
            .split(delimiters = *charArrayOf('.')).last()
        return pythonFiles.findLast { it.name == "$runFileName.py" }!!

    }

    private fun getContextLocation(kedroYml: VirtualFile): String {
        val firstLine: String = File(kedroYml.path).bufferedReader().readLine()
        val group: Map<*, *> = Yaml().loadAll(firstLine).toList().first() as Map<*, *>
        return group["context_path"].toString()
    }

    private fun getEntryPoint(setupPy: VirtualFile, project: Project): String {

        val pythonFile: PyFile = setupPy.toPsi(project)!! as PyFile
        val assignment: PyAssignmentStatementImpl = (
                    pythonFile
                        .statements
                        .filterIsInstance<PyAssignmentStatement>()
                        .first() as PyAssignmentStatementImpl
                )

        if (assignment.targets.first().text != "entry_point"){
            throw KedroParsingException("Unable to retrieve `entry point` from `setup.py`", 0)
        }

        val entryPoint: String = assignment.assignedValue!!.children.first().text
        val split: List<String> = entryPoint.split(regex = Regex(pattern = "\\s+=\\s+"))
        return split.last()

    }

}