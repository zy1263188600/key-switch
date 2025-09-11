package inputmethod.impl;

import enums.InputState;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import inputmethod.InputMethodSwitchStrategy;


import static com.sun.jna.platform.win32.WinUser.KEYBDINPUT.KEYEVENTF_KEYUP;
import static com.sun.jna.platform.win32.WinUser.VK_LSHIFT;


public class KeyboardSwitcher implements InputMethodSwitchStrategy {

    private static long lastPressTime = 0;
    private static final long MIN_PRESS_INTERVAL_MS = 100;

    public interface Imm32 extends Library {

        Imm32 INSTANCE = Native.load("imm32", Imm32.class);

        Pointer ImmGetDefaultIMEWnd(Pointer hWnd);

        //释放窗口
        Pointer ImmGetContext(HWND hWnd);

        boolean ImmReleaseContext(HWND hWnd, Pointer hIMC);

    }

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        HWND GetForegroundWindow();

        long SendMessage(HWND hWnd, int Msg, long wParam, long lParam);

        //模拟按键盘按键
        void keybd_event(byte bVk, byte bScan, int dwFlags, int extraInfo);
    }

    private static final int WM_IME_CONTROL = 0x0283;
    private static final int IMC_GETOPENSTATUS = 0x0001;

    @Override
    public InputState getCurrentMode() {
        return isEnglishMode() ? InputState.ENGLISH : InputState.CHINESE;
    }

    public static boolean isEnglishMode() {
        try {
            User32 user32 = User32.INSTANCE;
            Imm32 imm32 = Imm32.INSTANCE;

            HWND activeWindow = user32.GetForegroundWindow();
            if (activeWindow == null) {
                System.out.println("未找到激活窗口");
                return false;
            }

            Pointer imeWnd = imm32.ImmGetDefaultIMEWnd(activeWindow.getPointer());
            if (imeWnd == null) {
                System.out.println("未找到输入法窗口");
                return true; // 英文输入法可能没有 IME 窗口
            }

            long result = user32.SendMessage(new HWND(imeWnd), WM_IME_CONTROL, IMC_GETOPENSTATUS, 0);
            Pointer hIMC = imm32.ImmGetContext(activeWindow);
            if (hIMC != null) {
                imm32.ImmReleaseContext(activeWindow, hIMC);
            }

            return result == 0;
        } catch (Exception e) {
            System.out.println("获取输入法异常");
            return false;
        }
    }

    public static void pressShift() {
        long now = System.currentTimeMillis();
        if (now - lastPressTime < MIN_PRESS_INTERVAL_MS) {
            // 如果距离上次按下的时间小于500毫秒，则跳过本次操作
            return;
        }
        User32 user32 = User32.INSTANCE;

        // 按下左 Shift
        user32.keybd_event((byte) VK_LSHIFT, (byte) 0, 0, 0);
        System.out.println("按下 Shift");


        // 释放左 Shift
        user32.keybd_event((byte) VK_LSHIFT, (byte) 0, KEYEVENTF_KEYUP, 0);
        System.out.println("释放 Shift");
        lastPressTime = now;

    }



    @Override
    public void change() {
        long startTimeNano_l = System.nanoTime();
        try {
            pressShift();
        } finally {
            long nano_l = System.nanoTime()  - startTimeNano_l;
            double milliseconds_l = nano_l / 1e6;
            String msFormatted_l = String.format("%.6f",  milliseconds_l);
            System.out.println("  执行时间: " + msFormatted_l + " ms");
        }
    }


}