package researchflow.quality;

import researchflow.domain.Answer;
import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;
import researchflow.domain.Response;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

public final class DuplicateResponseHandler extends QualityHandler {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Override
    protected List<QualityIssue> evaluate(QualityScanContext context) {
        var issues = new ArrayList<QualityIssue>();
        var byForm = new LinkedHashMap<UUID, List<Response>>();
        for (var response : context.responses()) byForm.computeIfAbsent(response.formId(), id -> new ArrayList<>()).add(response);

        for (var formResponses : byForm.values()) {
            var groups = new LinkedHashMap<String, List<Response>>();
            for (var response : formResponses) groups.computeIfAbsent(signature(response), key -> new ArrayList<>()).add(response);
            for (var group : groups.values()) {
                if (group.size() < 2) continue;
                var sorted = group.stream().sorted(Comparator.comparing(Response::submittedAt)).toList();
                var original = sorted.getFirst();
                for (var duplicate : sorted.subList(1, sorted.size())) {
                    issues.add(QualityIssue.open(context.studyId(), null, QualityIssueType.DUPLICATE_RESPONSE,
                            QualitySeverity.WARNING, duplicate.id(), null,
                            "Answers match an earlier response (" + shortId(original.id()) + ") submitted "
                                    + TIMESTAMP.format(original.submittedAt()) + "; likely a duplicate submission."));
                }
            }
        }
        return issues;
    }

    private static String signature(Response response) {
        var byQuestion = new TreeMap<String, String>();
        for (var answer : response.answers()) byQuestion.put(answer.questionId().toString(), valueOf(answer));
        return byQuestion.toString();
    }

    private static String valueOf(Answer answer) {
        if (answer instanceof Answer.Text value) return value.value();
        if (answer instanceof Answer.Number value) return Double.toString(value.value());
        if (answer instanceof Answer.BooleanValue value) return Boolean.toString(value.value());
        if (answer instanceof Answer.DateValue value) return value.value().toString();
        var choice = (Answer.Choice) answer;
        return String.join(",", choice.values().stream().sorted().toList());
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}

