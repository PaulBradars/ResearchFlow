package researchflow.quality;

import org.junit.jupiter.api.Test;
import researchflow.domain.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QualityIssueFilterTest {
    @Test void combinesCriteriaAndSearchesCaseInsensitively() {
        var response = UUID.randomUUID();
        var issue = QualityIssue.open(UUID.randomUUID(), null, QualityIssueType.INVALID_RANGE,
                QualitySeverity.ERROR, response, UUID.randomUUID(), "Age outside allowed range");
        assertTrue(new QualityIssueFilter(QualityIssueStatus.OPEN, QualityIssueType.INVALID_RANGE,
                QualitySeverity.ERROR, " AGE ").matches(issue));
        assertTrue(new QualityIssueFilter(null, null, null, response.toString().substring(0, 8)).matches(issue));
        assertFalse(new QualityIssueFilter(QualityIssueStatus.DEFERRED, null, null, "").matches(issue));
        assertFalse(new QualityIssueFilter(null, QualityIssueType.OUTLIER, null, "").matches(issue));
        assertFalse(new QualityIssueFilter(null, null, QualitySeverity.WARNING, "").matches(issue));
        assertFalse(new QualityIssueFilter(null, null, null, "no match").matches(issue));
        assertTrue(new QualityIssueFilter(null, null, null, "").matches(issue));
    }
}
