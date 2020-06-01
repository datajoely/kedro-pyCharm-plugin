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
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.impl.PyAssignmentStatementImpl
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml

class DetectKedroAction : AnAction() {
    // @todo - Check what happens with multiple open projects
    private var project: Project? = null

    override fun actionPerformed(e: AnActionEvent) {
        detectKedroProjectFiles()
    }

    private fun detectKedroProjectFiles() {
        this.project = ProjectManager.getInstance().openProjects.first()
        val pythonFiles: Collection<VirtualFile> = getFilesByExt("py")
        val yamlFiles: Collection<VirtualFile> = getFilesByExt("yml")
        val setupPy: VirtualFile = pythonFiles.findLast { it.name == "setup.py" }!!
        val kedroYml: VirtualFile = yamlFiles.findLast { it.name == ".kedro.yml" }!!
        val entryPoint: String = getEntryPoint(setupPy)
        val projectContext: PyClass = getProjectContext(entryPoint, pythonFiles)
        val catalogs: Map<String, String> = getKedroCatalogEntries(yamlFiles)
        val temp = getKedroNodePythonFiles(pythonFiles)

        // @todo Suggest kedro generate a file with all of this information available?
    }

    private fun getFilesByExt(ext: String): @NotNull MutableCollection<VirtualFile> =
        FilenameIndex.getAllFilesByExt(this.project!!, ext)

    private fun getKedroCatalogEntries(yamlFiles: Collection<VirtualFile>): Map<String, String> {
        val snakeYaml = Yaml()
        return yamlFiles.asSequence()
            .filter { it.path.contains(regex = Regex(pattern = ".*catalog.+")) } // Get all Catalog YMLs
            .map<VirtualFile, List<Any>> { snakeYaml.loadAll(it.inputStream.bufferedReader()).toList() } // Parse YML
            .flatten<Any?>() // Flatten list of lists
            .map { it.castSafelyTo<Map<String, Map<String, String>>>()!! } // Collapse into one Map
            .asSequence()
            .flatMap { it.asSequence() }
            .filter { !it.key.startsWith(char = '_') } // Remove anchors
            .groupingBy(keySelector = { it.key }) // Group by distinct keys
            .fold(initialValue = "") { _: String, (_: String, item: Map<String, String>) ->
                item["type"].toString() // Take first 'type' value from each group
            }
    }

    private fun getProjectContext(
        entryPoint: String,
        pythonFiles: Collection<VirtualFile>
    ): PyClass {
        val runFileName: String = entryPoint.split(delimiters = *charArrayOf(':'))
            .first()
            .split(delimiters = *charArrayOf('.')).last()

        val virtualRunFile: VirtualFile = pythonFiles.findLast { it.name == "$runFileName.py" }!!
        val pyRunFile: PyFile = parsePythonFile(virtualRunFile)
        return pyRunFile.findTopLevelClass("ProjectContext")!!
    }

    private fun getContextLocation(kedroYml: VirtualFile): String {
        val firstLine: String = kedroYml.inputStream.bufferedReader().readLine()
        val group: Map<*, *> = Yaml().loadAll(firstLine).toList().first() as Map<*, *>
        return group["context_path"].toString()
    }

    private fun getEntryPoint(setupPy: VirtualFile): String {

        val pythonFile: PyFile = parsePythonFile(setupPy)
        val assignment: PyAssignmentStatementImpl = (
                    pythonFile
                        .statements
                        .filterIsInstance<PyAssignmentStatement>()
                        .first() as PyAssignmentStatementImpl
                )

        if (assignment.targets.first().text != "entry_point") {
            throw KedroParsingException("Unable to retrieve `entry point` from `setup.py`")
        }

        val entryPoint: String = assignment.assignedValue!!.children.first().text
        val split: List<String> = entryPoint.split(regex = Regex(pattern = "\\s+=\\s+"))
        return split.last()
    }

    private fun getKedroNodePythonFiles(pythonFiles: Collection<VirtualFile>): List<PyFile> {
        return pythonFiles.map { parsePythonFile(it) }.filter { fileImportsKedroNode(it) }.distinct()
    }

    private fun fileImportsKedroNode(pythonFile: PyFile): Boolean {
        val kedroNodePresent: Boolean
        kedroNodePresent = pythonFile.importBlock
            .map { import: PyImportStatementBase -> import.importElements.map { it.context?.text } }
            .flatten()
            .any { it?.contains(other = "node", ignoreCase = true)!! and
                    it.contains(other = "kedro", ignoreCase = true)
            }

        return kedroNodePresent
    }

    private fun parsePythonFile(dotPyFile: VirtualFile): PyFile {
        return this.project?.let { dotPyFile.toPsi(it) } as PyFile
    }
}
