package com.jialiangh.readbook

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 项目打开时执行一次（全局仅一次）：
 * 1. 注册单键分发器
 * 2. 按“最近文件 → 配置文件路径”自动加载小说
 * 3. 若开启 autoStart 则自动开始阅读
 */
class ReadBookStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!INITIALIZED.compareAndSet(false, true)) return

        // 全局注册单键分发器，生命周期跟随应用
        IdeEventQueue.getInstance().addDispatcher(
            ReadBookKeyDispatcher(),
            ApplicationManager.getApplication()
        )

        val settings = ReadBookSettings.state()
        val service = NovelReaderService.getInstance()

        val recent = settings.recentFilePath
        val configured = settings.filePath
        val pathToLoad = when {
            recent.isNotEmpty() && File(recent).exists() -> recent
            configured.isNotEmpty() && File(configured).exists() -> configured
            else -> null
        }

        if (pathToLoad != null) {
            try {
                val text = String(File(pathToLoad).readBytes(), StandardCharsets.UTF_8)
                service.loadContent(text, pathToLoad)
                LOG.info("已自动加载：$pathToLoad")
            } catch (e: Exception) {
                LOG.warn("自动加载失败：$pathToLoad", e)
            }
        }

        if (settings.autoStart) {
            service.start()
        }
    }

    companion object {
        private val INITIALIZED = AtomicBoolean(false)
        private val LOG = Logger.getInstance(ReadBookStartupActivity::class.java)
    }
}
