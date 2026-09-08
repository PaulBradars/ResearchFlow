package researchflow.service;

import researchflow.domain.Study;
import researchflow.domain.StudyMetrics;
import researchflow.domain.StudyStatus;
import researchflow.persistence.StudyRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StudyService {
    private final StudyRepository repository;
    private final StudyValidator validator = new StudyValidator();

    public StudyService(StudyRepository repository) {
        this.repository = repository;
    }

    public Study create(String title, String description, String objectives, String researcher,
                        LocalDate startDate, LocalDate endDate, List<String> researchQuestions) {
        var study = Study.create(title, description, objectives, researcher, startDate, endDate, researchQuestions);
        validator.validate(study);
        repository.save(study, "STUDY_CREATED");
        return study;
    }

    public Study update(UUID id, String title, String description, String objectives, String researcher,
                        LocalDate startDate, LocalDate endDate, List<String> researchQuestions) {
        var current = requireActive(id);
        var updated = current.revise(title, description, objectives, researcher, startDate, endDate, researchQuestions);
        validator.validate(updated);
        repository.save(updated, "STUDY_UPDATED");
        return updated;
    }

    public void archive(UUID id) {
        var current = requireActive(id);
        repository.save(current.archive(), "STUDY_ARCHIVED");
    }

    public List<Study> list(boolean includeArchived) {
        return repository.findAll(includeArchived);
    }

    public Optional<Study> find(UUID id) {
        return repository.findById(id);
    }

    public StudyMetrics metrics(UUID studyId) {
        require(studyId);
        return repository.metrics(studyId);
    }

    private Study requireActive(UUID id) {
        var study = require(id);
        if (study.status() == StudyStatus.ARCHIVED) {
            throw new IllegalStateException("Archived studies are read-only.");
        }
        return study;
    }

    private Study require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Study not found: " + id));
    }
}

