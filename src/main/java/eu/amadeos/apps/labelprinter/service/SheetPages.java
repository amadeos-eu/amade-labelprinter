package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;

/**
 * The sheets of a print as pages for {@code java.awt.print}: one page per sheet, or front and back after each
 * other for two-sided cards (the printer prints them on both sides of one sheet, turned over the long edge).
 */
public final class SheetPages implements Pageable, Printable {
    private static final double PT_PER_MM = 72 / 25.4;

    private final LabelDesign design;
    private final PrintPlan plan;
    private final SheetPainter painter;
    private final SheetPainter.Options options;
    private final boolean testPage;
    private final PageFormat format;

    public SheetPages(LabelDesign design, PrintPlan plan, SheetPainter painter, SheetPainter.Options options) {
        this(design, plan, painter, options, false);
    }

    private SheetPages(LabelDesign design, PrintPlan plan, SheetPainter painter, SheetPainter.Options options,
                       boolean testPage) {
        this.design = design;
        this.plan = plan;
        this.painter = painter;
        this.options = options;
        this.testPage = testPage;
        this.format = format(design.sheet());
    }

    /** One page with the outline and number of every label, to hold against a sheet of labels. */
    public static SheetPages testPage(LabelDesign design, PrintPlan plan, double dx, double dy) {
        return new SheetPages(design, plan, null, new SheetPainter.Options(dx, dy, true, false, -1), true);
    }

    /** The paper of the sheet, all of it printable (the labels decide what is drawn where). */
    public static PageFormat format(SheetTemplate sheet) {
        Paper paper = new Paper();
        double w = sheet.paperW() * PT_PER_MM;
        double h = sheet.paperH() * PT_PER_MM;
        paper.setSize(w, h);
        paper.setImageableArea(0, 0, w, h);
        PageFormat f = new PageFormat();
        f.setPaper(paper);
        f.setOrientation(PageFormat.PORTRAIT);
        return f;
    }

    public boolean twoSided() {
        return design.twoSided() && !testPage;
    }

    @Override
    public int getNumberOfPages() {
        return testPage ? 1 : plan.sheets() * (twoSided() ? 2 : 1);
    }

    @Override
    public PageFormat getPageFormat(int pageIndex) {
        return format;
    }

    @Override
    public Printable getPrintable(int pageIndex) {
        return this;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= getNumberOfPages()) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            LabelPainter.hints(g);
            g.translate(pageFormat.getImageableX() - format.getImageableX(),
                    pageFormat.getImageableY() - format.getImageableY());
            g.scale(PT_PER_MM, PT_PER_MM);
            if (testPage) {
                SheetPainter.testPage(g, design.sheet(), options.dx(), options.dy());
            } else {
                int sheet = twoSided() ? pageIndex / 2 : pageIndex;
                boolean back = twoSided() && pageIndex % 2 == 1;
                painter.paint(g, design, plan, sheet, back, options);
            }
        } finally {
            g.dispose();
        }
        return PAGE_EXISTS;
    }
}
