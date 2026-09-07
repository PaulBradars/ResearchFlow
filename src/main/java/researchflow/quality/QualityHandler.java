package researchflow.quality;

import researchflow.domain.QualityIssue;

import java.util.ArrayList;
import java.util.List;

/**
 * Chain-of-Responsibility link for a single deterministic quality rule. Every linked handler
 * always runs and contributes its own issues (rather than stopping the chain), because a scan
 * must surface every applicable rule rather than the first one that matches.
 */
public abstract class QualityHandler {
    private QualityHandler next;

    /** Links {@code next} after this handler and returns it, so chains can be built fluently. */
    public final QualityHandler linkTo(QualityHandler next) {
        this.next = next;
        return next;
    }

    public final List<QualityIssue> handle(QualityScanContext context) {
        var issues = new ArrayList<>(evaluate(context));
        if (next != null) issues.addAll(next.handle(context));
        return List.copyOf(issues);
    }

    protected abstract List<QualityIssue> evaluate(QualityScanContext context);
}
