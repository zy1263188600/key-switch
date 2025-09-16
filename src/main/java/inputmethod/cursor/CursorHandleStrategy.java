package inputmethod.cursor;

import com.intellij.openapi.editor.Editor;
import enums.InputState;

public interface CursorHandleStrategy {
    void change(Editor editor, InputState state);
}
