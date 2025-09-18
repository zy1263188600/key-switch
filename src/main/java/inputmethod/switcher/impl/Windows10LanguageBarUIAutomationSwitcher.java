package inputmethod.switcher.impl;

import com.sun.jna.platform.win32.*;
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
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;

public class Windows10LanguageBarUIAutomationSwitcher implements InputMethodSwitchStrategy {

    private static final Logger LOG = Logger.getInstance(Windows10LanguageBarUIAutomationSwitcher.class);
    private static final int MAX_RETRY_COUNT = 3;

    public enum ErrorCode {
        TRAY_WINDOW_NOT_FOUND,
        ELEMENT_FROM_HANDLE_FAILED,
        LANGUAGE_BAR_NOT_FOUND,
        SWITCH_BUTTON_NOT_FOUND,
        BUTTON_INVOKE_FAILED
    }

    public static class LanguageBarException extends RuntimeException {
        private final ErrorCode errorCode;

        public LanguageBarException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode  = errorCode;
        }

        public LanguageBarException(ErrorCode errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode  = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    @Override
    public void change() {
        int retryCount = 0;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                UIAutomation automation = UIAutomation.getInstance();

                // 1. 获取任务栏窗口
                WinDef.HWND taskbarHandle = getTaskbarWindow();

                // 2. 获取任务栏元素
                Element taskbarElement = automation.getElementFromHandle(taskbarHandle);
                if (taskbarElement == null) {
                    throw new LanguageBarException(
                            ErrorCode.ELEMENT_FROM_HANDLE_FAILED,
                            "无法从句柄获取任务栏元素"
                    );
                }

                // 3. 查找语言栏容器
                Element languageBar = findLanguageBar(automation, taskbarElement);
                if (languageBar == null) {
                    throw new LanguageBarException(
                            ErrorCode.LANGUAGE_BAR_NOT_FOUND,
                            "未找到语言栏容器"
                    );
                }

                // 4. 查找切换按钮
                Element switchButton = findSwitchButton(automation, languageBar);
                if (switchButton == null) {
                    throw new LanguageBarException(
                            ErrorCode.SWITCH_BUTTON_NOT_FOUND,
                            "未找到中英文切换按钮"
                    );
                }

                // 5. 执行点击操作
                clickButton(switchButton);
                return;

            } catch (LanguageBarException e) {
                handleException(e, retryCount);
                retryCount++;
            } catch (Exception e) {
                LOG.error(" 未处理的异常: " + e.getMessage(),  e);
                retryCount++;
            }
        }

        LOG.error(" 语言栏切换失败，重试次数: " + MAX_RETRY_COUNT);
    }

    private WinDef.HWND getTaskbarWindow() {
        WinDef.HWND taskbarHandle = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (taskbarHandle == null) {
            throw new LanguageBarException(
                    ErrorCode.TRAY_WINDOW_NOT_FOUND,
                    "未找到任务栏窗口"
            );
        }
        return taskbarHandle;
    }

    private Element findLanguageBar(UIAutomation automation, Element taskbarElement)
            throws AutomationException {

        // 创建组合条件: 工具栏 + 特定名称
        PointerByReference toolbarCondition = automation.createPropertyCondition(
                30003, // ControlType 属性
                createVariant(50021)  // Toolbar 控件类型
        );

        WTypes.BSTR bstr = OleAuto.INSTANCE.SysAllocString("语言栏");
        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
        variant.setValue(Variant.VT_BSTR,  bstr);
        PointerByReference nameCondition = automation.createPropertyCondition(
                30005,
                variant
        );
        variant.clear();

        PointerByReference andCondition = automation.createAndCondition(
                toolbarCondition,
                nameCondition
        );


        Variant.VARIANT.ByValue variant1 = new Variant.VARIANT.ByValue();
        variant1.setValue(Variant.VT_BSTR,   OleAuto.INSTANCE.SysAllocString("输入指示器"));
        PointerByReference altNameCondition = automation.createPropertyCondition(
                30005,
                variant1
        );

        PointerByReference altCondition = automation.createAndCondition(
                toolbarCondition,
                altNameCondition
        );

        List<Element> toolbars = taskbarElement.findAll(
                new TreeScope(4),
                andCondition
        );

        if (toolbars == null || toolbars.isEmpty())  {
            toolbars = taskbarElement.findAll(
                    new TreeScope(4),
                    altCondition
            );
        }

        return (toolbars != null && !toolbars.isEmpty())  ? toolbars.get(0)  : null;
    }

//    private Element findSwitchButtonEnhanced(UIAutomation automation, Element languageBar)
//            throws AutomationException {
//
//        // 步骤1：打印语言栏基本信息（确认父容器）
//        System.out.println("\n=====  语言栏基本信息 =====");
//        System.out.println(" 名称: " + languageBar.getName());
//        System.out.println(" 控件类型: " + languageBar.getControlType());
//        System.out.println(" 自动化ID: " + languageBar.getAutomationId());
//
//        // 步骤2：打印所有子控件信息（核心调试）
//        System.out.println("\n=====  所有子控件信息 =====");
//        List<Element> allElements = languageBar.findAll(
//                new TreeScope(4),
//                automation.createTrueCondition()  // 无条件获取所有元素
//        );
//
//        if (allElements == null || allElements.isEmpty())  {
//            System.out.println("未找到任何子控件！");
//            return null;
//        }
//
//        // 打印控件信息表头
//        System.out.printf("%-8s  | %-30s | %-20s | %-15s | %-10s%n",
//                "类型ID", "控件名称", "自动化ID", "类名", "边界坐标");
//        System.out.println("----------------------------------------------------------------------");
//
//        // 遍历并打印每个控件详细信息
//        for (Element element : allElements) {
//            WinDef.RECT bounds = element.getBoundingRectangle();
//
//
//            System.out.printf("%-8d  | %-30s | %-20s | %-15s %n",
//                    element.getControlType(),
//                    StringUtils.abbreviate(element.getName(),  30),
//                    StringUtils.abbreviate(element.getAutomationId(),  20),
//                    StringUtils.abbreviate(element.getClassName(),  15)
//                    );
//        }
//
//        return null;
//    }

    private Element findSwitchButton(UIAutomation automation, Element languageBar)
            throws AutomationException {

        // 创建语言栏查找条件
        PointerByReference buttonCondition = automation.createPropertyCondition(
                30003,
                createVariant(50021) // 语言栏控件类型
        );

        List<Element> buttons = languageBar.findAll(
               new TreeScope(4),
                buttonCondition
        );

        if (buttons == null) return null;

        for (Element button : buttons) {
            String name = button.getName();
            if (name == null) continue;

            if (isSwitchButtonName(name)) {
                return button;
            }
        }
        return null;
    }

    private boolean isSwitchButtonName(String name) {
        return name.contains("中/英");
    }

    private void clickButton(Element button) {
        try {
            IUIAutomationElement element = button.getElement();
            PointerByReference patternRef = new PointerByReference();
            element.getCurrentPattern(10018,  patternRef); // LegacyIAccessible 模式

            IUIAutomationLegacyIAccessiblePattern legacyPattern =
                    IUIAutomationLegacyIAccessiblePatternConverter.pointerToInterface(patternRef);

            legacyPattern.doDefaultAction();
        } catch (Exception e) {
            throw new LanguageBarException(
                    ErrorCode.BUTTON_INVOKE_FAILED,
                    "按钮点击失败: " + e.getMessage(),
                    e
            );
        }
    }

    private Variant.VARIANT.ByValue createVariant(Object value) {
        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();

        if (value instanceof Integer) {
            variant.setValue(3,  new WinDef.LONG(((Integer) value).longValue()));
        } else if (value instanceof String) {
            variant.setValue(8,  (String) value);
        }

        return variant;
    }

    private void handleException(LanguageBarException e, int retryCount) {
        LOG.warn(String.format(" 语言栏操作异常 (重试 %d/%d): %s",
                retryCount + 1, MAX_RETRY_COUNT, e.getMessage()));

        try {
            Thread.sleep(200);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public InputState getCurrentMode() {
        // 使用独立的键盘状态检测逻辑 
        return KeyboardSwitcher.isEnglishMode()  ?
                InputState.ENGLISH : InputState.CHINESE;
    }
}