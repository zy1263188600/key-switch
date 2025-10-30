package editoraction;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.editor.*;
import enums.InputState;
import inputmethod.cursor.CursorHandle;
import inputmethod.switcher.InputMethodSwitcher;
import org.jetbrains.annotations.NotNull;
import utlis.LogUtil;

import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class CursorTrackerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(CursorTrackerService.class);

    private final CompositeDisposable composite = new CompositeDisposable();

    // 存储每个编辑器最后输入的时间
    private final Map<Editor, Long> lastInputTimeMap = new ConcurrentHashMap<>();
    // 存储编辑器上次选择状态
    private final Map<Editor, Boolean> lastSelectionStateMap = new ConcurrentHashMap<>();
    // 事件过滤阈值（毫秒）
    private static final long EVENT_THRESHOLD = 100;

    @Override
    public void dispose() {
        composite.dispose();
        lastInputTimeMap.clear();
    }

    public CursorTrackerService() {
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        // 监听文档变化（输入事件）
        multicaster.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                LogUtil.info("文档变化");
                Document document = event.getDocument();
                Editor[] editors = EditorFactory.getInstance().getEditors(document);
                long currentTime = System.currentTimeMillis();
                for (Editor editor : editors) {
                    lastInputTimeMap.put(editor, currentTime);
                }
            }
        }, composite);

        // 监听光标移动
        multicaster.addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                LogUtil.info("光标移动");
                Editor editor = event.getEditor();
                SelectionModel selectionModel = editor.getSelectionModel();
                CaretModel caretModel = editor.getCaretModel();
                // 获取当前光标位置和选区范围
                int currentOffset = caretModel.getOffset();
                int selStart = selectionModel.getSelectionStart();
                int selEnd = selectionModel.getSelectionEnd();
                //-1 表示判断当前光标是否紧跟随选择区域
                // 貌似是IDEA平台的BUG　
                // selectionModel.hasSelection()好像没有正确返回是否在选取中 所以自行判断是否有选中状态
                // https://youtrack.jetbrains.com/issue/IDEA-381472/hasSelection-returns-true-in-CaretListener-after-moving-cursor-out-of-selection
                currentOffset = currentOffset - 1;
                // fix 修复选中文本后 鼠标首次单击其余区域不会切换输入法
                if (currentOffset >= selStart && currentOffset <= selEnd
                ) {
                    LogUtil.info(" 检测到文本选择，跳过输入法切换");
                    return;
                }
                long lastInputTime = lastInputTimeMap.getOrDefault(editor, 0L);
                long currentTime = System.currentTimeMillis();
                // 如果最近没有输入事件，或者输入事件已经过去足够长时间
                if (currentTime - lastInputTime > EVENT_THRESHOLD) {
                    handleCursorMovement(event);
                } else {
                    LogUtil.info("抛弃光标移动事件");
                }
            }
        }, composite);

        // 监听光标移动
//        multicaster.addCaretListener(new CaretListener() {
//            @Override
//            public void caretPositionChanged(@NotNull CaretEvent event) {
//                System.out.println("Selection  state: " + event.getEditor().getSelectionModel().hasSelection()); // Incorrectly true
//            }
//        }, composite);

        //监听鼠标拖动选择事件
        multicaster.addEditorMouseListener(new EditorMouseListener() {
            @Override
            public void mouseReleased(@NotNull EditorMouseEvent e) {
                LogUtil.info("鼠标释放");
                if (e.getMouseEvent().getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                Editor editor = e.getEditor();
                SelectionModel selectionModel = editor.getSelectionModel();
                Boolean remove = lastSelectionStateMap.remove(e.getEditor());
                if (selectionModel.hasSelection() && remove) {
                    handleDragEnd(editor);
                    lastSelectionStateMap.remove(e.getEditor());
                }
            }

            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                LogUtil.info("鼠标按下");
                if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1) {
                    lastSelectionStateMap.put(e.getEditor(), true);
                }
            }
        }, composite);

    }

    private void handleDragEnd(Editor editor) {
        LogUtil.info("文本选择触发切换");
        if (getFocusedEditor() != editor) {
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        String prefixText = getPrefixText(editor, offset, 1);

        if (!prefixText.isEmpty()) {
            InputState prefixState = isChineseCharacter(prefixText.charAt(0));
            InputState currentMode = InputMethodSwitcher.getCurrentMode();

            if (!prefixState.equals(currentMode)) {
                InputMethodSwitcher.change();
                CursorHandle.change(editor, prefixState);
            }
        }
    }

    private void handleCursorMovement(CaretEvent event) {
        LogUtil.info("触发切换");
        Editor editor = getFocusedEditor();
        if (editor == null || editor != event.getEditor()) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        String prefixText = getPrefixText(editor, offset, 1);
        if (prefixText.isEmpty()) {
            return;
        }
        InputState prefixTextState = isChineseCharacter(prefixText.charAt(0));
        LogUtil.info("前一个字符：" + prefixText);
        LogUtil.info("前一个字符状态：" + prefixTextState);
        InputState currentMode = InputMethodSwitcher.getCurrentMode();
        LogUtil.info("输入法当前状态：" + currentMode);
        if (!prefixTextState.equals(currentMode)) {
            LogUtil.info("切换");
            InputMethodSwitcher.change();
            CursorHandle.change(editor, prefixTextState);
        } else {
            LogUtil.info("不切换");
        }
        InputState currentMode1 = InputMethodSwitcher.getCurrentMode();
        LogUtil.info("输入法最终状态：" + currentMode1);
    }

    //判断是中文还是英文
    private static InputState isChineseCharacter(char c) {
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