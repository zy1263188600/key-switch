package inputmethod;

import enums.InputState;
import inputmethod.InputMethodChecker2.UIAutomationSwitcher;
import view.SettingsState;

public class InputMethodSwitcher {
    // 私有构造防止实例化
    private InputMethodSwitcher() {
    }

    public static InputState getCurrentMode() {
        try {
            SettingsState settingsState = SettingsState.getInstance();
            return StrategyFactory.createStrategy(settingsState.strategyClass).getCurrentMode();
        } catch (Exception e) {
            new UIAutomationSwitcher().change();
        }
        return InputState.ENGLISH;
    }

    public static void change() {
        try {
            SettingsState settingsState = SettingsState.getInstance();
            StrategyFactory.createStrategy(settingsState.strategyClass).change();
        } catch (Exception e) {
            new UIAutomationSwitcher().change();
        }
    }
}