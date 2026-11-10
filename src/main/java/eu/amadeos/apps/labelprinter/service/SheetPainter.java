package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.Merge;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.domain.Side;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.TreeSet;

/**
 * Draws a whole sheet, front or back, in millimetres from the paper's top left corner: backgrounds, labels and
 * cut guides. The back is mirrored left to right, so after turning the sheet over its long edge every card's
 * back lies behind its front.
 */
public final class SheetPainter {
    /** How far a bleeding background reaches over the cut edge at most. */
    public static final double BLEED = 2;
    /** Crop marks keep this distance from the labels and are at most this long. */
    public static final double MARK_GAP = 1.5;
    public static final double MARK_LENGTH = 6;
    private static final Color USED = new Color(0xE6, 0xE6, 0xE6);
    private static final Color GUIDE = new Color(0x40, 0x40, 0x40);
    private static final Color SCREEN_OUTLINE = new Color(0xC0, 0xC6, 0xCE);

    private final LabelPainter labels;

    public SheetPainter(LabelPainter labels) {
        this.labels = labels;
    }

    /**
     * What to draw besides the labels.
     *
     * @param dx        printer adjustment to the right, in mm
     * @param dy        printer adjustment down, in mm
     * @param outlines  print a thin line around every label (a test on plain paper)
     * @param screen    on screen: grey used positions, light outlines, codes that cannot be encoded
     * @param highlight a position to mark on screen, -1 for none
     */
    public record Options(double dx, double dy, boolean outlines, boolean screen, int highlight) {
        public static final Options PRINT = new Options(0, 0, false, false, -1);
        public static final Options SCREEN = new Options(0, 0, false, true, -1);

        public Options withHighlight(int position) {
            return new Options(dx, dy, outlines, screen, position);
        }
    }

    /** The label's rectangle on the side of the paper that is drawn; mirrored for the back. */
    public static Rectangle2D position(SheetTemplate sheet, int index, boolean back) {
        Rectangle2D r = sheet.label(index);
        if (!back) return r;
        return new Rectangle2D.Double(sheet.paperW() - r.getX() - r.getWidth(), r.getY(), r.getWidth(), r.getHeight());
    }

    public void paint(Graphics2D g, LabelDesign design, PrintPlan plan, int sheetIndex, boolean back, Options o) {
        SheetTemplate sheet = design.sheet();
        Side side = design.side(back);
        AffineTransform saved = g.getTransform();
        g.translate(o.dx(), o.dy());
        for (int i = 0; i < sheet.count(); i++) {
            Rectangle2D r = position(sheet, i, back);
            Shape outline = sheet.shape().outline(r.getX(), r.getY(), r.getWidth(), r.getHeight(), sheet.radius(),
                    sheet.hole());
            if (plan.used(sheetIndex, i)) {
                if (o.screen()) used(g, outline, r);
                continue;
            }
            Merge.Label label = plan.at(sheetIndex, i);
            if (label != null) label(g, design, side, label, r, outline, o.screen());
            if (o.outlines() || design.cutGuides() == CutGuides.OUTLINES) {
                g.setColor(GUIDE);
                g.setStroke(new BasicStroke(0.15f));
                g.draw(outline);
            } else if (o.screen()) {
                g.setColor(SCREEN_OUTLINE);
                g.setStroke(new BasicStroke(0.25f));
                g.draw(outline);
            }
        }
        if (design.cutGuides() == CutGuides.CROP_MARKS) cropMarks(g, sheet);
        if (o.screen() && o.highlight() >= 0 && o.highlight() < sheet.count()) {
            Rectangle2D r = position(sheet, o.highlight(), back);
            g.setColor(new Color(0x2F, 0xB8, 0xB0));
            g.setStroke(new BasicStroke(0.8f));
            g.draw(sheet.shape().outline(r.getX(), r.getY(), r.getWidth(), r.getHeight(), sheet.radius(),
                    sheet.hole()));
        }
        g.setTransform(saved);
    }

    /** One label at its place on the sheet. */
    public void label(Graphics2D g, LabelDesign design, Side side, Merge.Label label, Rectangle2D r, Shape outline,
                      boolean screen) {
        SheetTemplate sheet = design.sheet();
        Shape clip = g.getClip();
        Background bg = side.background();
        if (!bg.isEmpty()) {
            Rectangle2D area = bg.bleed() && sheet.shape() == LabelShape.RECTANGLE ? bleed(sheet, r) : r;
            if (area == r) g.clip(outline);
            else g.clip(area);
            labels.background(g, bg, area);
            g.setClip(clip);
        }
        g.clip(outline);
        Graphics2D lg = (Graphics2D) g.create();
        lg.translate(r.getX(), r.getY());
        labels.elements(lg, side, label.values(), screen);
        lg.dispose();
        g.setClip(clip);
    }

    /** The label grown by the bleed, at most half the gap to the next label, so neighbours do not overlap. */
    static Rectangle2D bleed(SheetTemplate sheet, Rectangle2D r) {
        double bx = sheet.gapX() > 0 ? Math.min(BLEED, sheet.gapX() / 2) : sheet.columns() == 1 ? BLEED : 0;
        double by = sheet.gapY() > 0 ? Math.min(BLEED, sheet.gapY() / 2) : sheet.rows() == 1 ? BLEED : 0;
        return new Rectangle2D.Double(r.getX() - bx, r.getY() - by, r.getWidth() + 2 * bx, r.getHeight() + 2 * by);
    }

    private static void used(Graphics2D g, Shape outline, Rectangle2D r) {
        g.setColor(USED);
        g.fill(outline);
        g.setColor(new Color(0xB0, 0xB0, 0xB0));
        g.setStroke(new BasicStroke(0.4f));
        double inset = Math.min(r.getWidth(), r.getHeight()) * 0.12;
        g.draw(new Line2D.Double(r.getMinX() + inset, r.getMinY() + inset, r.getMaxX() - inset, r.getMaxY() - inset));
        g.draw(new Line2D.Double(r.getMaxX() - inset, r.getMinY() + inset, r.getMinX() + inset, r.getMaxY() - inset));
    }

    /**
     * Short lines in the margins of the paper, in line with every edge of the labels, so the sheet can be cut
     * with a ruler without lines on the labels themselves.
     */
    static void cropMarks(Graphics2D g, SheetTemplate s) {
        TreeSet<Double> xs = new TreeSet<>();
        TreeSet<Double> ys = new TreeSet<>();
        for (int c = 0; c < s.columns(); c++) {
            xs.add(s.left() + c * s.pitchX());
            xs.add(s.left() + c * s.pitchX() + s.width());
        }
        for (int r = 0; r < s.rows(); r++) {
            ys.add(s.top() + r * s.pitchY());
            ys.add(s.top() + r * s.pitchY() + s.height());
        }
        double top = s.top();
        double bottom = s.paperH() - (s.top() + (s.rows() - 1) * s.pitchY() + s.height());
        double left = s.left();
        double right = s.paperW() - (s.left() + (s.columns() - 1) * s.pitchX() + s.width());
        g.setColor(GUIDE);
        g.setStroke(new BasicStroke(0.15f));
        for (double x : xs) {
            mark(g, x, top - MARK_GAP, x, Math.max(0, top - MARK_GAP - MARK_LENGTH), top);
            double y0 = s.paperH() - bottom + MARK_GAP;
            mark(g, x, y0, x, Math.min(s.paperH(), y0 + MARK_LENGTH), bottom);
        }
        for (double y : ys) {
            mark(g, left - MARK_GAP, y, Math.max(0, left - MARK_GAP - MARK_LENGTH), y, left);
            double x0 = s.paperW() - right + MARK_GAP;
            mark(g, x0, y, Math.min(s.paperW(), x0 + MARK_LENGTH), y, right);
        }
    }

    /** A mark only where the margin has room for it. */
    private static void mark(Graphics2D g, double x1, double y1, double x2, double y2, double margin) {
        if (margin < MARK_GAP + 1) return;
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    /** The test page: every label's outline with its number, nothing else. */
    public static void testPage(Graphics2D g, SheetTemplate sheet, double dx, double dy) {
        AffineTransform saved = g.getTransform();
        g.translate(dx, dy);
        g.setColor(GUIDE);
        g.setStroke(new BasicStroke(0.2f));
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 10).deriveFont(3.5f);
        g.setFont(font);
        for (int i = 0; i < sheet.count(); i++) {
            Rectangle2D r = sheet.label(i);
            g.draw(sheet.shape().outline(r.getX(), r.getY(), r.getWidth(), r.getHeight(), sheet.radius(),
                    sheet.hole()));
            g.draw(new Line2D.Double(r.getCenterX() - 2, r.getCenterY(), r.getCenterX() + 2, r.getCenterY()));
            g.draw(new Line2D.Double(r.getCenterX(), r.getCenterY() - 2, r.getCenterX(), r.getCenterY() + 2));
            g.drawString(Integer.toString(i + 1), (float) r.getX() + 1.5f, (float) r.getY() + 4.5f);
        }
        g.setTransform(saved);
    }
}
