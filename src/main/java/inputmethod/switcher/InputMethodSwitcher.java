package inputmethod.switcher;

import enums.InputState;
import inputmethod.switcher.impl.WindowsUIAutomationSwitcher;
import utlis.LogUtil;
import view.SettingsState;

public class InputMethodSwitcher {
    // 私有构造防止实例化
    private InputMethodSwitcher() {
    }

    public static InputState getCurrentMode() {
        SettingsState settingsState = SettingsState.getInstance();
        return SwitcherStrategyFactory.createStrategy(settingsState.inputSwitchStrategyClass).getCurrentMode();
    }

    public static void change() {
        SettingsState settingsState = SettingsState.getInstance();
        SwitcherStrategyFactory.createStrategy(settingsState.inputSwitchStrategyClass).change();
    }
}