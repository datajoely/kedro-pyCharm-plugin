package com.quantumblack.kedro

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class KedroYamlCatalogScanner : StartupActivity {
    override fun runActivity(project: Project) {

    }

}