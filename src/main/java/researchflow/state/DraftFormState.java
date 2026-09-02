package researchflow.state;

import researchflow.domain.FormStatus;

final class DraftFormState implements FormState {
    static final DraftFormState INSTANCE = new DraftFormState();
    private DraftFormState() { }
    public void requireStructuralEdit() { }
    public void requireSubmission() { throw new IllegalStateException("Draft forms cannot accept responses."); }
    public FormStatus activate() { return FormStatus.ACTIVE; }
    public FormStatus close() { throw new IllegalStateException("Activate the form before closing it."); }
}
