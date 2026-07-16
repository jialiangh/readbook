package com.jialiangh.readbook

import com.intellij.ide.IdeEventQueue
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.text.JTextComponent

/**
 * 单键快捷键处理（R/W/E/Q/P/O，可在设置里改）。
 *
 * JetBrains 里普通 Action 绑定单字母会在编辑器打字时被抢占，所以这里改用一个全局
 * AWT 事件分发器：只有在“焦点不在文本输入组件（编辑器/输入框）上”时才拦截单键。
 * 这样在编辑器里正常打字不受影响，把焦点放到工具窗口/项目树/状态栏等地方才用单键翻页，
 * 与参考项目 `when: !inputFocus` 的摸鱼语义一致，从根本上避开与输入的冲突。
 *
 * 翻页/退出/跳转类键要求阅读已激活（READING/PAUSED）；开始键在空闲时也可用。
 */
class ReadBookKeyDispatcher : IdeEventQueue.EventDispatcher {

    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.id != KeyEvent.KEY_PRESSED) return false

        // 只处理“裸键”：带 Ctrl/Alt/Meta 的组合键放行，避免抢占 IDE 自身快捷键
        val badMods = InputEvent.CTRL_DOWN_MASK or
            InputEvent.ALT_DOWN_MASK or
            InputEvent.META_DOWN_MASK or
            InputEvent.ALT_GRAPH_DOWN_MASK
        if (e.modifiersEx and badMods != 0) return false

        // 焦点在文本输入组件上 → 放行（让用户正常打字）
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (isTextInput(focus)) return false

        val keyName = KeyEvent.getKeyText(e.keyCode)
        val settings = ReadBookSettings.state()
        val service = NovelReaderService.getInstance()
        val active = service.state != ReaderState.IDLE

        fun match(bind: String) = bind.isNotEmpty() && keyName.equals(bind, ignoreCase = true)

        when {
            match(settings.keyStart) -> {
                if (service.chunkCount == 0) service.loadDefaultNovel()
                service.start()
                return true
            }
            active && match(settings.keyStop) -> { service.stop(); return true }
            active && match(settings.keyNext) -> { service.next(); return true }
            active && match(settings.keyPrev) -> { service.prev(); return true }
            active && match(settings.keyJumpChunk) -> { service.jumpToChunk(); return true }
            active && match(settings.keyJumpText) -> { service.jumpToText(); return true }
        }
        return false
    }

    private fun isTextInput(c: Component?): Boolean {
        if (c == null) return false
        if (c is JTextComponent) return true
        val name = c.javaClass.name
        // 编辑器组件（EditorComponentImpl）以及其它文本编辑区
        return name.contains("EditorComponent") || name.contains("TextComponent")
    }
}
