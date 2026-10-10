package inputmethod.switcher.impl;

import enums.InputState;
import inputmethod.switcher.InputMethodSwitchStrategy;
import utils.LogUtil;

import java.awt.Component;
import java.awt.KeyboardFocusManager;

/**
 * ImmDirect 定向直切策略：委托给 Win32IME 完整版（Unsafe 强突 + IMM32 + 消息注入）。
 * 不模拟 Shift，不点任务栏托盘按钮。
 */
public class ImmDirectSwitcher implements InputMethodSwitchStrategy {

    @Override
    public InputState getCurrentMode() {
        return isEnglishMode() ? InputState.ENGLISH : InputState.CHINESE;
    }

    public static boolean isEnglishMode() {
        if (isWindows()) {
            return KeyboardSwitcher.isEnglishMode();
        }
        try {
            Component focusOwner = currentFocusOwner();
            boolean chinese = Win32IME.getPhysicalChineseMode(focusOwner);
            return !chinese;
        } catch (Throwable t) {
            LogUtil.debug("ImmDirect query via Win32IME failed, fallback: " + t.getMessage());
            return KeyboardSwitcher.isEnglishMode();
        }
    }

    public static void setEnglishMode(boolean english) {
        if (isWindows()) {
            KeyboardSwitcher.pressShift();
            return;
        }
        try {
            Component focusOwner = currentFocusOwner();
            // Win32IME 语义：chinese=true 为中文；内部自带去重 + 双发 + 校验
            Win32IME.setChineseMode(focusOwner, !english, false);
        } catch (Throwable t) {
            LogUtil.debug("ImmDirect set via Win32IME failed, fallback to Shift: " + t.getMessage());
            KeyboardSwitcher.pressShift();
        }
    }

    private static Component currentFocusOwner() {
        try {
            return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return !os.toLowerCase().contains("win");
    }

    @Override
    public void change() {
        long start = System.nanoTime();
        try {
            boolean englishNow = isEnglishMode();
            setEnglishMode(!englishNow);
        } finally {
            double ms = (System.nanoTime() - start) / 1e6;
            LogUtil.debug("ImmDirect 执行时间: " + String.format("%.6f", ms) + " ms");
        }
    }
}
