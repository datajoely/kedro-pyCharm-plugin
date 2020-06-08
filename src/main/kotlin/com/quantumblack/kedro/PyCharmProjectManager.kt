package com.quantumblack.kedro

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.yaml.YAMLFileType
import java.util.concurrent.TimeoutException

object PyCharmProjectManager { // Singleton

    internal var project: Project? = getProject()

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

    fun getScope(project: Project): GlobalSearchScope {
        return GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.allScope(project),
            YAMLFileType.YML
        )
    }
}
