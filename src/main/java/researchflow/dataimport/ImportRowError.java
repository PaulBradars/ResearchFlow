package researchflow.dataimport;

/** {@code rowNumber} is 1-indexed including the header row, so row 2 is the first data row. */
public record ImportRowError(int rowNumber, String message) { }
