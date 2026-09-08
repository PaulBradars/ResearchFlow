package researchflow.service;

import org.junit.jupiter.api.Test;
import researchflow.domain.Study;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyValidatorTest {
    private final StudyValidator validator = new StudyValidator();

    @Test
    void rejectsMissingTitleAndInvertedDateRange() {
        var study = Study.create(" ", "", "", "", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 1), List.of());

        var failure = assertThrows(ValidationException.class, () -> validator.validate(study));

        assertEquals("Study title is required.", failure.errors().get("title"));
        assertTrue(failure.errors().containsKey("endDate"));
    }

    @Test
    void acceptsAWellFormedStudy() {
        var study = Study.create("Valid", "Description", "Objectives", "Researcher",
                LocalDate.now(), null, List.of("What changes?"));
        validator.validate(study);
    }
}

