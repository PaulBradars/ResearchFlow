package researchflow.state;

import researchflow.domain.FormStatus;

final class ClosedFormState implements FormState {
    static final ClosedFormState INSTANCE = new ClosedFormState();
    private ClosedFormState() { }
    public void requireStructuralEdit() { throw new IllegalStateException("Closed forms cannot be structurally edited."); }
    public void requireSubmission() { throw new IllegalStateException("Closed forms cannot accept responses."); }
    public FormStatus activate() { throw new IllegalStateException("Closed forms cannot be reactivated."); }
    public FormStatus close() { throw new IllegalStateException("The form is already closed."); }
}

