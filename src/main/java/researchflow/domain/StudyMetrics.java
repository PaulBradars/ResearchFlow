package researchflow.domain;

public record StudyMetrics(long forms, long responses, long unresolvedIssues,
                           long datasetVersions, long recentAnalyses, long approvedFindings) {
    public static StudyMetrics empty() {
        return new StudyMetrics(0, 0, 0, 0, 0, 0);
    }
}

