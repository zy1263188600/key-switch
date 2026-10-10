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
import utils.LogUtil;

import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.concurrent.*;

@Service(Service.Level.APP)
public final class CursorTrackerService implements Disposable {
    private static final long EVENT_THRESHOLD = 100; // 事件过滤阈值（毫秒）

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
            //光标变化
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                handleCaretMovement(event.getEditor());
            }
        }, composite);
    }

    private void setupMouseListener(EditorEventMulticaster multicaster) {
        multicaster.addEditorMouseListener(new EditorMouseListener() {
            //鼠标按下
            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1) {
                    selectionStateMap.put(e.getEditor(), true);
                }
            }

            //鼠标释放
            @Override
            public void mouseReleased(@NotNull EditorMouseEvent e) {
                if (e.getMouseEvent().getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                Editor editor = e.getEditor();
                if (selectionStateMap.remove(editor) != null &&
                        editor.getSelectionModel().hasSelection()) {
                    switchInputOnChar(editor);
                }
            }
        }, composite);
    }

    //编辑器变化监听
    private void setupEditorLifecycleListener() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            //关闭编辑器时
            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                lastInputTimeMap.remove(editor);
                selectionStateMap.remove(editor);
            }
        }, composite);

    }

    private void handleCaretMovement(Editor editor) {
        //fix 延迟执行等待selectionModel.hasSelection()、selectionModel.getSelectionStart()/getSelectionEnd()更新最新的光标位置状态
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
                switchInputOnChar(editor);
            }
        }, 1);
    }

    private boolean isWithinSelectionBoundary(Editor editor) {
        CaretModel caret = editor.getCaretModel();
        int currentOffset = caret.getOffset();
        int selStart = caret.getCurrentCaret().getSelectionStart();
        int selEnd = caret.getCurrentCaret().getSelectionEnd();
        LogUtil.debug(String.format("offset:%d | selStart:%d | selEnd:%d", currentOffset, selStart, selEnd));
        return CaretDecision.isWithinSelectionBoundary(currentOffset, selStart, selEnd);
    }

    private boolean switchInputOnState(InputState state) {
        InputState currentMode = InputMethodSwitcher.getCurrentMode();
        LogUtil.debug("当前输入法状态：" + currentMode);
        boolean b = !state.equals(currentMode);
        if (b) {
            LogUtil.info(" 触发输入法切换");
            InputMethodSwitcher.change();
        }
        return b;
    }

    private void switchInputOnChar(Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        String prefix = getPrefixText(editor, offset, 1);
        if (prefix.isEmpty()) {
            return;
        }
        InputState prevCharState = CaretDecision.charStateOf(prefix.charAt(0));
        LogUtil.debug("前一个字符：" + prefix.charAt(0));
        LogUtil.debug("前一个字符状态：" + prevCharState);
        if (switchInputOnState(prevCharState)) {
            CursorHandle.change(editor, prevCharState);
        }
    }

    //因为每次汉字输入候选框时在编辑器中有字母字符，这里就必须丢弃键盘事件触发的光标移动，不然会出现打不了汉字，刚输入汉字就被切换为英文输入法了
    //    IDEA平台存在BUG
    //    Ctrl+V（粘贴）、Ctrl+X（选中文本时剪贴、没选择文本时删除当前行）、Ctrl+Z（撤销）、Ctrl+SHIFT+Z（重做）
    //    Ctrl+Alt+Enter（换行并将光标移动至上一行）
    //    并没有触发光标移动
    //todo 上述没有触发光标移动事件，光标移动了的操作都需要额外判断，不应丢弃事件
    private boolean shouldProcessMovement(Editor editor) {
        long lastInputTime = lastInputTimeMap.getOrDefault(editor, 0L);
        long elapsed = System.currentTimeMillis() - lastInputTime;
        if (elapsed > EVENT_THRESHOLD) {
            return true;
        }

        LogUtil.debug("丢弃由键盘输入触发的光标移动事件（时间阈值）");
        return false;
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
