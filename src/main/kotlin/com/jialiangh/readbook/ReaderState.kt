package com.jialiangh.readbook

/** 阅读器状态：空闲 / 阅读中（自动滚动）/ 暂停 */
enum class ReaderState {
    IDLE,
    READING,
    PAUSED
}
