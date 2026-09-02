package researchflow.domain;

import java.util.UUID;

public record DatasetQuery(UUID formId, String search, UUID filterQuestionId,
                           DatasetFilterOperator filterOperator, String filterValue,
                           DatasetSort sort, UUID sortQuestionId, int offset, int limit) {
    public DatasetQuery {
        search = search == null ? "" : search.strip();
        filterValue = filterValue == null ? "" : filterValue.strip();
        sort = sort == null ? DatasetSort.NEWEST : sort;
        offset = Math.max(0, offset);
        limit = Math.max(1, Math.min(100, limit));
    }

    public static DatasetQuery firstPage() {
        return new DatasetQuery(null, "", null, null, "", DatasetSort.NEWEST, null, 0, 50);
    }
}
