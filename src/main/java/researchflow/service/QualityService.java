package researchflow.service;

import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueStatus;
import researchflow.persistence.FormRepository;
import researchflow.persistence.QualityRepository;
import researchflow.persistence.ResponseRepository;
import researchflow.quality.QualityHandlerChain;
import researchflow.quality.QualityScanContext;

import java.util.List;
import java.util.UUID;

public final class QualityService {
    private final FormRepository forms;
    private final ResponseRepository responses;
    private final QualityRepository quality;

    public QualityService(FormRepository forms, ResponseRepository responses, QualityRepository quality) {
        this.forms = forms;
        this.responses = responses;
        this.quality = quality;
    }

    /** Runs the deterministic handler chain and persists any newly detected issues. Never changes data. */
    public List<QualityIssue> scan(UUID studyId) {
        var context = new QualityScanContext(studyId, forms.findByStudy(studyId), responses.findFullByStudy(studyId));
        var detected = QualityHandlerChain.buildDefault().handle(context);
        quality.reconcile(studyId, detected);
        return list(studyId, null);
    }

    public List<QualityIssue> list(UUID studyId, QualityIssueStatus status) {
        return quality.findByStudy(studyId, status);
    }
}
