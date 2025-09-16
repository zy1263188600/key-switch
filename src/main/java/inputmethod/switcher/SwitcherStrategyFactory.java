package inputmethod.switcher;

import java.util.Map;
import java.util.function.Supplier;

import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.UIAutomationSwitcher;

public class SwitcherStrategyFactory {
    private static final Map<String, Supplier<InputMethodSwitchStrategy>> STRATEGIES =
        Map.of( 
            "UIAutomationSwitcher", UIAutomationSwitcher::new,
            "KeyboardSwitcher", KeyboardSwitcher::new
        );
 
    public static InputMethodSwitchStrategy createStrategy(String className) {
        Supplier<InputMethodSwitchStrategy> supplier = STRATEGIES.get(className); 
        if (supplier == null) {
            throw new IllegalArgumentException("无效策略: " + className);
        }
        return supplier.get(); 
    }
}