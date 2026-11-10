package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A main window without Swing: remembers what it was shown and answers questions from a script. */
final class FakeView implements MainView {
    final List<State> states = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<String> statuses = new ArrayList<>();
    final List<String> inserted = new ArrayList<>();
    final Deque<Optional<Path>> files = new ArrayDeque<>();
    Unsaved unsaved = Unsaved.CANCEL;
    int unsavedAsked;
    Optional<SheetTemplate> sheet = Optional.empty();
    Optional<DataChoice> data = Optional.empty();
    Optional<PrintChoice> print = Optional.empty();
    PrintRequest printRequest;
    boolean editingText;
    boolean contentEdited;
    boolean aboutShown;
    boolean closed;

    State last() {
        return states.getLast();
    }

    @Override
    public void show(State state) {
        states.add(state);
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showStatus(String message) {
        statuses.add(message);
    }

    @Override
    public Unsaved askUnsaved(String name) {
        unsavedAsked++;
        return unsaved;
    }

    private Optional<Path> nextFile() {
        return files.isEmpty() ? Optional.empty() : files.pop();
    }

    @Override
    public Optional<Path> chooseOpen(Optional<Path> folder) {
        return nextFile();
    }

    @Override
    public Optional<Path> chooseSave(Optional<Path> folder, String suggestedName, String extension) {
        return nextFile();
    }

    @Override
    public Optional<Path> choosePicture(Optional<Path> folder) {
        return nextFile();
    }

    @Override
    public Optional<SheetTemplate> chooseSheet(SheetTemplate current) {
        return sheet;
    }

    @Override
    public Optional<DataChoice> chooseData(DataSource source, MergeSettings merge, DataTable table,
                                           Set<String> designFields) {
        return data;
    }

    @Override
    public Optional<PrintChoice> choosePrint(PrintRequest request) {
        printRequest = request;
        return print;
    }

    @Override
    public boolean insertIntoText(String field) {
        if (editingText) inserted.add(field);
        return editingText;
    }

    @Override
    public void editContent() {
        contentEdited = true;
    }

    @Override
    public void showAbout() {
        aboutShown = true;
    }

    @Override
    public void close() {
        closed = true;
    }
}
