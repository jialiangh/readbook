package com.jialiangh.readbook

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 阅读器核心：小说加载、切段、自动滚动、翻页、跳转、进度存档。
 * 应用级单例，状态在所有打开的项目窗口间共享。
 */
@Service(Service.Level.APP)
class NovelReaderService : Disposable {

    var state: ReaderState = ReaderState.IDLE
        private set

    /** 状态栏是否可见：按 W 停止后隐藏，按 R 开始后重新显示 */
    var uiVisible: Boolean = true
        private set

    /** 小说全文（trim 后） */
    private var content: String = ""

    /** 切分后的段落 */
    private var chunks: List<String> = emptyList()

    var currentIndex: Int = 0
        private set

    /** 当前文件标识（绝对路径或 "__default__"），用于按文件区分存档 */
    private var currentFilePath: String = DEFAULT_FILE

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    /** 展示监听器（状态栏 widget 注册进来） */
    private val listeners = mutableSetOf<Runnable>()

    private val settings get() = ReadBookSettings.state()

    val chunkCount: Int get() = chunks.size

    // —— 监听/刷新 ——

    fun addListener(l: Runnable) {
        listeners.add(l)
    }

    fun removeListener(l: Runnable) {
        listeners.remove(l)
    }

    /** 通知所有 widget 刷新（保证在 EDT 上执行） */
    fun fireUpdate() {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            listeners.toList().forEach { it.run() }
        } else {
            app.invokeLater { listeners.toList().forEach { it.run() } }
        }
    }

    // —— 加载 ——

    /**
     * 加载小说文本。
     * @param text 全文
     * @param filePath 文件标识（不传则沿用当前标识）
     */
    fun loadContent(text: String, filePath: String? = null) {
        content = text.trim()
        if (content.isEmpty()) {
            chunks = emptyList()
            currentIndex = 0
            fireUpdate()
            warn("小说内容为空！")
            return
        }
        chunks = splitIntoChunks(content, settings.chunkSize)
        if (filePath != null) {
            currentFilePath = filePath
        }
        currentIndex = restoreIndexForCurrentFile()
        fireUpdate()
        LOG.info("已加载小说，共 ${chunks.size} 段，起始段 ${currentIndex + 1}")
        if (filePath != null && filePath != DEFAULT_FILE) {
            settings.recentFilePath = filePath
        }
    }

    /** 加载内置示例小说（从插件资源读取） */
    fun loadDefaultNovel() {
        val text = try {
            javaClass.getResourceAsStream("/novels/sample.txt")?.use {
                it.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            LOG.warn("读取内置示例失败", e)
            null
        }
        loadContent(
            text ?: "这是一个示例小说内容。请在设置里配置小说文件路径，或用“打开小说文件”选择你的 .txt。" +
                "小说会按段落显示在底部状态栏，点击可暂停/继续。",
            DEFAULT_FILE
        )
    }

    /** 弹出文件选择框加载 txt */
    fun openFile(project: Project?) {
        val descriptor = FileChooserDescriptorFactory
            .createSingleFileDescriptor("txt")
            .withTitle("选择小说文件")
        val file = FileChooser.chooseFile(descriptor, project, null) ?: return
        try {
            val path = file.path
            val text = String(File(path).readBytes(), StandardCharsets.UTF_8)
            loadContent(text, path)
            notify("已加载：${File(path).name}")
        } catch (e: Exception) {
            error("读取文件失败：${e.message}")
        }
    }

    /** 重新按当前 chunkSize 切段（配置改动后调用），尽量保持当前段位置 */
    fun reSplit() {
        if (content.isEmpty()) {
            fireUpdate()
            return
        }
        val old = currentIndex
        chunks = splitIntoChunks(content, settings.chunkSize)
        currentIndex = old.coerceIn(0, maxOf(0, chunks.size - 1))
        fireUpdate()
    }

    // —— 播放控制 ——

    fun start() {
        if (chunks.isEmpty()) {
            warn("当前还没有加载小说。请用“打开小说文件”选择 .txt，或在设置里配置文件路径。")
            return
        }
        if (state == ReaderState.READING) return
        uiVisible = true
        state = ReaderState.READING
        fireUpdate()
        scheduleNext()
        notify("已开始阅读（自动播放中）。翻页/退出见状态栏右键菜单或快捷键。")
    }

    fun pause() {
        if (state != ReaderState.READING) return
        state = ReaderState.PAUSED
        alarm.cancelAllRequests()
        fireUpdate()
        notify("已暂停阅读")
    }

    fun resume() {
        if (state != ReaderState.PAUSED) return
        state = ReaderState.READING
        scheduleNext()
        fireUpdate()
        notify("继续阅读")
    }

    fun toggle() {
        when (state) {
            ReaderState.READING -> pause()
            ReaderState.PAUSED -> resume()
            ReaderState.IDLE -> start()
        }
    }

    fun next() {
        if (chunks.isEmpty()) return
        alarm.cancelAllRequests()
        currentIndex = if (currentIndex < chunks.size - 1) currentIndex + 1 else 0
        fireUpdate()
        saveProgress()
        if (state == ReaderState.READING) scheduleNext()
    }

    fun prev() {
        if (chunks.isEmpty()) return
        alarm.cancelAllRequests()
        currentIndex = if (currentIndex > 0) currentIndex - 1 else chunks.size - 1
        fireUpdate()
        saveProgress()
        if (state == ReaderState.READING) scheduleNext()
    }

    /** 跳转到设置里的指定段（1 起） */
    fun jumpToChunk() {
        if (chunks.isEmpty()) return
        val target = settings.jumpToChunk
        if (target < 1) return
        val clamped = minOf(target, chunks.size)
        currentIndex = clamped - 1
        fireUpdate()
        saveProgress()
    }

    /** 模糊匹配跳转（读取设置里的搜索文本） */
    fun jumpToText() {
        if (chunks.isEmpty()) return
        val raw = settings.jumpToText
        val keyword = raw.replace(Regex("\\s+"), "")
        if (keyword.isEmpty()) return

        var matchIndex = -1
        for (i in chunks.indices) {
            if (chunks[i].replace(Regex("\\s+"), "").contains(keyword)) {
                matchIndex = i
                break
            }
        }
        if (matchIndex == -1) {
            for (i in chunks.indices) {
                if (isSubsequence(keyword, chunks[i].replace(Regex("\\s+"), ""))) {
                    matchIndex = i
                    break
                }
            }
        }
        if (matchIndex == -1) {
            warn("未找到匹配\"$raw\"的段落")
            return
        }
        currentIndex = matchIndex
        fireUpdate()
        saveProgress()
        notify("已跳转到第 ${matchIndex + 1} 段（匹配\"$raw\"）")
    }

    fun stop() {
        alarm.cancelAllRequests()
        state = ReaderState.IDLE
        saveProgress()
        uiVisible = false
        fireUpdate()
        notify("已停止阅读（已记住位置，下次开始从断点继续）")
    }

    // —— 定时器 ——

    private fun scheduleNext() {
        alarm.cancelAllRequests()
        if (state != ReaderState.READING) return
        val speed = settings.speed
        val len = maxOf(1, (chunks.getOrNull(currentIndex) ?: "").length)
        val ms = maxOf(200, speed.toLong() * len)
        alarm.addRequest({
            if (state == ReaderState.READING) next()
        }, ms)
    }

    // —— 存档 ——

    private fun saveProgress() {
        if (chunks.isEmpty()) return
        settings.progressIndex = currentIndex
        settings.progressFilePath = currentFilePath.lowercase()
    }

    private fun restoreIndexForCurrentFile(): Int {
        val savedPath = settings.progressFilePath
        if (savedPath.isEmpty()) return 0
        if (savedPath != currentFilePath.lowercase()) return 0
        val idx = settings.progressIndex
        if (idx < 0 || idx >= chunks.size) return 0
        return idx
    }

    // —— 展示文本 ——

    fun displayText(): String {
        if (chunks.isEmpty()) return "阅读小说：未加载"
        val current = chunks[currentIndex]
        return "$current [${currentIndex + 1}/${chunks.size}]"
    }

    fun tooltipText(): String {
        if (chunks.isEmpty()) return "阅读小说：点击开始 / 右键菜单加载文件"
        val current = chunks[currentIndex]
        return "<html>第 ${currentIndex + 1} 段 / 共 ${chunks.size} 段<br><br>" +
            current.replace("<", "&lt;").replace(">", "&gt;") +
            "<br><br>点击切换暂停/继续</html>"
    }

    // —— 切段（移植自参考项目 splitIntoChunks）——

    private fun splitIntoChunks(text: String, size: Int): List<String> {
        val target = maxOf(10, size)
        val result = mutableListOf<String>()

        // 策略 A：连续双换行切自然段
        var paragraphs = text
            .split(Regex("\\r?\\n[\\s\\u3000]*(?:\\r?\\n[\\s\\u3000]*)+"))
            .map { it.replace(Regex("[\\s\\u3000]+"), " ").trim() }
            .filter { it.isNotEmpty() }

        // 策略 B：只得到 1 段则回退按任意 \n+ 分段
        if (paragraphs.size <= 1) {
            paragraphs = text
                .split(Regex("\\r?\\n+"))
                .map { it.replace(Regex("[\\s\\u3000]+"), " ").trim() }
                .filter { it.isNotEmpty() }
        }

        val breakRegex = Regex("[。！？][^。！？]*$")
        for (para in paragraphs) {
            if (para.length <= target) {
                result.add(para)
                continue
            }
            var pos = 0
            val len = para.length
            while (pos < len) {
                var end = minOf(pos + target, len)
                if (end < len) {
                    val searchStr = para.substring(pos, end)
                    val m = breakRegex.find(searchStr)
                    if (m != null) end = pos + m.range.first + 1
                    if (end <= pos) end = pos + 1
                }
                val chunk = para.substring(pos, end).trim()
                if (chunk.isNotEmpty()) result.add(chunk)
                pos = end
            }
        }
        return result
    }

    /** needle 是否为 haystack 的子序列（字符按序出现，可不连续） */
    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var j = 0
        var i = 0
        while (i < haystack.length && j < needle.length) {
            if (haystack[i] == needle[j]) j++
            i++
        }
        return j == needle.length
    }

    // —— 通知 ——

    /** 操作反馈气泡，受 showNotifications 控制 */
    fun notify(message: String) {
        if (!settings.showNotifications) return
        balloon(message, NotificationType.INFORMATION)
    }

    private fun warn(message: String) = balloon(message, NotificationType.WARNING)

    private fun error(message: String) = balloon(message, NotificationType.ERROR)

    private fun balloon(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ReadBook Notifications")
            .createNotification(message, type)
            .notify(null)
    }

    override fun dispose() {
        alarm.cancelAllRequests()
        listeners.clear()
    }

    companion object {
        private const val DEFAULT_FILE = "__default__"
        private val LOG = Logger.getInstance(NovelReaderService::class.java)

        @JvmStatic
        fun getInstance(): NovelReaderService =
            ApplicationManager.getApplication().getService(NovelReaderService::class.java)
    }
}
