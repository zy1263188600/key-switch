//package inputmethod.switcher.impl;
//
//import com.sun.jna.platform.win32.*;
//import com.sun.jna.ptr.PointerByReference;
//import enums.InputState;
//import inputmethod.switcher.InputMethodSwitchStrategy;
//import mmarquee.automation.AutomationException;
//import mmarquee.automation.Element;
//import mmarquee.automation.UIAutomation;
//import mmarquee.uiautomation.IUIAutomationElement;
//import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePattern;
//import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePatternConverter;
//import mmarquee.uiautomation.TreeScope;
//import com.intellij.openapi.diagnostic.Logger;
//
//import java.util.List;
//
//public class Windows10LanguageBarUIAutomationSwitcher implements InputMethodSwitchStrategy {
//
//    private static final Logger LOG = Logger.getInstance(Windows10LanguageBarUIAutomationSwitcher.class);
//    private static final int MAX_RETRY_COUNT = 3;
//
//    public enum ErrorCode {
//        TRAY_WINDOW_NOT_FOUND,
//        ELEMENT_FROM_HANDLE_FAILED,
//        LANGUAGE_BAR_NOT_FOUND,
//        SWITCH_BUTTON_NOT_FOUND,
//        BUTTON_INVOKE_FAILED
//    }
//
//    public static class LanguageBarException extends RuntimeException {
//        private final ErrorCode errorCode;
//
//        public LanguageBarException(ErrorCode errorCode, String message) {
//            super(message);
//            this.errorCode  = errorCode;
//        }
//
//        public LanguageBarException(ErrorCode errorCode, String message, Throwable cause) {
//            super(message, cause);
//            this.errorCode  = errorCode;
//        }
//
//        public ErrorCode getErrorCode() {
//            return errorCode;
//        }
//    }
//
//    @Override
//    public void change() {
//        int retryCount = 1;
//
//        while (retryCount <= MAX_RETRY_COUNT) {
//            try {
//                UIAutomation automation = UIAutomation.getInstance();
//                WinDef.HWND taskbarHandle = getTaskbarWindow();
//                Element taskbarElement = automation.getElementFromHandle(taskbarHandle);
//                if (taskbarElement == null) {
//                    throw new LanguageBarException(
//                            ErrorCode.ELEMENT_FROM_HANDLE_FAILED,
//                            "Failed to retrieve taskbar element from handle"
//                    );
//                }
//                Element languageBar = findLanguageBar(automation, taskbarElement);
//                if (languageBar == null) {
//                    throw new LanguageBarException(
//                            ErrorCode.LANGUAGE_BAR_NOT_FOUND,
//                            "Language bar container not found"
//                    );
//                }
//                Element switchButton = findSwitchButton(automation, languageBar);
//                if (switchButton == null) {
//                    throw new LanguageBarException(
//                            ErrorCode.SWITCH_BUTTON_NOT_FOUND,
//                            "Chinese/English toggle button not found"
//                    );
//                }
//                clickButton(switchButton);
//                return;
//
//            } catch (LanguageBarException e) {
//                handleException(e, retryCount);
//                retryCount++;
//            } catch (Exception e) {
//                LOG.error("Unhandled  exception: " + e.getMessage(),  e);
//                retryCount++;
//            }
//        }
//
//        LOG.error("Language  bar toggle failed after " + MAX_RETRY_COUNT + " attempts");
//    }
//
//    private WinDef.HWND getTaskbarWindow() {
//        WinDef.HWND taskbarHandle = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
//        if (taskbarHandle == null) {
//            throw new LanguageBarException(
//                    ErrorCode.TRAY_WINDOW_NOT_FOUND,
//                    "Taskbar window not found"
//            );
//        }
//        return taskbarHandle;
//    }
//
//    private Element findLanguageBar(UIAutomation automation, Element taskbarElement)
//            throws AutomationException {
//
//        PointerByReference toolbarCondition = automation.createPropertyCondition(
//                30003,
//                createVariant(50021)
//        );
//
//        WTypes.BSTR bstr = OleAuto.INSTANCE.SysAllocString("语言栏");
//        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
//        variant.setValue(Variant.VT_BSTR,  bstr);
//        PointerByReference nameCondition = automation.createPropertyCondition(
//                30005,
//                variant
//        );
//        variant.clear();
//
//        PointerByReference andCondition = automation.createAndCondition(
//                toolbarCondition,
//                nameCondition
//        );
//
//
//        Variant.VARIANT.ByValue variant1 = new Variant.VARIANT.ByValue();
//        variant1.setValue(Variant.VT_BSTR,  OleAuto.INSTANCE.SysAllocString("Input Indicator"));
//        PointerByReference altNameCondition = automation.createPropertyCondition(
//                30005,
//                variant1
//        );
//
//        PointerByReference altCondition = automation.createAndCondition(
//                toolbarCondition,
//                altNameCondition
//        );
//
//        List<Element> toolbars = taskbarElement.findAll(
//                new TreeScope(4),
//                andCondition
//        );
//
//        if (toolbars == null || toolbars.isEmpty())  {
//            toolbars = taskbarElement.findAll(
//                    new TreeScope(4),
//                    altCondition
//            );
//        }
//
//        return (toolbars != null && !toolbars.isEmpty())  ? toolbars.get(0)  : null;
//    }
//
//    private Element findSwitchButton(UIAutomation automation, Element languageBar)
//            throws AutomationException {
//
//        // Create language bar search condition
//        PointerByReference buttonCondition = automation.createPropertyCondition(
//                30003,
//                createVariant(50021) // Language bar control type
//        );
//
//        List<Element> buttons = languageBar.findAll(
//                new TreeScope(4),
//                buttonCondition
//        );
//
//        if (buttons == null) return null;
//
//        for (Element button : buttons) {
//            String name = button.getName();
//            if (name == null) continue;
//
//            if (isSwitchButtonName(name)) {
//                return button;
//            }
//        }
//        return null;
//    }
//
//    private boolean isSwitchButtonName(String name) {
//        return name.contains("中/英");
//    }
//
//    private void clickButton(Element button) {
//        try {
//            IUIAutomationElement element = button.getElement();
//            PointerByReference patternRef = new PointerByReference();
//            element.getCurrentPattern(10018,  patternRef);
//
//            IUIAutomationLegacyIAccessiblePattern legacyPattern =
//                    IUIAutomationLegacyIAccessiblePatternConverter.pointerToInterface(patternRef);
//
//            legacyPattern.doDefaultAction();
//        } catch (Exception e) {
//            throw new LanguageBarException(
//                    ErrorCode.BUTTON_INVOKE_FAILED,
//                    "Button activation failed: " + e.getMessage(),
//                    e
//            );
//        }
//    }
//
//    private Variant.VARIANT.ByValue createVariant(Object value) {
//        Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
//
//        if (value instanceof Integer) {
//            variant.setValue(3,  new WinDef.LONG(((Integer) value).longValue()));
//        } else if (value instanceof String) {
//            variant.setValue(8,  (String) value);
//        }
//
//        return variant;
//    }
//
//    private void handleException(LanguageBarException e, int retryCount) {
//        LOG.warn(String.format("Language  bar operation exception (Retry %d/%d): %s",
//                retryCount, MAX_RETRY_COUNT, e.getMessage()));
//
//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException ie) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    @Override
//    public InputState getCurrentMode() {
//        // Use independent keyboard state detection logic
//        return KeyboardSwitcher.isEnglishMode()  ?
//                InputState.ENGLISH : InputState.CHINESE;
//    }
//}