package editoraction;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.editor.*;
import enums.InputState;
import inputmethod.InputMethodSwitcher;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@Service
public final class CursorTrackerService {

    // 存储每个编辑器最后输入的时间
    private final Map<Editor, Long> lastInputTimeMap = new HashMap<>();
    // 事件过滤阈值（毫秒）
    private static final long EVENT_THRESHOLD = 100;

    public CursorTrackerService() {
        CompositeDisposable composite = new CompositeDisposable();
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        // 1. 监听文档变化（输入事件）
        multicaster.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                Document document = event.getDocument();
                Editor[] editors = EditorFactory.getInstance().getEditors(document);
                long currentTime = System.currentTimeMillis();
                for (Editor editor : editors) {
                    lastInputTimeMap.put(editor, currentTime);
                }
            }
        }, composite);

        // 2. 监听光标移动
        multicaster.addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                Editor editor = event.getEditor();
                long lastInputTime = lastInputTimeMap.getOrDefault(editor, 0L);
                long currentTime = System.currentTimeMillis();

                // 如果最近没有输入事件，或者输入事件已经过去足够长时间
                if (currentTime - lastInputTime > EVENT_THRESHOLD) {
                    handleCursorMovement(event);
                }
            }
        }, composite);
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

//        boolean b = (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xFFE0 && c <= 0xFFEE);
        boolean b = Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
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