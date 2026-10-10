package inputmethod.switcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SwitcherStrategyFactoryTest {

    @Test
    public void createsImmDirectSwitcher() {
        InputMethodSwitchStrategy strategy = SwitcherStrategyFactory.createStrategy("ImmDirectSwitcher");
        assertNotNull(strategy);
        assertEquals("ImmDirectSwitcher", strategy.getClass().getSimpleName());
    }

    @Test
    public void keepsLegacyStrategies() {
        assertNotNull(SwitcherStrategyFactory.createStrategy("KeyboardSwitcher"));
        assertNotNull(SwitcherStrategyFactory.createStrategy("UIAutomationSwitcher"));
    }
}
