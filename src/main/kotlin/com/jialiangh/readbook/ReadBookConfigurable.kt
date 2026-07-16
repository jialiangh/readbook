package com.jialiangh.readbook

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.util.ui.FormBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.JPanel

/** 设置界面：Settings/Preferences → 工具 → 阅读小说 (ReadBook) */
class ReadBookConfigurable : Configurable {

    private val filePathField = TextFieldWithBrowseButton()
    private val speedField = IntegerField("", 50, Int.MAX_VALUE)
    private val chunkSizeField = IntegerField("", 10, 2000)
    private val fontSizeField = IntegerField("", 6, 72)
    private val jumpToChunkField = IntegerField("", 0, Int.MAX_VALUE)
    private val jumpToTextField = JBTextField()
    private val autoStartBox = JBCheckBox("启动 IDE 时自动进入阅读")
    private val showNotificationsBox = JBCheckBox("弹出操作反馈气泡（错误/警告不受影响）")

    private val keyStartField = JBTextField()
    private val keyStopField = JBTextField()
    private val keyNextField = JBTextField()
    private val keyPrevField = JBTextField()
    private val keyJumpChunkField = JBTextField()
    private val keyJumpTextField = JBTextField()

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "阅读小说 (ReadBook)"

    override fun createComponent(): JComponent {
        filePathField.addBrowseFolderListener(
            "选择小说文件", "选择一个 .txt 小说文件", null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("txt")
        )
        val keyHint = "（单键，聚焦在编辑器打字时不触发，把焦点放到工具窗口/项目树/状态栏再用）"
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("小说文件路径（.txt）", filePathField, true)
            .addLabeledComponent("自动播放速度（毫秒/字，越小越快）", speedField)
            .addLabeledComponent("每段字符数", chunkSizeField)
            .addLabeledComponent("底部文字字号", fontSizeField)
            .addComponent(autoStartBox)
            .addComponent(showNotificationsBox)
            .addSeparator()
            .addLabeledComponent("跳转到指定段（1 起，0=禁用）", jumpToChunkField)
            .addLabeledComponent("模糊匹配跳转的搜索文本", jumpToTextField)
            .addSeparator()
            .addComponent(com.intellij.ui.components.JBLabel("单键快捷键 $keyHint"))
            .addLabeledComponent("开始阅读", keyStartField)
            .addLabeledComponent("退出阅读", keyStopField)
            .addLabeledComponent("下一段", keyNextField)
            .addLabeledComponent("上一段", keyPrevField)
            .addLabeledComponent("跳转到指定段", keyJumpChunkField)
            .addLabeledComponent("模糊匹配跳转", keyJumpTextField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = ReadBookSettings.state()
        return filePathField.text != s.filePath ||
            speedField.value != s.speed ||
            chunkSizeField.value != s.chunkSize ||
            fontSizeField.value != s.fontSize ||
            jumpToChunkField.value != s.jumpToChunk ||
            jumpToTextField.text != s.jumpToText ||
            autoStartBox.isSelected != s.autoStart ||
            showNotificationsBox.isSelected != s.showNotifications ||
            key(keyStartField) != s.keyStart ||
            key(keyStopField) != s.keyStop ||
            key(keyNextField) != s.keyNext ||
            key(keyPrevField) != s.keyPrev ||
            key(keyJumpChunkField) != s.keyJumpChunk ||
            key(keyJumpTextField) != s.keyJumpText
    }

    override fun apply() {
        val s = ReadBookSettings.state()
        val oldFilePath = s.filePath
        val oldChunkSize = s.chunkSize

        s.filePath = filePathField.text.trim()
        s.speed = speedField.value.coerceAtLeast(50)
        s.chunkSize = chunkSizeField.value.coerceAtLeast(10)
        s.fontSize = fontSizeField.value.coerceIn(6, 72)
        s.jumpToChunk = jumpToChunkField.value.coerceAtLeast(0)
        s.jumpToText = jumpToTextField.text
        s.autoStart = autoStartBox.isSelected
        s.showNotifications = showNotificationsBox.isSelected
        s.keyStart = key(keyStartField)
        s.keyStop = key(keyStopField)
        s.keyNext = key(keyNextField)
        s.keyPrev = key(keyPrevField)
        s.keyJumpChunk = key(keyJumpChunkField)
        s.keyJumpText = key(keyJumpTextField)

        val service = NovelReaderService.getInstance()
        // 文件路径变化且存在 → 重新加载；否则字号/段长变化则重切段刷新
        if (s.filePath != oldFilePath && s.filePath.isNotEmpty() && File(s.filePath).exists()) {
            try {
                val text = String(File(s.filePath).readBytes(), StandardCharsets.UTF_8)
                service.loadContent(text, s.filePath)
            } catch (e: Exception) {
                service.fireUpdate()
            }
        } else if (s.chunkSize != oldChunkSize) {
            service.reSplit()
        } else {
            service.fireUpdate()
        }
    }

    override fun reset() {
        val s = ReadBookSettings.state()
        filePathField.text = s.filePath
        speedField.value = s.speed
        chunkSizeField.value = s.chunkSize
        fontSizeField.value = s.fontSize
        jumpToChunkField.value = s.jumpToChunk
        jumpToTextField.text = s.jumpToText
        autoStartBox.isSelected = s.autoStart
        showNotificationsBox.isSelected = s.showNotifications
        keyStartField.text = s.keyStart
        keyStopField.text = s.keyStop
        keyNextField.text = s.keyNext
        keyPrevField.text = s.keyPrev
        keyJumpChunkField.text = s.keyJumpChunk
        keyJumpTextField.text = s.keyJumpText
    }

    /** 只取第一个字符并大写，作为单键绑定 */
    private fun key(field: JBTextField): String =
        field.text.trim().take(1).uppercase()
}
