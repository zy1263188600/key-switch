package inputmethod.switcher.impl;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;

/**
 * imm32.dll JNA 接口定义
 */
public interface MyImm32 extends StdCallLibrary {
    MyImm32 INSTANCE = Native.load("imm32", MyImm32.class);

    // 输入法转换模式常量
    int IME_CMODE_ALPHANUMERIC = 0x0000;
    int IME_CMODE_NATIVE = 0x0001;

    Pointer ImmGetContext(HWND hWnd);

    boolean ImmReleaseContext(HWND hWnd, Pointer hIMC);

    boolean ImmSetOpenStatus(Pointer hIMC, boolean fOpen);

    boolean ImmGetOpenStatus(Pointer hIMC);

    boolean ImmSetConversionStatus(Pointer hIMC, int dwConversion, int dwSentence);

    boolean ImmGetConversionStatus(Pointer hIMC, int[] lpdwConversion, int[] lpdwSentence);

    HWND ImmGetDefaultIMEWnd(HWND hWnd);
}
