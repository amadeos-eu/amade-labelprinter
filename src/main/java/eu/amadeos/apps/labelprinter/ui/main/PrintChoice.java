package eu.amadeos.apps.labelprinter.ui.main;

/**
 * What the print window asked for.
 *
 * @param printer  the printer's name, or null for a PDF file
 * @param mode     all records, the current one, or the current label on the whole sheet
 * @param start    the first free position on the first sheet, from 0
 * @param dx       printer adjustment to the right, mm
 * @param dy       printer adjustment down, mm
 * @param outlines print a line around every label
 */
public record PrintChoice(String printer, Mode mode, int start, double dx, double dy, boolean outlines) {
    public enum Mode { ALL, CURRENT, FILL_SHEET }

    public boolean pdf() {
        return printer == null;
    }
}
