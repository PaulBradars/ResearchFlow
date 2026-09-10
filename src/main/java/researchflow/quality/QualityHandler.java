package researchflow.quality;

import researchflow.domain.QualityIssue;

import java.util.ArrayList;
import java.util.List;

public abstract class QualityHandler {
    private QualityHandler next;

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
