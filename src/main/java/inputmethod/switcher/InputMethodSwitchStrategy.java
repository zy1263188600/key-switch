package inputmethod.switcher;

import enums.InputState;

public interface InputMethodSwitchStrategy {
    void change();
    InputState getCurrentMode();
}
