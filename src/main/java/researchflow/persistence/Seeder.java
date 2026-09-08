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
import java.util.Locale;
import java.util.Random;

/**
 * Builds a demonstration-scale dataset (one curated batch plus a larger generated batch) so every
 * workspace - dataset, quality, versioning, analysis, and reporting - has enough data to be a
 * meaningful demo rather than a handful of placeholder rows. The random generation uses a fixed
 * seed so the dataset (and its embedded quality examples) is identical on every fresh machine.
 */
public final class Seeder {
    private static final String STUDY_TITLE = "Student Wellbeing and Academic Focus";
    private static final int GENERATED_RESPONSE_COUNT = 142;
    private static final long RANDOM_SEED = 20260101L;

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
                "Explore how sleep, screen time, and study routines relate to self-reported academic focus.",
                "ResearchFlow Development Team",
                LocalDate.now(),
                LocalDate.now().plusMonths(3),
                List.of(
                        "How is sleep duration associated with academic focus?",
                        "Does evening screen time relate to how much sleep students get?",
                        "Does reported focus differ by preferred study time?"
                )
            );
        });
        if (study == null || !forms.list(study.id()).isEmpty()) return;

        var form = forms.create(study.id(), "Student wellbeing survey",
                "Demonstration dataset for the collection, quality, versioning, analysis, and reporting workspaces.");
        var sleep = Question.create("sleep_hours", "Hours of sleep last night", "", QuestionType.NUMBER,
                true, 0d, 16d, List.of());
        var screenTime = Question.create("screen_time_hours", "Screen time before bed (hours)", "", QuestionType.NUMBER,
                true, 0d, 12d, List.of());
        var focus = Question.create("focus_rating", "Academic focus today", "", QuestionType.RATING,
                true, null, null, options("1", "2", "3", "4", "5"));
        var studyTime = Question.create("study_time", "Preferred study time", "", QuestionType.SINGLE_CHOICE,
                true, null, null, options("Morning", "Afternoon", "Evening"));
        var note = Question.create("note", "Optional note", "", QuestionType.SHORT_TEXT,
                false, null, null, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleep, screenTime, focus, studyTime, note))));
        form = forms.activate(form.id());

        // Curated rows: {sleepHours, screenTimeHours, focusRating, studyTime, note}. Deliberately embeds
        // one duplicate pair (rows 2 and 7 match on every field), one statistical outlier (14h sleep),
        // and one unusually fast submission (row 0, submitted 3 seconds after it started) - see
        // SeederQualityScanTest, which asserts the Phase 4 quality handlers surface exactly these.
        var curated = List.of(
                new String[]{"7.5", "2.0", "4", "Morning", "Quiet study session"},
                new String[]{"6", "4.0", "3", "Evening", ""},
                new String[]{"8", "1.5", "5", "Morning", "Felt well rested"},
                new String[]{"5", "5.5", "2", "Evening", "Late deadline"},
                new String[]{"7", "2.5", "4", "Afternoon", ""},
                new String[]{"14", "3.0", "3", "Afternoon", "Weekend"},
                new String[]{"6.5", "3.5", "3", "Morning", "Library"},
                new String[]{"8", "1.5", "5", "Morning", "Felt well rested"}
        );
        var questions = new Question[]{sleep, screenTime, focus, studyTime, note};
        for (int index = 0; index < curated.size(); index++) {
            submitRow(form, questions, curated.get(index), Instant.now().minusSeconds(index == 0 ? 3 : 30 + index * 8L));
        }

        // Generated rows: sleep hours drawn from a clipped normal distribution around 6.8h, with
        // screen time before bed negatively correlated to sleep (more screen time, less sleep) so the
        // correlation analysis strategy has a real, demonstrable relationship to find.
        var random = new Random(RANDOM_SEED);
        var studyTimes = new String[]{"Morning", "Afternoon", "Evening"};
        for (int index = 0; index < GENERATED_RESPONSE_COUNT; index++) {
            var sleepHours = clamp(round(6.8 + random.nextGaussian() * 1.1, 1), 4.0, 10.0);
            var screenHours = clamp(round(4.2 - 0.55 * (sleepHours - 6.8) + random.nextGaussian() * 0.9, 1), 0.0, 12.0);
            var focusRating = focusRatingFor(sleepHours, random);
            var chosenTime = studyTimes[random.nextInt(studyTimes.length)];
            var noteText = random.nextDouble() < 0.15 ? "Follow-up week " + (1 + random.nextInt(12)) : "";
            var row = new String[]{
                    format(sleepHours), format(screenHours), Integer.toString(focusRating), chosenTime, noteText};
            var offsetSeconds = 60 + random.nextInt(900);
            submitRow(form, questions, row, Instant.now().minusSeconds(offsetSeconds));
        }
    }

    private void submitRow(researchflow.domain.Form form, Question[] questions, String[] row, Instant startedAt) {
        var answers = new java.util.LinkedHashMap<java.util.UUID, String>();
        for (int i = 0; i < questions.length; i++) {
            if (!row[i].isBlank()) answers.put(questions[i].id(), row[i]);
        }
        submissions.submit(form.id(), startedAt, answers);
    }

    private static int focusRatingFor(double sleepHours, Random random) {
        // More sleep skews (softly, with noise) toward a higher self-reported focus rating.
        var base = 1 + (sleepHours - 4.0) / 6.0 * 4.0;
        var noisy = base + random.nextGaussian() * 0.9;
        return (int) Math.round(clamp(noisy, 1.0, 5.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value, int decimals) {
        var factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static List<QuestionOption> options(String... values) {
        return java.util.Arrays.stream(values).map(QuestionOption::create).toList();
    }
}
