package researchflow.persistence;

import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Seeder {
    private static final String STUDY_TITLE = "Student Wellbeing and Academic Focus";
    private final StudyService studies;
    private final FormService forms;
    private final ResponseSubmissionService submissions;

    public Seeder(StudyService studies, FormService forms, ResponseSubmissionService submissions) {
        this.studies = studies;
        this.forms = forms;
        this.submissions = submissions;
    }

    public void seedIfEmpty() {
        var existing = studies.list(true);
        var study = existing.stream().filter(value -> value.title().equals(STUDY_TITLE)).findFirst().orElseGet(() -> {
            if (!existing.isEmpty()) return null;
            return studies.create(
                STUDY_TITLE,
                "Development study used to verify the ResearchFlow workspace and future collection workflow.",
                "Explore how sleep, study routines, and time of day relate to self-reported academic focus.",
                "ResearchFlow Development Team",
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                List.of(
                        "How is sleep duration associated with academic focus?",
                        "Does reported focus differ by preferred study time?"
                )
            );
        });
        if (study == null || !forms.list(study.id()).isEmpty()) return;

        var form = forms.create(study.id(), "Student wellbeing survey", "Small local demo dataset for the collection and dataset workspaces.");
        var sleep = Question.create("sleep_hours", "Hours of sleep last night", "", QuestionType.NUMBER,
                true, 0d, 16d, List.of());
        var focus = Question.create("focus_rating", "Academic focus today", "", QuestionType.RATING,
                true, null, null, options("1", "2", "3", "4", "5"));
        var studyTime = Question.create("study_time", "Preferred study time", "", QuestionType.SINGLE_CHOICE,
                true, null, null, options("Morning", "Afternoon", "Evening"));
        var note = Question.create("note", "Optional note", "", QuestionType.SHORT_TEXT,
                false, null, null, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleep, focus, studyTime, note))));
        form = forms.activate(form.id());
        var rows = List.of(
                new String[]{"7.5", "4", "Morning", "Quiet study session"},
                new String[]{"6", "3", "Evening", ""},
                new String[]{"8", "5", "Morning", "Felt well rested"},
                new String[]{"5", "2", "Evening", "Late deadline"},
                new String[]{"7", "4", "Afternoon", ""},
                new String[]{"14", "3", "Afternoon", "Weekend"},
                new String[]{"6.5", "3", "Morning", "Library"},
                new String[]{"8", "5", "Morning", "Felt well rested"}
        );
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var answers = new java.util.LinkedHashMap<java.util.UUID, String>();
            answers.put(sleep.id(), row[0]); answers.put(focus.id(), row[1]); answers.put(studyTime.id(), row[2]);
            if (!row[3].isBlank()) answers.put(note.id(), row[3]);
            submissions.submit(form.id(), Instant.now().minusSeconds(index == 0 ? 3 : 30 + index * 8L), answers);
        }
    }

    private static List<QuestionOption> options(String... values) {
        return java.util.Arrays.stream(values).map(QuestionOption::create).toList();
    }
}
