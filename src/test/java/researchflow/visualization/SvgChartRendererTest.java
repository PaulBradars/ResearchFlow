package researchflow.visualization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgChartRendererTest {
    @Test
    void rendersABarChartAsAnSvgWithOneRectPerCategory() {
        var chart = new ChartSpec.Bar("Study time", "Category", "Count", List.of("Morning", "Evening"), List.of(3.0, 2.0));

        var svg = SvgChartRenderer.render(chart);

        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("Study time"));
        assertTrue(countOccurrences(svg, "<rect") == 2);
    }

    @Test
    void rendersAHistogramAsBarsToo() {
        var chart = new ChartSpec.Histogram("Sleep hours", "Hours", List.of("5–6", "6–7", "7–8"), List.of(2, 3, 1));

        var svg = SvgChartRenderer.render(chart);

        assertTrue(countOccurrences(svg, "<rect") == 3);
    }

    @Test
    void rendersAScatterChartAsOneCirclePerPoint() {
        var chart = new ChartSpec.Scatter("Sleep vs focus", "Sleep", "Focus", List.of(1.0, 2.0, 3.0), List.of(2.0, 4.0, 6.0));

        var svg = SvgChartRenderer.render(chart);

        assertTrue(countOccurrences(svg, "<circle") == 3);
    }

    @Test
    void escapesHtmlSpecialCharactersInTitlesAndLabels() {
        var chart = new ChartSpec.Bar("A & B <script>", "X", "Y", List.of("<b>bold</b>"), List.of(1.0));

        var svg = SvgChartRenderer.render(chart);

        assertTrue(svg.contains("A &amp; B &lt;script&gt;"));
        assertTrue(svg.contains("&lt;b&gt;bold&lt;/b&gt;"));
        assertTrue(!svg.contains("<script>"));
    }

    private static long countOccurrences(String text, String token) {
        var count = 0L;
        var index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }
}
