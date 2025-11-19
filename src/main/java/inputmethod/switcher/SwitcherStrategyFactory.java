package inputmethod.switcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import inputmethod.cursor.impl.BalloonHandle;
import inputmethod.cursor.impl.CursorColorHandle;
import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.WindowsUIAutomationSwitcher;
import utlis.LogUtil;

public class SwitcherStrategyFactory {
    private static final Map<String, Supplier<InputMethodSwitchStrategy>> STRATEGIES =       Map.of(
            "KeyboardSwitcher", KeyboardSwitcher::new,
            "UIAutomationSwitcher", WindowsUIAutomationSwitcher::new
    );
    private static final Map<String, InputMethodSwitchStrategy> STRATEGY_CACHE = new ConcurrentHashMap<>();


    public static InputMethodSwitchStrategy createStrategy(String className) {
        return STRATEGY_CACHE.computeIfAbsent(className, key -> {
            Supplier<InputMethodSwitchStrategy> supplier = STRATEGIES.get(key);
            if (supplier == null) {
                throw new IllegalArgumentException("Invalid Switcher Strategy: " + className);
            }
            InputMethodSwitchStrategy instance = supplier.get();
            LogUtil.info("New Strategy: " + key);
            return instance;
        });
    }
}