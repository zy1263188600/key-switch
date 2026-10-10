package inputmethod.switcher.impl;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;

/**
 * user32.dll JNA 接口定义
 */
public interface MyUser32 extends StdCallLibrary {
    MyUser32 INSTANCE = Native.load("user32", MyUser32.class);

    HWND GetForegroundWindow();

    HWND GetFocus();

    /**
     * 获取系统所有已加载的键盘布局列表
     */
    int GetKeyboardLayoutList(int nBuff, Pointer lpList);

    /**
     * 卸载指定的键盘布局
     */
    boolean UnloadKeyboardLayout(Pointer hkl);

    /**
     * 向窗口发送 Windows 消息
     */
    int SendMessageW(HWND hWnd, int Msg, int wParam, int lParam);
}
