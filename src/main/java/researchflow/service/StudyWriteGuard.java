package researchflow.service;

import researchflow.domain.StudyStatus;
import researchflow.persistence.StudyRepository;

import java.util.Objects;
import java.util.UUID;

/** Shared service-layer policy. Always reloads the study instead of trusting a UI projection. */
public final class StudyWriteGuard {
    private final StudyRepository studies;

    public StudyWriteGuard(StudyRepository studies) {
        this.studies = Objects.requireNonNull(studies);
    }

    public researchflow.domain.Study requireWritable(UUID studyId) {
        var study = studies.findById(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Study not found: " + studyId));
        if (study.status() == StudyStatus.ARCHIVED) throw new IllegalStateException("Archived studies are read-only.");
        return study;
    }
}
