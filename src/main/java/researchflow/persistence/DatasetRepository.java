package researchflow.persistence;

import researchflow.domain.Answer;
import researchflow.domain.CorrectionTarget;
import researchflow.domain.DatasetPage;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetRow;

import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository {
    DatasetPage query(UUID studyId, DatasetQuery query);
    Optional<DatasetRow> findResponse(UUID studyId, UUID responseId);
    Optional<CorrectionTarget> findCorrectionTarget(UUID studyId, UUID responseId, UUID questionId);
    void correct(CorrectionTarget target, Answer replacement, String reason);
}

