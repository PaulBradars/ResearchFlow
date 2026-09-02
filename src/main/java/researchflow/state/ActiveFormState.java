package researchflow.state;

import researchflow.domain.FormStatus;

final class ActiveFormState implements FormState {
    static final ActiveFormState INSTANCE = new ActiveFormState();
    private ActiveFormState() { }
    public void requireStructuralEdit() { throw new IllegalStateException("Active forms cannot be structurally edited."); }
    public void requireSubmission() { }
    public FormStatus activate() { throw new IllegalStateException("The form is already active."); }
    public FormStatus close() { return FormStatus.CLOSED; }
}
