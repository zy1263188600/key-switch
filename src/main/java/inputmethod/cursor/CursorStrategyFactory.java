package inputmethod.cursor;

import inputmethod.cursor.impl.BalloonHandle;
import inputmethod.cursor.impl.CursorColorHandle;
import inputmethod.switcher.InputMethodSwitchStrategy;
import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.WindowsUIAutomationSwitcher;
import utlis.LogUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class CursorStrategyFactory {
    private static final Map<String, Supplier<CursorHandleStrategy>> STRATEGIES =
            Map.of(
                    "CursorColorStrategy", CursorColorHandle::new,
                    "BalloonStrategy", BalloonHandle::new
            );

    private static final Map<String, CursorHandleStrategy> STRATEGY_CACHE = new ConcurrentHashMap<>();

    public static CursorHandleStrategy createStrategy(String className) {
        return STRATEGY_CACHE.computeIfAbsent(className, key -> {
            Supplier<CursorHandleStrategy> supplier = STRATEGIES.get(key);
            if (supplier == null) {
                throw new IllegalArgumentException("Invalid Cursor Strategy: " + className);
            }
            CursorHandleStrategy instance = supplier.get();
            LogUtil.info("New Cursor Strategy: " + key);
            return instance;
        });
    }
}