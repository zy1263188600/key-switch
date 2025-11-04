package inputmethod.switcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.WindowsUIAutomationSwitcher;
import utlis.LogUtil;

public class SwitcherStrategyFactory {
    private static final Map<String, Supplier<InputMethodSwitchStrategy>> STRATEGIES = new HashMap<>();
    private static final Map<String, InputMethodSwitchStrategy> STRATEGY_CACHE = new ConcurrentHashMap<>();

    static {
        LogUtil.info("strategy init");
        STRATEGIES.put("KeyboardSwitcher", KeyboardSwitcher::new);
        STRATEGIES.put("UIAutomationSwitcher", WindowsUIAutomationSwitcher::new);
    }


    public static InputMethodSwitchStrategy createStrategy(String className) {
        return STRATEGY_CACHE.computeIfAbsent(className, key -> {
            Supplier<InputMethodSwitchStrategy> supplier = STRATEGIES.get(key);
            if (supplier == null) {
                throw new IllegalArgumentException("Invalid strategy: " + className);
            }
            InputMethodSwitchStrategy instance = supplier.get();
            LogUtil.info("new strategy: " + key);
            return instance;
        });
    }
}