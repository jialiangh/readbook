package com.jialiangh.readbook

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * 底部状态栏 widget：用一个 JLabel 展示当前段落文字（可设字号）。
 * 左键点击 = 暂停/继续（空闲时开始）；右键 = 弹出全部操作菜单。
 */
class ReadBookStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val service = NovelReaderService.getInstance()
    private val label = JBLabel()
    private val listener = Runnable { refresh() }
    private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent {
        label.border = JBUI.Borders.empty(0, 6)
        applyFont()
        label.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    showPopup(e)
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    service.toggle()
                }
            }
        })
        refresh()
        return label
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        service.addListener(listener)
    }

    private fun refresh() {
        val visible = service.uiVisible
        label.isVisible = visible
        if (visible) {
            applyFont()
            label.text = service.displayText()
            label.icon = stateIcon()
            label.toolTipText = service.tooltipText()
        } else {
            // 停止后彻底隐藏：清空内容并让父容器重新布局，不再占位
            label.text = ""
            label.icon = null
            label.toolTipText = null
        }
        label.revalidate()
        label.repaint()
        label.parent?.revalidate()
        label.parent?.repaint()
    }

    private fun applyFont() {
        val size = ReadBookSettings.state().fontSize.coerceAtLeast(6)
        label.font = label.font.deriveFont(size.toFloat())
    }

    private fun stateIcon(): Icon = when (service.state) {
        ReaderState.READING -> AllIcons.Actions.Execute
        ReaderState.PAUSED -> AllIcons.Actions.Pause
        ReaderState.IDLE -> AllIcons.Actions.Suspend
    }

    /**
     * 用平台原生的 JBPopupFactory + ActionGroup 弹菜单。
     * 之前用原生 Swing JPopupMenu/JMenuItem，在 IDE 状态栏里点击项的鼠标事件会被
     * 平台吞掉，导致 actionListener 根本不触发（日志里从没出现点击标记）。
     * 平台弹窗会通过 IDE 的动作系统正确派发点击，并提供带 project 的 DataContext。
     */
    private fun showPopup(e: MouseEvent) {
        val group = DefaultActionGroup()

        fun add(text: String, run: (Project) -> Unit) {
            group.add(object : AnAction(text) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun actionPerformed(ev: AnActionEvent) {
                    run(ev.project ?: project)
                }
            })
        }

        add("开始 / 暂停") { _ ->
            if (service.chunkCount == 0) service.loadDefaultNovel()
            service.toggle()
        }
        add("隐藏") { _ -> service.stop() }
        add("下一段") { _ -> service.next() }
        add("上一段") { _ -> service.prev() }
        add("跳转到指定段") { _ -> service.jumpToChunk() }
        add("模糊匹配跳转") { _ -> service.jumpToText() }
        group.addSeparator()
        add("设置") { p ->
            LOG.info("openSettings clicked, project=${p.name}")
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .editConfigurable(p, ReadBookConfigurable())
        }
        add("加载内置示例") { _ ->
            service.loadDefaultNovel()
            service.notify("已加载内置示例小说")
        }

        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            "阅读小说",
            group,
            DataManager.getInstance().getDataContext(label),
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true
        )
        popup.show(RelativePoint(e))
    }

    override fun dispose() {
        service.removeListener(listener)
        statusBar = null
    }

    companion object {
        const val WIDGET_ID = "ReadBook.Widget"
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ReadBookStatusBarWidget::class.java)
    }
}
