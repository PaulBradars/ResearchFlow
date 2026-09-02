package researchflow.state;

import researchflow.domain.FormStatus;

public sealed interface FormState permits DraftFormState, ActiveFormState, ClosedFormState {
    void requireStructuralEdit();
    void requireSubmission();
    FormStatus activate();
    FormStatus close();

    static FormState forStatus(FormStatus status) {
        return switch (status) {
            case DRAFT -> DraftFormState.INSTANCE;
            case ACTIVE -> ActiveFormState.INSTANCE;
            case CLOSED -> ClosedFormState.INSTANCE;
        };
    }
}
