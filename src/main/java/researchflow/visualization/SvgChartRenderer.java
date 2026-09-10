package researchflow.visualization;

import java.util.List;
import java.util.Locale;

public final class SvgChartRenderer {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 260;
    private static final int PADDING = 40;

    private SvgChartRenderer() { }

    public static String render(ChartSpec chart) {
        return switch (chart) {
            case ChartSpec.Bar bar -> renderBars(bar.title(), bar.categories(), bar.values());
            case ChartSpec.Histogram histogram -> renderBars(histogram.title(), histogram.binLabels(),
                    histogram.binCounts().stream().map(Integer::doubleValue).toList());
            case ChartSpec.Scatter scatter -> renderScatter(scatter);
        };
    }

    private static String renderBars(String title, List<String> labels, List<Double> values) {
        var max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        var plotWidth = WIDTH - PADDING * 2;
        var plotHeight = HEIGHT - PADDING * 2;
        var barSlot = labels.isEmpty() ? plotWidth : plotWidth / (double) labels.size();
        var builder = new StringBuilder();
        openSvg(builder, title);
        for (int index = 0; index < labels.size(); index++) {
            var value = values.get(index);
            var barHeight = (value / max) * plotHeight;
            var x = PADDING + index * barSlot + barSlot * 0.1;
            var y = HEIGHT - PADDING - barHeight;
            var width = barSlot * 0.8;
            builder.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append("\" width=\"")
                    .append(fmt(width)).append("\" height=\"").append(fmt(barHeight)).append("\" fill=\"#3157d5\"/>");
            builder.append("<text x=\"").append(fmt(x + width / 2)).append("\" y=\"").append(HEIGHT - PADDING + 14)
                    .append("\" text-anchor=\"middle\" font-size=\"9\">").append(escape(labels.get(index))).append("</text>");
            builder.append("<text x=\"").append(fmt(x + width / 2)).append("\" y=\"").append(fmt(y - 4))
                    .append("\" text-anchor=\"middle\" font-size=\"9\">").append(fmt(value)).append("</text>");
        }
        axisLine(builder);
        builder.append("</svg>");
        return builder.toString();
    }

    private static String renderScatter(ChartSpec.Scatter scatter) {
        var xs = scatter.xValues();
        var ys = scatter.yValues();
        var minX = xs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        var maxX = xs.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        var minY = ys.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        var maxY = ys.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        var rangeX = maxX - minX == 0 ? 1 : maxX - minX;
        var rangeY = maxY - minY == 0 ? 1 : maxY - minY;
        var plotWidth = WIDTH - PADDING * 2;
        var plotHeight = HEIGHT - PADDING * 2;
        var builder = new StringBuilder();
        openSvg(builder, scatter.title());
        for (int index = 0; index < xs.size(); index++) {
            var px = PADDING + ((xs.get(index) - minX) / rangeX) * plotWidth;
            var py = HEIGHT - PADDING - ((ys.get(index) - minY) / rangeY) * plotHeight;
            builder.append("<circle cx=\"").append(fmt(px)).append("\" cy=\"").append(fmt(py))
                    .append("\" r=\"3\" fill=\"#3157d5\" fill-opacity=\"0.7\"/>");
        }
        axisLine(builder);
        builder.append("<line x1=\"").append(PADDING).append("\" y1=\"").append(PADDING).append("\" x2=\"")
                .append(PADDING).append("\" y2=\"").append(HEIGHT - PADDING).append("\" stroke=\"#667085\"/>");
        builder.append("</svg>");
        return builder.toString();
    }

    private static void openSvg(StringBuilder builder, String title) {
        builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" role=\"img\" aria-label=\"").append(escape(title)).append("\">");
        builder.append("<text x=\"").append(WIDTH / 2).append("\" y=\"16\" text-anchor=\"middle\" font-size=\"12\">")
                .append(escape(title)).append("</text>");
    }

    private static void axisLine(StringBuilder builder) {
        builder.append("<line x1=\"").append(PADDING).append("\" y1=\"").append(HEIGHT - PADDING).append("\" x2=\"")
                .append(WIDTH - PADDING).append("\" y2=\"").append(HEIGHT - PADDING).append("\" stroke=\"#667085\"/>");
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
