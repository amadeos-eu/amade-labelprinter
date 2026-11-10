package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** What the presenter needs from the main window. */
public interface MainView {
    /**
     * Everything the window shows.
     *
     * @param values     the fields of the label shown in the design (the current record)
     * @param fields     the fields to drag onto the label: the data's columns, else the design's own fields
     * @param preview    the labels on the preview sheets
     * @param sheet      the preview sheet shown, from 0
     * @param highlight  the position of the current record on that sheet, -1 if it is not on it
     * @param record     "3 / 46", or "" without data
     */
    record State(LabelDesign design, boolean back, Function<String, String> values, String selected,
                 List<String> fields, PrintPlan preview, int sheet, int highlight, String record, String title,
                 String status, boolean canUndo, boolean canRedo) {
    }

    void show(State state);

    void showError(String message);

    void showStatus(String message);

    enum Unsaved { SAVE, DISCARD, CANCEL }

    Unsaved askUnsaved(String name);

    Optional<Path> chooseOpen(Optional<Path> folder);

    Optional<Path> chooseSave(Optional<Path> folder, String suggestedName, String extension);

    Optional<Path> choosePicture(Optional<Path> folder);

    Optional<SheetTemplate> chooseSheet(SheetTemplate current);

    Optional<DataChoice> chooseData(DataSource source, MergeSettings merge, DataTable table, Set<String> designFields);

    /**
     * What the print window needs.
     *
     * @param plans     the plan for a mode, starting at a position of the first sheet (from 0)
     * @param printers  the printers' names
     * @param last      the printer chosen last
     * @param offsets   the saved adjustment of a printer, {dx, dy}
     * @param testPage  prints the test page with the printer and adjustment of a choice
     */
    record PrintRequest(LabelDesign design, java.util.function.BiFunction<PrintChoice.Mode, Integer, PrintPlan> plans, List<String> printers,
                        Optional<String> last, Function<String, double[]> offsets,
                        java.util.function.Consumer<PrintChoice> testPage) {
    }

    Optional<PrintChoice> choosePrint(PrintRequest request);

    /** Puts {@code «Field»} into the text being edited; false if no text is being edited. */
    boolean insertIntoText(String field);

    /** Moves the focus to the content of the selected element. */
    void editContent();

    void showAbout();

    void close();
}
