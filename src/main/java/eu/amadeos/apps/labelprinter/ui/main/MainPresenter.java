package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.domain.Align;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelDocument;
import eu.amadeos.apps.labelprinter.domain.Merge;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.Placeholders;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.domain.Side;
import eu.amadeos.apps.labelprinter.domain.TextStyle;
import eu.amadeos.apps.labelprinter.repository.LabelPreferences;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.service.LabelFiles;
import eu.amadeos.apps.labelprinter.service.LabelPainter;
import eu.amadeos.apps.labelprinter.service.PdfExport;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.apps.labelprinter.service.SheetPages;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.print.PaperSize;
import eu.amadeos.commons.print.PrintJobs;
import eu.amadeos.commons.process.ProgramMissingException;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * The main window's logic: the open document with undo, the data for mail merge, the record shown, and the
 * commands of the menus and the toolbar. Everything runs on the event dispatch thread except reading data,
 * printing and exporting, which run in the background.
 */
public final class MainPresenter {
    /** Undo keeps this many steps. */
    public static final int UNDO_STEPS = 100;
    /** Typing in the same element within this time is one undo step. */
    static final long COALESCE_MS = 1500;
    private static final String PICTURE_TYPES = ".png .jpg .jpeg .gif .bmp";

    /** Opens the handbook. */
    public interface Opener {
        void handbook() throws IOException;
    }

    /**
     * What the presenter and its window use.
     *
     * @param documents where file choosers start the first time
     * @param pictures  where the picture chooser starts the first time
     */
    public record Services(SheetCatalog catalog, DataLoader data, LabelPreferences prefs, PdfExport pdf,
                           Opener opener, ExecutorService background, Executor ui, Runnable exit, Clock clock,
                           Path documents, Path pictures) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services s;
    private final Deque<LabelDocument> undo = new ArrayDeque<>();
    private final Deque<LabelDocument> redo = new ArrayDeque<>();
    private final Map<String, BufferedImage> imageCache = new HashMap<>();
    private final SheetPainter sheetPainter;
    private final LabelPainter labelPainter;

    private LabelDocument doc;
    private Path file;
    private boolean dirty;
    private DataTable table = DataTable.NONE;
    private int record;
    private int previewSheet;
    private boolean back;
    private String selected;
    private String coalesceKey;
    private long coalesceTime;
    private String status = "";

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = Objects.requireNonNull(view, "view");
        this.msg = msg;
        this.locales = locales;
        this.s = services;
        this.labelPainter = new LabelPainter(this::image);
        this.sheetPainter = new SheetPainter(labelPainter);
        this.doc = new LabelDocument(LabelDesign.empty(startSheet()), Map.of(), DataSource.NONE, MergeSettings.ALL);
    }

    public LabelPainter labelPainter() {
        return labelPainter;
    }

    public SheetPainter sheetPainter() {
        return sheetPainter;
    }

    /** Starts with an empty design, or opens a file. */
    public void start(Optional<Path> open) {
        if (open.isPresent()) openFile(open.get());
        else newDesign(startSheet());
    }

    private SheetTemplate startSheet() {
        boolean letter = PaperSize.forLocale(Locale.getDefault(Locale.Category.FORMAT)) == PaperSize.LETTER;
        return s.prefs().lastSheet().flatMap(s.catalog()::byId).orElse(s.catalog().standard(letter));
    }

    // ---- state ------------------------------------------------------------------------------------------------

    public LabelDocument document() {
        return doc;
    }

    public DataTable table() {
        return table;
    }

    public boolean dirty() {
        return dirty;
    }

    public String selected() {
        return selected;
    }

    public boolean back() {
        return back;
    }

    public int record() {
        return record;
    }

    /** The labels as they are printed: every record (filtered, sorted, with copies), or one label without data. */
    public List<Merge.Label> labels() {
        return Merge.labels(table, doc.merge(), doc.design(), LocalDate.now(s.clock()), msg.locale());
    }

    private Function<String, String> currentValues(List<Merge.Label> labels) {
        if (table.isEmpty()) {
            // Without data, built-in fields show their value and the others stay «Field».
            return labels.getFirst().values();
        }
        return labels.isEmpty() ? f -> "" : labels.get(Math.min(record, labels.size() - 1)).values();
    }

    void refresh() {
        List<Merge.Label> labels = labels();
        record = Math.max(0, Math.min(record, labels.size() - 1));
        LabelDesign d = doc.design();
        PrintPlan preview = new PrintPlan(d.sheet(), labels, 0);
        previewSheet = Math.max(0, Math.min(previewSheet, preview.sheets() - 1));
        int highlight = record / d.sheet().count() == previewSheet ? record % d.sheet().count() : -1;
        String recordText = table.isEmpty() ? "" : msg.format("record.of", record + 1, labels.size());
        String name = file == null ? msg.get("file.untitled") : file.getFileName().toString();
        String title = (dirty ? "*" : "") + name + " – " + msg.get("app.title");
        String sheetText = msg.format("status.sheet", d.sheet().paperName(), d.sheet().gridText(),
                d.sheet().sizeText(), preview.sheets());
        String dataText = table.isEmpty() ? msg.get("status.no-data")
                : msg.format("status.data", table.source(), labels.size() / doc.merge().copies(), table.size());
        view.show(new MainView.State(d, back, currentValues(labels), selected, fields(), preview, previewSheet,
                highlight, recordText, title, status.isEmpty() ? sheetText + " · " + dataText : status,
                !undo.isEmpty(), !redo.isEmpty()));
        status = "";
    }

    /** The fields to drag onto the label: the data's columns, the design's own fields, and the built-in ones. */
    public List<String> fields() {
        List<String> fields = new ArrayList<>(table.columns());
        for (String f : doc.design().fields()) if (!fields.contains(f) && table.column(f).isEmpty()) fields.add(f);
        fields.add(Placeholders.DATE);
        fields.add(Placeholders.COUNTER);
        fields.add(Placeholders.NUMBER);
        return fields;
    }

    // ---- editing with undo ------------------------------------------------------------------------------------

    private void change(LabelDocument next, String key) {
        if (next.equals(doc)) return;
        long now = s.clock().millis();
        boolean coalesce = key != null && key.equals(coalesceKey) && now - coalesceTime < COALESCE_MS;
        if (!coalesce) {
            undo.push(doc);
            while (undo.size() > UNDO_STEPS) undo.removeLast();
        }
        redo.clear();
        coalesceKey = key;
        coalesceTime = now;
        doc = next;
        dirty = true;
        refresh();
    }

    private void changeDesign(LabelDesign design) {
        change(doc.withDesign(design), null);
    }

    private Side side() {
        return doc.design().side(back);
    }

    private void changeSide(Side side, String key) {
        change(doc.withDesign(doc.design().withSide(back, side)), key);
    }

    public void undo() {
        if (undo.isEmpty()) return;
        redo.push(doc);
        doc = undo.pop();
        coalesceKey = null;
        dirty = true;
        keepSelection();
        refresh();
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.push(doc);
        doc = redo.pop();
        coalesceKey = null;
        dirty = true;
        keepSelection();
        refresh();
    }

    private void keepSelection() {
        if (!doc.design().twoSided()) back = false;
        if (selected != null && side().find(selected).isEmpty()) selected = null;
    }

    /** An element was changed in the properties or with the mouse. */
    public void apply(Element element) {
        if (side().find(element.id()).isEmpty()) return;
        changeSide(side().with(element), "element:" + element.id());
    }

    public void select(String id) {
        selected = id != null && side().find(id).isPresent() ? id : null;
        coalesceKey = null;
        refresh();
    }

    public void delete(String id) {
        if (id == null || side().find(id).isEmpty()) return;
        if (id.equals(selected)) selected = null;
        changeSide(side().without(id), null);
    }

    public void deleteSelected() {
        delete(selected);
    }

    public void toFront() {
        if (selected != null) changeSide(side().reordered(selected, true), null);
    }

    public void toBack() {
        if (selected != null) changeSide(side().reordered(selected, false), null);
    }

    /** Duplicates the selected element a little to the right and down. */
    public void duplicate() {
        side().find(selected == null ? "" : selected).ifPresent(e -> {
            Element copy = withId(e, Element.newId()).withBox(e.box().moved(2, 2));
            selected = copy.id();
            changeSide(side().with(copy), null);
        });
    }

    private static Element withId(Element e, String id) {
        return switch (e) {
            case Element.Text t -> new Element.Text(id, t.box(), t.text(), t.style(), t.shrink(), t.hideEmptyLines());
            case Element.Picture p -> new Element.Picture(id, p.box(), p.image(), p.keepAspect());
            case Element.Figure f -> new Element.Figure(id, f.box(), f.kind(), f.fill(), f.stroke(), f.strokeWidth(),
                    f.radius());
            case Element.Code c -> new Element.Code(id, c.box(), c.type(), c.content(), c.color(), c.showText());
        };
    }

    /** What the toolbar adds. */
    public enum Add { TEXT, FIELD, PICTURE, SHAPE, BARCODE, QR }

    public void add(Add what) {
        SheetTemplate sheet = doc.design().sheet();
        double margin = Math.min(4, Math.min(sheet.width(), sheet.height()) / 8);
        double w = Math.max(5, sheet.width() - 2 * margin);
        Element e = switch (what) {
            case TEXT -> new Element.Text(Element.newId(), new Box(margin, margin, w, Math.min(8, sheet.height() / 3)),
                    msg.get("new.text"), TextStyle.DEFAULT, true, true);
            case FIELD -> {
                List<String> fields = fields();
                String first = table.columns().isEmpty() ? fields.getFirst() : table.columns().getFirst();
                yield new Element.Text(Element.newId(), new Box(margin, margin, w, Math.min(8, sheet.height() / 3)),
                        Placeholders.field(first), TextStyle.DEFAULT, true, true);
            }
            case PICTURE -> {
                Optional<Path> chosen = view.choosePicture(s.prefs().lastFolder());
                if (chosen.isEmpty()) yield null;
                String name = addImage(chosen.get());
                if (name == null) yield null;
                double side = Math.min(sheet.width(), sheet.height()) / 2;
                yield new Element.Picture(Element.newId(), new Box(margin, margin, side, side), name, true);
            }
            case SHAPE -> new Element.Figure(Element.newId(), new Box(margin, margin, Math.min(20, w),
                    Math.min(12, sheet.height() / 2)), ShapeKind.RECTANGLE, null, Color.BLACK, 0.3, 0);
            case BARCODE -> new Element.Code(Element.newId(), new Box(margin, margin, Math.min(40, w),
                    Math.min(15, sheet.height() / 2)), CodeType.EAN_13, "400638133393", Color.BLACK, true);
            case QR -> {
                double side = Math.min(20, Math.min(sheet.width(), sheet.height()) - 2 * margin);
                yield new Element.Code(Element.newId(), new Box(sheet.width() - margin - side,
                        sheet.height() - margin - side, side, side), CodeType.QR, "https://amadeos.eu", Color.BLACK,
                        false);
            }
        };
        if (e == null) return;
        selected = e.id();
        changeSide(side().with(e), null);
    }

    /** A field dropped on the label: into the selected text if it was dropped on it, else as a new text. */
    public void dropField(String field, double x, double y) {
        String token = Placeholders.field(field);
        Optional<Element> target = side().elements().stream()
                .filter(e -> e instanceof Element.Text && e.box().rect().contains(x, y)).reduce((a, b) -> b);
        if (target.isPresent() && target.get() instanceof Element.Text t) {
            String text = t.text().isEmpty() ? token : t.text() + "\n" + token;
            selected = t.id();
            changeSide(side().with(t.withText(text).withBox(grow(t.box(), t.style()))), null);
            return;
        }
        SheetTemplate sheet = doc.design().sheet();
        double w = Math.max(10, Math.min(sheet.width() - x - 1, 50));
        Element.Text e = new Element.Text(Element.newId(), new Box(x, y, w, 5), token, TextStyle.DEFAULT, true, true);
        selected = e.id();
        changeSide(side().with(e), null);
    }

    private static Box grow(Box b, TextStyle style) {
        return new Box(b.x(), b.y(), b.w(), b.h() + style.size() * LabelPainter.MM_PER_PT * 1.2);
    }

    /** A field double-clicked in the list: into the text being edited, else dropped at the top left. */
    public void insertField(String field) {
        if (view.insertIntoText(Placeholders.field(field))) return;
        dropField(field, 3, 3);
    }

    public void openElement(String id) {
        select(id);
        view.editContent();
    }

    // ---- label settings ---------------------------------------------------------------------------------------

    public void background(Background background) {
        changeSide(side().withBackground(background), null);
    }

    /** A picture for an element ({@code id}) or, with null, for the background. */
    public void choosePicture(String id) {
        Optional<Path> chosen = view.choosePicture(s.prefs().lastFolder());
        if (chosen.isEmpty()) {
            refresh();
            return;
        }
        String name = addImage(chosen.get());
        if (name == null) return;
        if (id == null) {
            background(side().background().withImage(name));
        } else {
            side().find(id).ifPresent(e -> {
                if (e instanceof Element.Picture p) apply(p.withImage(name));
            });
        }
    }

    /** Reads a picture into the document; returns its name, or null after showing why it failed. */
    String addImage(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                view.showError(msg.format("error.picture", path.getFileName(), PICTURE_TYPES));
                return null;
            }
            s.prefs().setLastFolder(path.toAbsolutePath().getParent());
            LabelDocument.Named named = doc.withImage(path.getFileName().toString(), bytes);
            imageCache.put(named.name(), image);
            doc = named.document();
            return named.name();
        } catch (IOException e) {
            view.showError(msg.format("error.picture", path.getFileName(), e.getMessage()));
            return null;
        }
    }

    BufferedImage image(String name) {
        return imageCache.computeIfAbsent(name, n -> {
            byte[] bytes = doc.images().get(n);
            if (bytes == null) return null;
            try {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                return null;
            }
        });
    }

    public void twoSided(boolean two) {
        if (!two) back = false;
        changeDesign(doc.design().withTwoSided(two));
    }

    public void showSide(boolean showBack) {
        back = showBack && doc.design().twoSided();
        selected = null;
        refresh();
    }

    public void cutGuides(CutGuides guides) {
        changeDesign(doc.design().withCutGuides(guides));
    }

    public void counter(int start, int digits) {
        change(doc.withDesign(doc.design().withCounter(start, digits)), "counter");
    }

    public void chooseSheet() {
        view.chooseSheet(doc.design().sheet()).ifPresent(this::setSheet);
    }

    /** Uses another sheet; elements keep their place, but stay inside the new label. */
    public void setSheet(SheetTemplate sheet) {
        s.prefs().setLastSheet(sheet.id());
        LabelDesign d = doc.design().withSheet(sheet);
        d = d.withSide(false, fit(d.front(), sheet));
        if (d.twoSided()) d = d.withSide(true, fit(d.back(), sheet));
        if (sheet.kind() == SheetKind.BUSINESS_CARD && d.cutGuides() == CutGuides.NONE && sheet.gapX() > 0) {
            d = d.withCutGuides(CutGuides.CROP_MARKS);
        }
        changeDesign(d);
    }

    private static Side fit(Side side, SheetTemplate sheet) {
        Side result = side;
        for (Element e : side.elements()) {
            Box b = e.box();
            double w = Math.min(b.w(), sheet.width());
            double h = Math.min(b.h(), sheet.height());
            double x = Math.max(0, Math.min(b.x(), sheet.width() - w));
            double y = Math.max(0, Math.min(b.y(), sheet.height() - h));
            Box fitted = new Box(x, y, w, h);
            if (!fitted.equals(b)) result = result.with(e.withBox(fitted));
        }
        return result;
    }

    // ---- data -------------------------------------------------------------------------------------------------

    public void chooseData() {
        view.chooseData(doc.source(), doc.merge(), table, doc.design().fields()).ifPresent(choice -> {
            table = choice.table();
            record = 0;
            previewSheet = 0;
            change(doc.withSource(choice.source(), choice.merge()), null);
        });
    }

    /** Reads the document's data again, in the background. */
    public void reloadData() {
        DataSource source = doc.source();
        if (source.kind() == DataSource.Kind.NONE) {
            table = DataTable.NONE;
            refresh();
            return;
        }
        s.background().execute(() -> {
            try {
                DataTable loaded = s.data().load(source);
                s.ui().execute(() -> {
                    table = loaded;
                    refresh();
                });
            } catch (IOException | RuntimeException e) {
                s.ui().execute(() -> {
                    table = DataTable.NONE;
                    view.showError(msg.format("error.data", source.path().isEmpty() ? source.group() : source.path(),
                            e.getMessage()));
                    refresh();
                });
            }
        });
    }

    public void nextRecord() {
        record++;
        followRecord();
    }

    public void previousRecord() {
        record = Math.max(0, record - 1);
        followRecord();
    }

    private void followRecord() {
        List<Merge.Label> labels = labels();
        record = Math.max(0, Math.min(record, labels.size() - 1));
        previewSheet = record / doc.design().sheet().count();
        refresh();
    }

    public void previewSheet(int delta) {
        previewSheet += delta;
        refresh();
    }

    /** A label clicked on the preview: its record is shown in the design. */
    public void previewClicked(int position) {
        int index = previewSheet * doc.design().sheet().count() + position;
        if (index < labels().size()) {
            record = index;
            refresh();
        }
    }

    // ---- files ------------------------------------------------------------------------------------------------

    public void newDesign() {
        if (!confirmed()) return;
        newDesign(doc.design().sheet());
    }

    private void newDesign(SheetTemplate sheet) {
        doc = new LabelDocument(LabelDesign.empty(sheet), Map.of(), DataSource.NONE, MergeSettings.ALL);
        file = null;
        resetState();
        refresh();
    }

    private void resetState() {
        undo.clear();
        redo.clear();
        imageCache.clear();
        table = DataTable.NONE;
        dirty = false;
        selected = null;
        back = false;
        record = 0;
        previewSheet = 0;
        coalesceKey = null;
    }

    public void open() {
        if (!confirmed()) return;
        view.chooseOpen(s.prefs().lastFolder()).ifPresent(this::openFile);
    }

    public void openFile(Path path) {
        try {
            LabelDocument loaded = LabelFiles.read(path);
            doc = loaded;
            file = path;
            resetState();
            s.prefs().setLastFolder(path.toAbsolutePath().getParent());
            s.prefs().addRecentFile(path);
            refresh();
            reloadData();
        } catch (IOException e) {
            view.showError(msg.format("error.open", path.getFileName(), e.getMessage()));
            if (file == null) newDesign(startSheet());
        }
    }

    public List<Path> recentFiles() {
        return s.prefs().recentFiles().stream().filter(Files::isRegularFile).toList();
    }

    public boolean save() {
        if (file == null) return saveAs();
        return write(file);
    }

    public boolean saveAs() {
        String name = file == null ? msg.get("file.untitled") + LabelFiles.EXTENSION : file.getFileName().toString();
        Optional<Path> chosen = view.chooseSave(s.prefs().lastFolder(), name, LabelFiles.EXTENSION);
        if (chosen.isEmpty()) return false;
        Path path = chosen.get();
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(LabelFiles.EXTENSION)) {
            path = path.resolveSibling(path.getFileName() + LabelFiles.EXTENSION);
        }
        return write(path);
    }

    private boolean write(Path path) {
        try {
            LabelFiles.write(doc, path);
            file = path;
            dirty = false;
            s.prefs().setLastFolder(path.toAbsolutePath().getParent());
            s.prefs().addRecentFile(path);
            status = msg.format("status.saved", path.getFileName());
            refresh();
            return true;
        } catch (IOException e) {
            view.showError(msg.format("error.save", path.getFileName(), e.getMessage()));
            return false;
        }
    }

    /** True if there is nothing unsaved, or the user saved or discarded it. */
    public boolean confirmed() {
        if (!dirty) return true;
        return switch (view.askUnsaved(file == null ? msg.get("file.untitled") : file.getFileName().toString())) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    public void exit() {
        if (!confirmed()) return;
        view.close();
        s.exit().run();
    }

    // ---- printing ---------------------------------------------------------------------------------------------

    /** The plan for a print mode, starting at a position of the first sheet. */
    PrintPlan plan(PrintChoice.Mode mode, int start) {
        List<Merge.Label> labels = labels();
        SheetTemplate sheet = doc.design().sheet();
        Merge.Label current = labels.get(Math.min(record, labels.size() - 1));
        return switch (mode) {
            case ALL -> new PrintPlan(sheet, labels, start);
            case CURRENT -> new PrintPlan(sheet, List.of(current), start);
            case FILL_SHEET -> {
                List<Merge.Label> copies = new ArrayList<>();
                for (int i = start; i < sheet.count(); i++) copies.add(current);
                yield new PrintPlan(sheet, copies, start);
            }
        };
    }

    public void print() {
        List<String> printers = PrintJobs.printerNames();
        MainView.PrintRequest request = new MainView.PrintRequest(doc.design(), mode -> plan(mode, 0), printers,
                s.prefs().lastPrinter(), s.prefs()::offset, this::printTestPage);
        view.choosePrint(request).ifPresent(choice -> {
            if (choice.printer() != null) {
                s.prefs().setLastPrinter(choice.printer());
                s.prefs().setOffset(choice.printer(), choice.dx(), choice.dy());
            }
            PrintPlan plan = plan(choice.mode(), choice.start());
            SheetPages pages = new SheetPages(doc.design(), plan, sheetPainter,
                    new SheetPainter.Options(choice.dx(), choice.dy(), choice.outlines(), false, -1));
            if (choice.pdf()) exportPdf(pages);
            else send(pages, choice.printer(), plan.sheets());
        });
    }

    /** Prints the outlines of all labels with the chosen printer and adjustment. */
    void printTestPage(PrintChoice choice) {
        if (choice.printer() == null) return;
        s.prefs().setOffset(choice.printer(), choice.dx(), choice.dy());
        send(SheetPages.testPage(doc.design(), plan(PrintChoice.Mode.ALL, 0), choice.dx(), choice.dy()),
                choice.printer(), 1);
    }

    private void send(SheetPages pages, String printer, int sheets) {
        preloadImages();
        String job = file == null ? msg.get("app.title") : file.getFileName().toString();
        SheetTemplate sheet = doc.design().sheet();
        s.background().execute(() -> {
            try {
                PrinterJob pj = PrinterJob.getPrinterJob();
                PrintService service = service(printer);
                if (service != null) pj.setPrintService(service);
                pj.setJobName(job);
                pj.setPageable(pages);
                PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
                attributes.add(new JobName(job, null));
                MediaSizeName media = media(sheet);
                if (media != null) attributes.add(media);
                if (pages.twoSided()) attributes.add(Sides.TWO_SIDED_LONG_EDGE);
                pj.print(attributes);
                s.ui().execute(() -> {
                    status = msg.format("status.printed", sheets, printer);
                    refresh();
                });
            } catch (Exception e) {
                s.ui().execute(() -> view.showError(msg.format("error.print", e.getMessage())));
            }
        });
    }

    private static PrintService service(String name) {
        for (PrintService p : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    private static MediaSizeName media(SheetTemplate sheet) {
        if (SheetTemplate.Paper.A4.matches(sheet.paperW(), sheet.paperH())) return MediaSizeName.ISO_A4;
        if (SheetTemplate.Paper.A5.matches(sheet.paperW(), sheet.paperH())) return MediaSizeName.ISO_A5;
        if (SheetTemplate.Paper.A6.matches(sheet.paperW(), sheet.paperH())) return MediaSizeName.ISO_A6;
        if (SheetTemplate.Paper.LETTER.matches(sheet.paperW(), sheet.paperH())) return MediaSizeName.NA_LETTER;
        return null;
    }

    /** Exports all labels as a PDF. */
    public void exportPdf() {
        PrintPlan plan = plan(PrintChoice.Mode.ALL, 0);
        exportPdf(new SheetPages(doc.design(), plan, sheetPainter, SheetPainter.Options.PRINT));
    }

    private void exportPdf(SheetPages pages) {
        String base = file == null ? msg.get("file.untitled") : file.getFileName().toString()
                .replaceFirst("\\.label$", "");
        Optional<Path> chosen = view.chooseSave(s.prefs().lastFolder(), base + ".pdf", ".pdf");
        if (chosen.isEmpty()) return;
        Path pdf = chosen.get().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf") ? chosen.get()
                : chosen.get().resolveSibling(chosen.get().getFileName() + ".pdf");
        preloadImages();
        SheetTemplate sheet = doc.design().sheet();
        status = msg.get("status.exporting");
        refresh();
        s.background().execute(() -> {
            try {
                s.pdf().export(pages, sheet.paperW(), sheet.paperH(), pdf, pdf.getFileName().toString());
                s.ui().execute(() -> {
                    status = msg.format("status.exported", pdf.getFileName());
                    refresh();
                });
            } catch (ProgramMissingException e) {
                s.ui().execute(() -> view.showError(msg.get("error.ps2pdf")));
            } catch (Exception e) {
                s.ui().execute(() -> view.showError(msg.format("error.pdf", e.getMessage())));
            }
        });
    }

    /** Reads every picture now, so the background thread that prints only reads the cache. */
    private void preloadImages() {
        for (String name : doc.design().images()) image(name);
    }

    // ---- help and options -------------------------------------------------------------------------------------

    public void handbook() {
        try {
            s.opener().handbook();
        } catch (IOException e) {
            view.showError(msg.format("error.handbook", e.getMessage()));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
            view.showStatus(Messages.commons(msg.locale()).format("language.restart", msg.get("app.title")));
        } catch (IOException e) {
            view.showError(e.getMessage());
        }
    }

    /** Text alignment for tests and the toolbar: the selected text's alignment. */
    Optional<Align> selectedAlign() {
        return side().find(selected == null ? "" : selected).filter(e -> e instanceof Element.Text)
                .map(e -> ((Element.Text) e).style().align());
    }
}
