package com.appprogress.desktop

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import kotlin.concurrent.thread

/**
 * Глобальная горячая клавиша Windows (работает, даже когда активна другая программа).
 * RegisterHotKey привязывает сочетание к потоку, поэтому у нас свой поток с циклом сообщений Windows.
 * На Linux пока не поддерживается — там быстрая запись доступна из трея и по Ctrl+K в окне.
 */
object GlobalHotkey {
    private const val MOD_ALT = 0x0001
    private const val MOD_CONTROL = 0x0002
    private const val MOD_NOREPEAT = 0x4000
    private const val HOTKEY_ID = 0xA11

    /** Ctrl+Alt+N. Возвращает false, если не Windows или сочетание уже занято другой программой. */
    fun registerCtrlAltN(onPressed: () -> Unit): Boolean {
        if (!System.getProperty("os.name").lowercase().contains("win")) return false
        var registered = false
        val ready = java.util.concurrent.CountDownLatch(1)
        thread(isDaemon = true, name = "global-hotkey") {
            val user32 = User32.INSTANCE
            registered = user32.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_ALT or MOD_NOREPEAT, 'N'.code)
            ready.countDown()
            if (!registered) return@thread
            val msg = WinUser.MSG()
            while (user32.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WinUser.WM_HOTKEY && msg.wParam.toInt() == HOTKEY_ID) onPressed()
            }
        }
        ready.await()
        return registered
    }
}
