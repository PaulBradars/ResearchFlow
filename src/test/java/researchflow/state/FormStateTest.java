package researchflow.state;

import org.junit.jupiter.api.Test;
import researchflow.domain.FormStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormStateTest {
    @Test
    void draftAllowsEditingButOnlyActiveAllowsSubmission() {
        var draft = FormState.forStatus(FormStatus.DRAFT);
        assertDoesNotThrow(draft::requireStructuralEdit);
        assertThrows(IllegalStateException.class, draft::requireSubmission);
        assertEquals(FormStatus.ACTIVE, draft.activate());

        var active = FormState.forStatus(FormStatus.ACTIVE);
        assertThrows(IllegalStateException.class, active::requireStructuralEdit);
        assertDoesNotThrow(active::requireSubmission);
        assertEquals(FormStatus.CLOSED, active.close());

        var closed = FormState.forStatus(FormStatus.CLOSED);
        assertThrows(IllegalStateException.class, closed::requireStructuralEdit);
        assertThrows(IllegalStateException.class, closed::requireSubmission);
        assertThrows(IllegalStateException.class, closed::activate);
    }
}
