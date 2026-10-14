package plano.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for rendering structured ASCII tables in the terminal
 * with customizable columns, automatic sizing, and text alignment.
 */
public class TableRenderer {

    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final List<Integer> maxColumnWidths = new ArrayList<>();

    public TableRenderer(String... headers) {
        for (String h : headers) {
            this.headers.add(h);
            this.maxColumnWidths.add(h.length());
        }
    }

    public TableRenderer addRow(String... cells) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String cell = i < cells.length && cells[i] != null ? cells[i] : "";
            row.add(cell);
            if (cell.length() > maxColumnWidths.get(i)) {
                maxColumnWidths.set(i, cell.length());
            }
        }
        rows.add(row);
        return this;
    }

    public void render() {
        if (headers.isEmpty()) return;

        // Cap column widths if very long to prevent wrapping out of screen
        List<Integer> colWidths = new ArrayList<>();
        for (int w : maxColumnWidths) {
            colWidths.add(Math.min(Math.max(w, 4), 45));
        }

        // Top Border
        printDivider("┌", "┬", "┐", colWidths);

        // Header Row
        printRow(headers, colWidths, ConsoleIO.BOLD + ConsoleIO.CYAN);

        // Header Separator
        printDivider("├", "┼", "┤", colWidths);

        // Data Rows
        if (rows.isEmpty()) {
            int totalWidth = colWidths.stream().mapToInt(Integer::intValue).sum() + (colWidths.size() - 1) * 3;
            System.out.println("│ " + ConsoleIO.DIM + ConsoleIO.centerText("No records found", totalWidth) + ConsoleIO.RESET + " │");
        } else {
            for (List<String> row : rows) {
                printRow(row, colWidths, ConsoleIO.RESET);
            }
        }

        // Bottom Border
        printDivider("└", "┴", "┘", colWidths);
    }

    private void printRow(List<String> cells, List<Integer> widths, String style) {
        StringBuilder sb = new StringBuilder();
        sb.append("│");
        for (int i = 0; i < widths.size(); i++) {
            String raw = i < cells.size() ? cells.get(i) : "";
            int width = widths.get(i);
            String display = raw;
            if (raw.length() > width) {
                display = raw.substring(0, Math.max(0, width - 3)) + "...";
            }
            sb.append(" ");
            sb.append(style);
            sb.append(display);
            sb.append(" ".repeat(Math.max(0, width - display.length())));
            sb.append(ConsoleIO.RESET);
            sb.append(" │");
        }
        System.out.println(sb.toString());
    }

    private void printDivider(String left, String mid, String right, List<Integer> widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.size(); i++) {
            sb.append("─".repeat(widths.get(i) + 2));
            if (i < widths.size() - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        System.out.println(sb.toString());
    }
}
