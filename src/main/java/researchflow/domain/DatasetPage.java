package researchflow.domain;

import java.util.List;

public record DatasetPage(List<DatasetVariable> variables, List<DatasetRow> rows,
                          long totalRows, int offset, int limit) {
    public DatasetPage {
        variables = List.copyOf(variables);
        rows = List.copyOf(rows);
    }
}
