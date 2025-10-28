package inputmethod.switcher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.Windows11UIAutomationSwitcher;
import inputmethod.switcher.impl.Windows10LanguageBarUIAutomationSwitcher;
import utlis.WindowsVersionUtil;

public class SwitcherStrategyFactory {
    private static final Map<String, Supplier<InputMethodSwitchStrategy>> STRATEGIES;

    static {
        STRATEGIES = new HashMap<>();
        STRATEGIES.put("KeyboardSwitcher", KeyboardSwitcher::new);
        if (WindowsVersionUtil.isWindows11()) {
            STRATEGIES.put("UIAutomationSwitcher", Windows11UIAutomationSwitcher::new);
        } else  {
            STRATEGIES.put("UIAutomationSwitcher", Windows11UIAutomationSwitcher::new);
        }

    }


    public static InputMethodSwitchStrategy createStrategy(String className) {
        Supplier<InputMethodSwitchStrategy> supplier = STRATEGIES.get(className);
        if (supplier == null) {
            throw new IllegalArgumentException("无效策略: " + className);
        }
        return supplier.get();
    }
}