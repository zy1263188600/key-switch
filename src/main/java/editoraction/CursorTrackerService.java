package editoraction;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import enums.InputState;
import inputmethod.InputMethodSwitcher;
import org.jetbrains.annotations.NotNull;

@Service
public final class CursorTrackerService {

    // 注册全局光标监听器
    public CursorTrackerService() {
        EditorFactory.getInstance().getEventMulticaster().addCaretListener(
                new CaretListener() {
                    @Override
                    public void caretPositionChanged(@NotNull CaretEvent event) {
                        handleCursorMovement(event);
                    }
                }
        );
    }

    private void handleCursorMovement(CaretEvent event) {
        Editor editor = getFocusedEditor();
        if (editor == null || editor != event.getEditor()) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        String prefixText = getPrefixText(editor, offset, 1);
        InputState chineseCharacter = isChineseCharacter(prefixText.charAt(0));
        System.out.println("前一个字符：" + prefixText);
        System.out.println("前一个字符状态：" + chineseCharacter);
        InputState currentMode = InputMethodSwitcher.getCurrentMode();
        System.out.println("输入法当前状态：" + currentMode);
        if (!chineseCharacter.equals(currentMode)) {
            System.out.println("切换");
            InputMethodSwitcher.change();
        }
        InputState currentMode1 = InputMethodSwitcher.getCurrentMode();
        System.out.println("输入法最终状态：" + currentMode1);
    }

    //判断是中文还是英文
    private static InputState isChineseCharacter(char c) {
        // 汉字范围: Unicode 4E00-9FFF（基本汉字）
        // 全角符号范围: FFE0-FFEE（全角字符块）
        boolean b = (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xFFE0 && c <= 0xFFEE);
        if (b) {
            return InputState.CHINESE;
        } else {
            return InputState.ENGLISH;
        }
    }

    // 获取当前焦点编辑器 
    private Editor getFocusedEditor() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor != null && editor.getContentComponent().hasFocus()) {
                return editor;
            }
        }
        return null;
    }

    // 获取光标前指定长度的文本 
    private String getPrefixText(Editor editor, int offset, int length) {
        if (offset <= 0) {
            return "";
        }

        int start = Math.max(0, offset - length);
        return editor.getDocument().getText().substring(start, offset);
    }
}