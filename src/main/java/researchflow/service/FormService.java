package researchflow.service;

import researchflow.domain.Form;
import researchflow.domain.FormStatus;
import researchflow.domain.Section;
import researchflow.persistence.FormRepository;
import researchflow.state.FormState;

import java.util.List;
import java.util.UUID;

public final class FormService {
    private final FormRepository repository;
    private final FormValidator validator = new FormValidator();

    public FormService(FormRepository repository) {
        this.repository = repository;
    }

    public Form create(UUID studyId, String title, String description) {
        var form = Form.create(studyId, title, description);
        validator.validate(form);
        repository.save(form, "FORM_CREATED");
        return form;
    }

    public Form updateStructure(UUID id, String title, String description, List<Section> sections) {
        var current = require(id);
        FormState.forStatus(current.status()).requireStructuralEdit();
        var updated = current.revise(title, description, sections);
        validator.validate(updated);
        repository.save(updated, "FORM_UPDATED");
        return updated;
    }

    public Form activate(UUID id) {
        var current = require(id);
        validator.validate(current);
        if (current.questions().isEmpty()) throw new IllegalStateException("Add at least one question before activation.");
        var updated = current.withStatus(FormState.forStatus(current.status()).activate());
        repository.save(updated, "FORM_ACTIVATED");
        return updated;
    }

    public Form close(UUID id) {
        var current = require(id);
        var updated = current.withStatus(FormState.forStatus(current.status()).close());
        repository.save(updated, "FORM_CLOSED");
        return updated;
    }

    public List<Form> list(UUID studyId) {
        return repository.findByStudy(studyId);
    }

    public Form require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Form not found: " + id));
    }

    public List<Form> active(UUID studyId) {
        return list(studyId).stream().filter(form -> form.status() == FormStatus.ACTIVE).toList();
    }
}
