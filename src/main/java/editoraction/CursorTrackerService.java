package editoraction;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import enums.InputState;
import inputmethod.cursor.CursorHandle;
import inputmethod.switcher.InputMethodSwitcher;
import org.jetbrains.annotations.NotNull;
import utlis.LogUtil;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.APP)
public final class CursorTrackerService implements Disposable {
    private static final long EVENT_THRESHOLD = 100; // 事件过滤阈值（毫秒）
    private static final Character.UnicodeBlock[] CHINESE_BLOCKS = { // 中文相关Unicode区块
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
            Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    };
    private final CompositeDisposable composite = new CompositeDisposable();
    private final Map<Editor, Long> lastInputTimeMap = new ConcurrentHashMap<>();
    private final Map<Editor, Boolean> selectionStateMap = new ConcurrentHashMap<>();

    public CursorTrackerService() {
        EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        setupDocumentListener(editorEventMulticaster);
        setupCaretListener(editorEventMulticaster);
        setupMouseListener(editorEventMulticaster);
        setupEditorLifecycleListener();

        Disposer.register(this, this::disposeResources);
    }

    @Override
    public void dispose() {
        disposeResources();
    }

    private void disposeResources() {
        composite.dispose();
        lastInputTimeMap.clear();
        selectionStateMap.clear();
    }

    private void setupDocumentListener(EditorEventMulticaster multicaster) {
        multicaster.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                updateLastInputTime(event.getDocument());
            }
        }, composite);
    }

    private void updateLastInputTime(Document document) {
        long currentTime = System.currentTimeMillis();
        for (Editor editor : EditorFactory.getInstance().getEditors(document)) {
            lastInputTimeMap.put(editor, currentTime);
        }
    }

    private void setupCaretListener(EditorEventMulticaster multicaster) {
        multicaster.addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                handleCaretMovement(event);
            }
        }, composite);
    }

    private void handleCaretMovement(CaretEvent event) {
        //fix 延迟执行等待selectionModel.hasSelection()、selectionModel.getSelectionStart()/getSelectionEnd()更新最新的光标位置状态
        Editor editor = event.getEditor();
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        alarm.addRequest(() -> {
            if (editor.isDisposed()) {
                return;
            }
            if (isWithinSelectionBoundary(editor)) {
                LogUtil.debug(" 检测到文本选择，跳过输入法切换");
                return;
            }
            if (shouldProcessMovement(editor)) {
                switchInputMethodBasedOnChar(editor);
            }
        }, 10);
    }

    private boolean isWithinSelectionBoundary(Editor editor) {
        CaretModel caret = editor.getCaretModel();
        int currentOffset = caret.getOffset();
        int selStart = caret.getCurrentCaret().getSelectionStart();
        int selEnd = caret.getCurrentCaret().getSelectionEnd();
                /*
                 fix 修复选中文本后 鼠标首次单击其余区域不会切换
                 这里主要是KeyboardSwitcher模式会误触，UIAutomation影响不大
                 貌似是IDEA平台的BUG
                 selectionModel.hasSelection()好像没有正确返回是否在选取中 所以自行判断是否有选中状态
                 https://youtrack.jetbrains.com/issue/IDEA-381472/hasSelection-returns-true-in-CaretListener-after-moving-cursor-out-of-selection
                 -1 +1 表示判断当前光标是否紧跟随选择区域
                 */

                /*
                 fix 选中多行文本、并且含有中/英文字符 会频繁切换
                 这里主要是KeyboardSwitcher模式会误触，UIAutomation影响不大
                 selectionModel.getSelectionStart()/getSelectionEnd() 在多行选中时没有返回实时的选中位置
                 https://youtrack.jetbrains.com/issue/IJPL-216012/Selection-Model-Values-Not-Updated-Correctly-During-Multi-Line-Selection-in-CaretListener
                 */
        var flag = (currentOffset == selStart && selStart == selEnd);
        boolean b = !flag && currentOffset >= selStart - 1 && currentOffset <= selEnd + 1;
//        System.out.printf("""
//                offset:%d  | selStart:%d | selEnd:%d%n
//                """, currentOffset, selStart, selEnd);
//        System.out.println(b);
        return b;


    }

    private boolean shouldProcessMovement(Editor editor) {
        long lastInputTime = lastInputTimeMap.getOrDefault(editor, 0L);
        long elapsed = System.currentTimeMillis() - lastInputTime;
        if (elapsed > EVENT_THRESHOLD) {
            return true;
        }
        LogUtil.debug(" 抛弃光标移动事件（时间阈值）");
        return false;
    }

    private void setupMouseListener(EditorEventMulticaster multicaster) {
        multicaster.addEditorMouseListener(new EditorMouseListener() {
            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1) {
                    selectionStateMap.put(e.getEditor(), true);
                }
            }

            @Override
            public void mouseReleased(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() != MouseEvent.BUTTON1) return;

                Editor editor = e.getEditor();
                if (selectionStateMap.remove(editor) != null &&
                        editor.getSelectionModel().hasSelection()) {
                    switchInputMethodBasedOnChar(editor);
                }
            }
        }, composite);
    }

    private void setupEditorLifecycleListener() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                lastInputTimeMap.remove(editor);
                selectionStateMap.remove(editor);
            }
        }, composite);
    }


    private void switchInputMethodBasedOnChar(Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        String prefix = getPrefixText(editor, offset, 1);

        if (prefix.isEmpty()) {
            return;
        }

        InputState prevCharState = getCharacterState(prefix.charAt(0));
        InputState currentMode = InputMethodSwitcher.getCurrentMode();
//        LogUtil.info("前一个字符：" + prefix.charAt(0));
//        LogUtil.info("前一个字符状态：" + prevCharState);
//        LogUtil.info("当前输入法状态：" + currentMode);
        if (!prevCharState.equals(currentMode)) {
            LogUtil.info(" 触发输入法切换");
            InputMethodSwitcher.change();
            CursorHandle.change(editor, prevCharState);
        }
    }

    private static InputState getCharacterState(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        for (Character.UnicodeBlock chineseBlock : CHINESE_BLOCKS) {
            if (chineseBlock.equals(block)) {
                return InputState.CHINESE;
            }
        }
        return InputState.ENGLISH;
    }

    private String getPrefixText(Editor editor, int offset, int length) {
        if (offset <= 0 || length <= 0) {
            return "";
        }
        int start = Math.max(0, offset - length);
        Document doc = editor.getDocument();
        return doc.getText(new TextRange(start, offset));
    }
}