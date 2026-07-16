package com.jialiangh.readbook

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

private fun reader() = NovelReaderService.getInstance()

/** 开始阅读（未加载时先加载内置示例，方便直接体验） */
class ReadBookStartAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val r = reader()
        if (r.chunkCount == 0) r.loadDefaultNovel()
        r.start()
    }
}

class ReadBookStopAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().stop()
}

class ReadBookNextAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().next()
}

class ReadBookPrevAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().prev()
}

class ReadBookToggleAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().toggle()
}

class ReadBookJumpToChunkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().jumpToChunk()
}

class ReadBookJumpToTextAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = reader().jumpToText()
}

class ReadBookOpenFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        reader().openFile(e.getData(CommonDataKeys.PROJECT))
    }
}

class ReadBookLoadSampleAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val r = reader()
        r.loadDefaultNovel()
        r.notify("已加载内置示例小说")
    }
}

class ReadBookClearRecentAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val settings = ReadBookSettings.state()
        settings.recentFilePath = ""
        settings.progressFilePath = ""
        settings.progressIndex = 0
        reader().notify("已清除最近记录。下次启动不会自动加载小说。")
    }
}
