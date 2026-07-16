package com.jialiangh.readbook

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/** 注册底部状态栏 widget 的工厂 */
class ReadBookStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ReadBookStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "阅读小说 (ReadBook)"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = ReadBookStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        com.intellij.openapi.util.Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
