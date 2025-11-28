package inputmethod.switcher.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.PointerByReference;
import enums.InputState;
import inputmethod.switcher.InputMethodSwitchStrategy;
import mmarquee.automation.AutomationException;
import mmarquee.automation.Element;
import mmarquee.automation.UIAutomation;
import mmarquee.uiautomation.IUIAutomationElement;
import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePattern;
import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePatternConverter;
import mmarquee.uiautomation.TreeScope;
import utlis.LogUtil;

import java.util.List;

public class WindowsUIAutomationSwitcher implements InputMethodSwitchStrategy {

    private static final Logger LOG = Logger.getInstance(WindowsUIAutomationSwitcher.class);
    private static final int MAX_RETRY_COUNT = 1;

    public WindowsUIAutomationSwitcher() {
        LogUtil.info("WindowsUIAutomationSwitcher init");
    }

    public enum ErrorCode {
        COM_INIT_FAILED_STA, AUTOMATION_CREATE_FAILED, TRAY_WINDOW_NOT_FOUND, ELEMENT_FROM_HANDLE_FAILED, BUTTONS_NOT_FOUND, BUTTON_INVOKE_FAILED, NO_VALID_BUTTON
    }

    public static class UIAutomationSwitcherException extends RuntimeException {
        private final ErrorCode errorCode;

        public UIAutomationSwitcherException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public UIAutomationSwitcherException(ErrorCode errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    private List<Element> buttons;
    private UIAutomation automation;
    private Element buttonCache;

    private void clickInputMethodButton() {
        int retryCount = 1;

        while (retryCount <= MAX_RETRY_COUNT) {
            if (retryCount == 1) {
                LogUtil.debug("RETRY_COUNT:" + retryCount);
            }
            try {
                if (buttons == null) {
                    if (automation == null) {
                        automation = UIAutomation.getInstance();
                        WinDef.HWND hTrayWnd = findTrayWindow();
                        if (hTrayWnd == null) {
                            throw new UIAutomationSwitcherException(ErrorCode.TRAY_WINDOW_NOT_FOUND, "Tray window not found");
                        }

                        Element root = automation.getElementFromHandle(hTrayWnd);
                        if (root == null) {
                            throw new UIAutomationSwitcherException(ErrorCode.ELEMENT_FROM_HANDLE_FAILED, "Failed to get element from handle");
                        }

                        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
                        variant.setValue(3, new WinDef.LONG(50000L));
                        PointerByReference propertyCondition = automation.createPropertyCondition(30003, variant);
                        long startTimeNano = System.nanoTime();
                        try {
                            buttons = root.findAll(new TreeScope(TreeScope.DESCENDANTS), propertyCondition);
                        } finally {
                            long durationNano = System.nanoTime() - startTimeNano;
                            double milliseconds = durationNano / 1e6;
                            LogUtil.debug("findAll execution time: " + String.format("%.6f", milliseconds) + " ms");
                        }
                    }
                }
                if (buttons == null || buttons.isEmpty()) {
                    throw new UIAutomationSwitcherException(ErrorCode.BUTTONS_NOT_FOUND, "No buttons found");
                }
                //win10
                if (buttons.size() > 4) {
                    Element element = buttons.get(buttons.size() - 4);
                    if (isValidInputMethodButton(element)) {
                        doDefaultAction(element);
                        return;
                    }
                }
                //win11
                if (buttons.size() > 5) {
                    Element element = buttons.get(buttons.size() - 5);
                    if (isValidInputMethodButton(element)) {
                        doDefaultAction(element);
                        return;
                    }
                }

                for (int i = buttons.size() - 1; i >= 0; i--) {
                    Element element = buttons.get(i);
                    if (isValidInputMethodButton(element)) {
                        doDefaultAction(element);
                        return;
                    }
                }

                throw new UIAutomationSwitcherException(ErrorCode.NO_VALID_BUTTON, "No valid input method button found");

            } catch (Exception e) {
                handleException(e, retryCount);
                retryCount++;
            }
        }

        throw new UIAutomationSwitcherException(ErrorCode.AUTOMATION_CREATE_FAILED, "Operation failed after " + MAX_RETRY_COUNT + " retries");
    }

    private void handleException(Exception e, int retryCount) {
        buttons = null;
        automation = null;
        if (e instanceof UIAutomationSwitcherException) {
            throw (UIAutomationSwitcherException) e;
        }

        String message = e.getMessage();
        if (message != null) {
            if (message.contains("RPC_E_CHANGED_MODE")) {
                throw new UIAutomationSwitcherException(ErrorCode.COM_INIT_FAILED_STA, "COM initialization failed in STA mode", e);
            } else if (message.contains("0x80040201")) {
                LOG.warn("Taskbar  resource invalid, retrying... Attempt: " + (retryCount + 1));
                return;
            }
        }
        throw new UIAutomationSwitcherException(ErrorCode.AUTOMATION_CREATE_FAILED, "Unexpected error during automation", e);
    }

    private WinDef.HWND findTrayWindow() {
        WinDef.HWND hWnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (hWnd != null) {
            LogUtil.debug("Found tray window: ");
            return hWnd;
        }
        return null;
    }

    private boolean isValidInputMethodButton(Element element) throws AutomationException {
        String name = element.getName();
        return name != null && (
                // win10
                name.contains("输入指示器") ||
                        name.contains("中文模式") ||
                        name.contains("英语模式") ||
                        // win11
                        name.contains("输入模式") ||
                        name.contains("语言栏"));
    }

    @Override
    public InputState getCurrentMode() {
        return isEnglishMode() ? InputState.ENGLISH : InputState.CHINESE;
    }

    public boolean isEnglishMode() {
        if (buttonCache != null) {

        }
        return KeyboardSwitcher.isEnglishMode();
    }

    private void doDefaultAction(Element button) {
        try {
            IUIAutomationElement element = button.getElement();
            PointerByReference legacyPatternRef = new PointerByReference();
            element.getCurrentPattern(10018, legacyPatternRef);
            IUIAutomationLegacyIAccessiblePattern legacy = IUIAutomationLegacyIAccessiblePatternConverter.pointerToInterface(legacyPatternRef);
            int i = legacy.doDefaultAction();
            LogUtil.debug("doDefaultAction:" + i);
        } catch (Exception e) {
            throw new UIAutomationSwitcherException(ErrorCode.BUTTON_INVOKE_FAILED, "Failed to invoke button action", e);
        }
    }

    @Override
    public void change() {
        long startTimeNano = System.nanoTime();
        try {
            clickInputMethodButton();
        } catch (UIAutomationSwitcherException e) {
            LOG.error("Input  method switch failed: " + e.getErrorCode(), e);
        } finally {
            long durationNano = System.nanoTime() - startTimeNano;
            double milliseconds = durationNano / 1e6;
            LogUtil.info("  执行时间: " + String.format("%.6f", milliseconds) + " ms");
        }
    }
}