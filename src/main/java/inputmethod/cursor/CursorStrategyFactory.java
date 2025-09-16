package inputmethod.cursor;

import inputmethod.cursor.impl.BalloonHandle;
import inputmethod.cursor.impl.CursorColorHandle;
import inputmethod.switcher.InputMethodSwitchStrategy;
import inputmethod.switcher.impl.KeyboardSwitcher;
import inputmethod.switcher.impl.UIAutomationSwitcher;

import java.util.Map;
import java.util.function.Supplier;

public class CursorStrategyFactory {
    private static final Map<String, Supplier<CursorHandleStrategy>> STRATEGIES =
        Map.of( 
            "CursorColorStrategy", CursorColorHandle::new,
            "BalloonStrategy", BalloonHandle::new
        );
 
    public static CursorHandleStrategy createStrategy(String className) {
        Supplier<CursorHandleStrategy> supplier = STRATEGIES.get(className);
        if (supplier == null) {
            throw new IllegalArgumentException("无效策略: " + className);
        }
        return supplier.get(); 
    }
}