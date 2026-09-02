package researchflow.persistence;

import researchflow.domain.Form;
import researchflow.domain.Response;
import researchflow.domain.ResponseSummary;

import java.util.List;
import java.util.UUID;

public interface ResponseRepository {
    void submit(Form form, Response response);
    List<ResponseSummary> findByStudy(UUID studyId);
}
