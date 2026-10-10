package editoraction;

import enums.InputState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaretDecisionTest {

    @Test
    public void noSelectionReturnsFalse() {
        assertFalse(CaretDecision.isWithinSelectionBoundary(10, 10, 10));
    }

    @Test
    public void insideSelectionReturnsTrue() {
        assertTrue(CaretDecision.isWithinSelectionBoundary(12, 10, 20));
    }

    @Test
    public void selectionBoundaryTolerance() {
        // 紧贴选区前后 1 偏移视为仍在选择中（IDEA-381472 / IJPL-216012）
        assertTrue(CaretDecision.isWithinSelectionBoundary(9, 10, 20));
        assertTrue(CaretDecision.isWithinSelectionBoundary(21, 10, 20));
        assertFalse(CaretDecision.isWithinSelectionBoundary(8, 10, 20));
        assertFalse(CaretDecision.isWithinSelectionBoundary(22, 10, 20));
    }

    @Test
    public void chineseCharMapsToChinese() {
        assertEquals(InputState.CHINESE, CaretDecision.charStateOf('中'));
        assertEquals(InputState.CHINESE, CaretDecision.charStateOf('，'));
    }

    @Test
    public void asciiCharMapsToEnglish() {
        assertEquals(InputState.ENGLISH, CaretDecision.charStateOf('a'));
        assertEquals(InputState.ENGLISH, CaretDecision.charStateOf('1'));
    }
}
