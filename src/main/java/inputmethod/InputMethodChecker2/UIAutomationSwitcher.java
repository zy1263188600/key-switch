package inputmethod.InputMethodChecker2;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.PointerByReference;
import enums.InputState;
import inputmethod.InputMethodSwitchStrategy;
import mmarquee.automation.AutomationException;
import mmarquee.automation.Element;
import mmarquee.automation.UIAutomation;
import mmarquee.uiautomation.IUIAutomationElement;
import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePattern;
import mmarquee.uiautomation.IUIAutomationLegacyIAccessiblePatternConverter;
import mmarquee.uiautomation.TreeScope;

import java.util.List;

public class UIAutomationSwitcher implements InputMethodSwitchStrategy {

    public static final int SUCCESS = 0;
    public static final int COM_INIT_FAILED_STA = 1;
    public static final int AUTOMATION_CREATE_FAILED = 2;
    public static final int TRAY_WINDOW_NOT_FOUND = 3;
    public static final int ELEMENT_FROM_HANDLE_FAILED = 4;
    public static final int BUTTONS_NOT_FOUND = 5;
    public static final int BUTTON_INVOKE_FAILED = 6;
    public static final int NO_VALID_BUTTON = 7;

    public static List<Element> buttons;
    public static Element buttonCache;

    public static int clickInputMethodButton() {
        try {
            UIAutomation automation = UIAutomation.getInstance();
            WinDef.HWND hTrayWnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
            if (hTrayWnd == null) {
                return TRAY_WINDOW_NOT_FOUND;
            }
            Element root = automation.getElementFromHandle(hTrayWnd);
            if (root == null) {
                return ELEMENT_FROM_HANDLE_FAILED;
            }
            Variant.VARIANT.ByValue variant = new Variant.VARIANT.ByValue();
            variant.setValue(3, new WinDef.LONG(50000L));
            PointerByReference propertyCondition = automation.createPropertyCondition(30003, variant);

            if (buttons == null) {
                long startTimeNano_l = System.nanoTime();
                try {
                    buttons = root.findAll(new TreeScope(TreeScope.DESCENDANTS), propertyCondition);
                } finally {
                    long nano_l = System.nanoTime() - startTimeNano_l;
                    double milliseconds_l = nano_l / 1e6;
                    String msFormatted_l = String.format("%.6f", milliseconds_l);
                    System.out.println("findAll执行时间: " + msFormatted_l + " ms");
                }
                if (buttons == null || buttons.isEmpty()) {
                    return BUTTONS_NOT_FOUND;
                }
            }

            if (buttons.size() > 5) {
                Element element = buttons.get(buttons.size() - 5);
                String name = element.getName();
                if (name != null && isInputMethodButton(name)) {
                    try {
                        doDefaultAction(element);
                        return SUCCESS;
                    } catch (Exception e) {
                        return BUTTON_INVOKE_FAILED;
                    }
                }
            }
            // 逆向遍历按钮 输入法一般都在倒数位次 会快一点
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Element element = buttons.get(i);
                String name = element.getName();
                if (name != null && isInputMethodButton(name)) {
                    try {
                        doDefaultAction(element);
                        return SUCCESS;
                    } catch (Exception e) {
                        return BUTTON_INVOKE_FAILED;
                    }
                }
            }
            return NO_VALID_BUTTON;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("RPC_E_CHANGED_MODE")) {
                return COM_INIT_FAILED_STA;
            }
            return AUTOMATION_CREATE_FAILED;
        }
    }

    @Override
    public InputState getCurrentMode() {
        return isEnglishMode() ? InputState.ENGLISH : InputState.CHINESE;
    }

    public static boolean isEnglishMode() {
        if (buttonCache != null) {
            try {
                return buttonCache.getCachedName().contains("英文");
            } catch (AutomationException ignored) {

            }
        }
        return false;
    }

    private static void doDefaultAction(Element button) {
        buttonCache = button;
        IUIAutomationElement element = button.getElement();
        PointerByReference legacyPatternRef = new PointerByReference();
        element.getCurrentPattern(10018, legacyPatternRef);
        IUIAutomationLegacyIAccessiblePattern legacy = IUIAutomationLegacyIAccessiblePatternConverter.pointerToInterface(legacyPatternRef);
        legacy.doDefaultAction();
    }

    private static boolean isInputMethodButton(String name) {
        return name.contains(" 输入指示器") ||
                name.contains(" 输入模式") ||
                name.contains(" 语言栏") ||
                name.contains("ENG") ||
                name.contains("CHS") ||
                name.contains(" 中") ||
                name.contains(" 英");
    }

    @Override
    public void change() {
        long startTimeNano_l = System.nanoTime();
        try {
            clickInputMethodButton();
        } finally {
            long nano_l = System.nanoTime() - startTimeNano_l;
            double milliseconds_l = nano_l / 1e6;
            String msFormatted_l = String.format("%.6f", milliseconds_l);
            System.out.println("  执行时间: " + msFormatted_l + " ms");
        }
    }
}