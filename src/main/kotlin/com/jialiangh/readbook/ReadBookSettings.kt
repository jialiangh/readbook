package com.jialiangh.readbook

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 插件全部配置 + 阅读进度的持久化存储（应用级，跨项目/跨重启保留）。
 * 对应参考项目里的 configuration 项与 globalState 存档。
 */
@Service(Service.Level.APP)
@State(name = "ReadBookSettings", storages = [Storage("readbook.xml")])
class ReadBookSettings : PersistentStateComponent<ReadBookSettings.State> {

    class State {
        // —— 阅读配置 ——
        /** 小说文件路径（.txt），为空则不自动加载 */
        var filePath: String = ""
        /** 自动播放速度（毫秒/字），越小越快 */
        var speed: Int = 100
        /** 每段显示的字符数 */
        var chunkSize: Int = 50
        /** 底部文字字号 */
        var fontSize: Int = 13
        /** 启动 IDE 时是否自动进入阅读 */
        var autoStart: Boolean = false
        /** 是否弹出操作反馈气泡（错误/警告不受影响） */
        var showNotifications: Boolean = false
        /** 按“跳转到指定段”键时的目标段（1 起，0 表示禁用） */
        var jumpToChunk: Int = 0
        /** 按“模糊匹配跳转”键时的搜索文本 */
        var jumpToText: String = ""

        // —— 单键快捷键（可在设置里改，默认 R/W/E/Q/P/O）——
        var keyStart: String = "R"
        var keyStop: String = "W"
        var keyNext: String = "E"
        var keyPrev: String = "Q"
        var keyJumpChunk: String = "P"
        var keyJumpText: String = "O"

        // —— 进度存档（内部使用）——
        var recentFilePath: String = ""
        var progressIndex: Int = 0
        var progressFilePath: String = ""
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        @JvmStatic
        fun getInstance(): ReadBookSettings =
            ApplicationManager.getApplication().getService(ReadBookSettings::class.java)

        /** 便捷访问 state */
        @JvmStatic
        fun state(): State = getInstance().state
    }
}
