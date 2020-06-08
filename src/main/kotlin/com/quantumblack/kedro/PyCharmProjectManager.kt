package com.quantumblack.kedro

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.yaml.YAMLFileType
import java.util.concurrent.TimeoutException

// Provide Singleton object for the `PyCharmProjectManager`
object PyCharmProjectManager {

    internal var project: Project? = getProject()

    /**
     * This function attempts to retrieve the project instance which depending on the
     * PyCharm status may not yet be available
     *
     * @return The PyCharm project or null
     */
    private fun getProject(): Project? {

        return try {
            val dataContext: DataContext? = DataManager.getInstance()
                .dataContextFromFocusAsync
                .blockingGet(2_000)
            dataContext?.getData(PlatformDataKeys.PROJECT)
        } catch (e: TimeoutException) {
            null
        }
    }

    /**
     * This function provides a Scope object which is limited to project based YAML files
     *
     * @param project The project to work with
     * @return The scope object limited appropriately
     */
    fun getYamlScope(project: Project): GlobalSearchScope {
        return GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.allScope(project),
            YAMLFileType.YML
        )
    }
}
