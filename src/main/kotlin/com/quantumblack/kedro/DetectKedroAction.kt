package com.quantumblack.kedro

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.castSafelyTo
import com.jetbrains.extensions.python.toPsi
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyAssignmentStatementImpl
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml


class DetectKedroAction : AnAction() {


    override fun actionPerformed(e: AnActionEvent) {
        detectKedroProjectFiles()
    }

    private fun detectKedroProjectFiles() {
        val project: @NotNull Project = ProjectManager.getInstance().openProjects[0]
        val pythonFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(project, "py")
        val yamlFiles: Collection<VirtualFile> = FilenameIndex.getAllFilesByExt(project, "yml")
        val setupPy: VirtualFile = pythonFiles.findLast { it.name == "setup.py" }!!
        val kedroYml: VirtualFile = yamlFiles.findLast { it.name == ".kedro.yml" }!!
        val entryPoint: String = getEntryPoint(setupPy, project)
        val projectContext: PyClass = getProjectContext(entryPoint, pythonFiles, project)
        val catalogs: Map<String, String> = getKedroCatalogEntries(yamlFiles)
        // @todo Suggest kedro generate a file with all of this information available?
        // @todo Custom DataSet detection
        // @todo Autocomplete Node input/output
        // @todo get org.jetbrains.plugins.yaml working
    }

    private fun getKedroCatalogEntries(yamlFiles: Collection<VirtualFile>): Map<String, String> {
        val snakeYaml = Yaml()
        return yamlFiles.asSequence()
            .filter { it.path.contains(regex = Regex(pattern = ".*catalog.+")) } // Get all Catalog YMLs
            .map<VirtualFile, List<Any>> { snakeYaml.loadAll(it.inputStream.bufferedReader()).toList() } // Parse YML
            .flatten<Any?>() // Flatten list of lists
            .map { it.castSafelyTo<Map<String, Map<String, String>>>()!! } // Collapse into one Map
            .asSequence()
            .flatMap {it.asSequence()}
            .filter { !it.key.startsWith(char = '_') } // Remove anchors
            .groupingBy(keySelector = { it.key }) // Group by distinct keys
            .fold(initialValue = "") { _: String, (_: String, item: Map<String, String>) ->
                item["type"].toString() // Take first 'type' value from each group
            }
    }


    private fun getProjectContext(entryPoint: String, pythonFiles:Collection<VirtualFile>, project: Project): PyClass {
        val runFileName: String =  entryPoint.split(delimiters = *charArrayOf(':'))
            .first()
            .split(delimiters = *charArrayOf('.')).last()

        val virtualRunFile: VirtualFile =  pythonFiles.findLast { it.name == "$runFileName.py" }!!
        val pyRunFile:PyFile = parsePythonFile(virtualRunFile, project)
        return pyRunFile.findTopLevelClass("ProjectContext")!!

    }

    private fun getContextLocation(kedroYml: VirtualFile): String {
        val firstLine: String = kedroYml.inputStream.bufferedReader().readLine()
        val group: Map<*, *> = Yaml().loadAll(firstLine).toList().first() as Map<*, *>
        return group["context_path"].toString()
    }

    private fun getEntryPoint(setupPy: VirtualFile, project: Project): String {

        val pythonFile: PyFile = parsePythonFile(setupPy, project)
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

    private fun parsePythonFile(
        dotPyFile: VirtualFile,
        project: Project
    ): PyFile {
        return dotPyFile.toPsi(project)!! as PyFile
    }


}