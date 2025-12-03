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
    private static final String[] INPUT_METHOD_KEYWORDS = {
            "输入指示器", "中文模式", "英语模式", "输入模式", "语言栏"
    };

    private List<Element> buttons;
    private UIAutomation automation;
    private Element buttonCache;

    public WindowsUIAutomationSwitcher() {
        LogUtil.info("WindowsUIAutomationSwitcher  init");
    }

    public enum ErrorCode {
        COM_INIT_FAILED_STA, AUTOMATION_CREATE_FAILED, TRAY_WINDOW_NOT_FOUND,
        ELEMENT_FROM_HANDLE_FAILED, BUTTONS_NOT_FOUND, BUTTON_INVOKE_FAILED, NO_VALID_BUTTON
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

    @Override
    public InputState getCurrentMode() {
        return isEnglishMode() ? InputState.ENGLISH : InputState.CHINESE;
    }

    public boolean isEnglishMode() {
        try {
            return retryOperation(this::checkEnglishModeWithCache);
        } catch (Exception e) {
            LogUtil.warn("Failed  to get input mode via UI Automation, falling back to Imm32 API");
            return KeyboardSwitcher.isEnglishMode();
        }
    }

    @Override
    public void change() {
        long startTime = System.nanoTime();
        try {
            retryOperation(this::performButtonClick);
        } catch (UIAutomationSwitcherException e) {
            LOG.error("Input  method switch failed: " + e.getErrorCode(), e);
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1e6;
            LogUtil.info(" 执行时间: " + String.format("%.6f", durationMs) + " ms");
        }
    }

    // ======= 核心操作 =======
    private boolean performButtonClick() throws AutomationException {
        Element targetButton = findValidInputMethodButton();
        if (targetButton == null) {
            throw new UIAutomationSwitcherException(ErrorCode.NO_VALID_BUTTON,
                    "No valid input method button found");
        }
        doDefaultAction(targetButton);
        return true;
    }

    private Boolean checkEnglishModeWithCache() throws AutomationException {
        Element targetButton = findValidInputMethodButton();
        InputState state = matchInputMethodButton(targetButton);
        return state == InputState.ENGLISH;
    }

    // ======= 重试机制 =======
    private <T> T retryOperation(OperationSupplier<T> operation) {
        for (int retry = 0; retry <= MAX_RETRY_COUNT; retry++) {
            try {
                LogUtil.debug("RETRY_COUNT:" + (retry + 1));
                return operation.execute();
            } catch (Exception e) {
                resetCache();
                if (retry == MAX_RETRY_COUNT) {
                    throw e instanceof UIAutomationSwitcherException ?
                            (UIAutomationSwitcherException) e :
                            new UIAutomationSwitcherException(ErrorCode.AUTOMATION_CREATE_FAILED,
                                    "Operation failed after retries", e);
                }
                handleRetryException(e, retry);
            }
        }
        throw new UIAutomationSwitcherException(ErrorCode.AUTOMATION_CREATE_FAILED,
                "Operation failed after " + MAX_RETRY_COUNT + " retries");
    }

    private void handleRetryException(Exception e, int retryCount) {
        if (e.getMessage() != null && e.getMessage().contains("0x80040201")) {
            LogUtil.warn("Taskbar  resource invalid, retrying... Attempt: " + (retryCount + 1));
        } else if (e.getMessage().contains("RPC_E_CHANGED_MODE")) {
            throw new UIAutomationSwitcherException(ErrorCode.COM_INIT_FAILED_STA,
                    "COM initialization failed in STA mode", e);
        }
    }

    // ======= 缓存处理 =======
    private void resetCache() {
        buttons = null;
        automation = null;
        buttonCache = null;
    }

    private Element findValidInputMethodButton() throws AutomationException {
        // 尝试使用缓存按钮
        if (buttonCache != null && isValidInputMethodButton(buttonCache)) {
            return buttonCache;
        }

        // 获取新的按钮列表
        if (buttons == null || buttons.isEmpty()) {
            buttons = findInputMethodButtons();
        }

        // 优先检查特定位置（适配Win10(4)/Win11(5)）
        if (buttons.size() > 4) {
            Element button = buttons.get(buttons.size() - (buttons.size() > 5 ? 5 : 4));
            if (isValidInputMethodButton(button)) {
                buttonCache = button;
                return button;
            }
        }

        // 遍历查找有效按钮
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Element button = buttons.get(i);
            if (isValidInputMethodButton(button)) {
                buttonCache = button;
                return button;
            }
        }

        throw new UIAutomationSwitcherException(ErrorCode.NO_VALID_BUTTON,
                "No valid input method button in tray");
    }

    private List<Element> findInputMethodButtons() throws AutomationException {
        WinDef.HWND hTrayWnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (hTrayWnd == null) {
            throw new UIAutomationSwitcherException(ErrorCode.TRAY_WINDOW_NOT_FOUND,
                    "Tray window not found");
        }

        // 初始化自动化实例
        if (automation == null) {
            automation = UIAutomation.getInstance();
        }

        // 获取根元素
        Element root = automation.getElementFromHandle(hTrayWnd);
        if (root == null) {
            throw new UIAutomationSwitcherException(ErrorCode.ELEMENT_FROM_HANDLE_FAILED,
                    "Failed to get element from handle");
        }

        // 创建属性条件
        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
        variant.setValue(3, new WinDef.LONG(50000L));
        PointerByReference propertyCondition = automation.createPropertyCondition(30003, variant);

        // 查找按钮
        long startTime = System.nanoTime();
        try {
            List<Element> foundButtons = root.findAll(new TreeScope(TreeScope.DESCENDANTS), propertyCondition);
            if (foundButtons == null || foundButtons.isEmpty()) {
                throw new UIAutomationSwitcherException(ErrorCode.BUTTONS_NOT_FOUND,
                        "No buttons found in tray");
            }
            return foundButtons;
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1e6;
            LogUtil.debug("findAll  execution time: " + String.format("%.6f", durationMs) + " ms");
        }
    }

    // ======= 工具方法 =======
    private boolean isValidInputMethodButton(Element element) {
        try {
            return element != null && matchInputMethodButton(element) != InputState.NONE;
        } catch (AutomationException e) {
            LogUtil.warn("Failed  to validate button: " + e.getMessage());
            return false;
        }
    }

    private InputState matchInputMethodButton(Element element) throws AutomationException {
        if (element == null) return InputState.NONE;

        String name = element.getName();
        if (name == null || name.isEmpty()) return InputState.NONE;

        for (String keyword : INPUT_METHOD_KEYWORDS) {
            if (name.contains(keyword)) {
                if (name.contains("英") || name.contains("ENG")) {
                    return InputState.ENGLISH;
                }
                if (name.contains("中")) {
                    return InputState.CHINESE;
                }
                break;
            }
        }
        return InputState.NONE;
    }

    private void doDefaultAction(Element button) {
        try {
            IUIAutomationElement element = button.getElement();
            PointerByReference legacyPatternRef = new PointerByReference();
            element.getCurrentPattern(10018, legacyPatternRef);

            IUIAutomationLegacyIAccessiblePattern legacy =
                    IUIAutomationLegacyIAccessiblePatternConverter.pointerToInterface(legacyPatternRef);

            int result = legacy.doDefaultAction();
            LogUtil.debug("doDefaultAction  result: " + result);
        } catch (Exception e) {
            throw new UIAutomationSwitcherException(ErrorCode.BUTTON_INVOKE_FAILED,
                    "Failed to invoke button action", e);
        }
    }

    // 函数式接口支持
    @FunctionalInterface
    private interface OperationSupplier<T> {
        T execute() throws Exception;
    }
}