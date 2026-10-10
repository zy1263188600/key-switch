package editoraction;

import enums.InputState;

/**
 * 光标决策纯函数：选择边界判断 + 字符中英文判定。
 * 无 IDE 依赖，可单测；CursorTrackerService 只做事件装配与委托。
 */
public final class CaretDecision {

    private CaretDecision() {
    }

    private static final Character.UnicodeBlock[] CHINESE_BLOCKS = { // 中文相关Unicode区块
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
            Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    };

    /**
     * 光标是否落在选择区域边界内（含前后各 1 偏移容忍）。
     * -1/+1 容忍：IDEA 平台 BUG，selectionModel 在 CaretListener 中返回滞后值
     * (IDEA-381472 / IJPL-216012)，紧贴选区视为仍在选择中，避免切换策略误触。
     */
    public static boolean isWithinSelectionBoundary(int currentOffset, int selStart, int selEnd) {
        if (currentOffset == selStart && selStart == selEnd) {
            return false;
        }
        return currentOffset >= selStart - 1 && currentOffset <= selEnd + 1;
    }

    public static InputState charStateOf(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        for (Character.UnicodeBlock chineseBlock : CHINESE_BLOCKS) {
            if (chineseBlock.equals(block)) {
                return InputState.CHINESE;
            }
        }
        return InputState.ENGLISH;
    }
}
