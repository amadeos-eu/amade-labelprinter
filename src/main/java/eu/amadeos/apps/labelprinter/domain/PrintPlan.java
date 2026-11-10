package eu.amadeos.apps.labelprinter.domain;

import java.util.List;
import java.util.Objects;

/**
 * Which label goes where: the labels in order, starting at a position of the first sheet (the ones before it
 * were used already) and filling the following sheets.
 *
 * @param start the first free position on the first sheet, from 0
 */
public record PrintPlan(SheetTemplate sheet, List<Merge.Label> labels, int start) {
    public PrintPlan {
        Objects.requireNonNull(sheet, "sheet");
        labels = List.copyOf(labels);
        start = Math.max(0, Math.min(sheet.count() - 1, start));
    }

    public int sheets() {
        return Math.max(1, (start + labels.size() + sheet.count() - 1) / sheet.count());
    }

    /** The label at a position of a sheet, or null for a used or a left-over position. */
    public Merge.Label at(int sheetIndex, int position) {
        int i = sheetIndex * sheet.count() + position - start;
        return i >= 0 && i < labels.size() ? labels.get(i) : null;
    }

    /** Whether the position was used before this print (it is before the start on the first sheet). */
    public boolean used(int sheetIndex, int position) {
        return sheetIndex == 0 && position < start;
    }

    /** Free positions on the last sheet after printing. */
    public int leftOver() {
        return sheets() * sheet.count() - start - labels.size();
    }
}
