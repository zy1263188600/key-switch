package inputmethod.switcher;

import enums.InputState;
import inputmethod.switcher.impl.Windows11UIAutomationSwitcher;
import view.SettingsState;

public class InputMethodSwitcher {
    // 私有构造防止实例化
    private InputMethodSwitcher() {
    }

    public static InputState getCurrentMode() {
        try {
            SettingsState settingsState = SettingsState.getInstance();
            return SwitcherStrategyFactory.createStrategy(settingsState.inputSwitchStrategyClass).getCurrentMode();
        } catch (Exception e) {
            new Windows11UIAutomationSwitcher().getCurrentMode();
        }
        return InputState.ENGLISH;
    }

    public static void change() {
        try {
            SettingsState settingsState = SettingsState.getInstance();
            SwitcherStrategyFactory.createStrategy(settingsState.inputSwitchStrategyClass).change();
        } catch (Exception e) {
            new Windows11UIAutomationSwitcher().change();
        }
    }
}