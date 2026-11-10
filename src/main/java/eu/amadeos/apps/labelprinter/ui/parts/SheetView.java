package eu.amadeos.apps.labelprinter.ui.parts;

import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.PrintPlan;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.service.LabelPainter;
import eu.amadeos.apps.labelprinter.service.SheetPainter;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.IntConsumer;

/** A whole sheet as it will be printed, fitted into the component; a click names the label under the mouse. */
public final class SheetView extends JComponent {
    private static final int PAD = 10;

    private final SheetPainter painter;
    private LabelDesign design;
    private PrintPlan plan;
    private int sheet;
    private boolean back;
    private int highlight = -1;
    private IntConsumer clicked;

    public SheetView(SheetPainter painter, Dimension preferred) {
        this.painter = painter;
        setPreferredSize(preferred);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int position = positionAt(e.getX(), e.getY());
                if (position >= 0 && clicked != null) clicked.accept(position);
            }
        });
    }

    /** Calls back with the position of a clicked label. */
    public void onClick(IntConsumer clicked) {
        this.clicked = clicked;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void show(LabelDesign design, PrintPlan plan, int sheet, boolean back, int highlight) {
        this.design = design;
        this.plan = plan;
        this.sheet = sheet;
        this.back = back;
        this.highlight = highlight;
        repaint();
    }

    /** Only the sheet's labels, empty, for choosing a sheet. */
    public void showEmpty(SheetTemplate template, int highlight) {
        show(LabelDesign.empty(template), new PrintPlan(template, List.of(), 0), 0, false, highlight);
    }

    private double scale() {
        SheetTemplate s = design.sheet();
        return Math.min((getWidth() - 2.0 * PAD) / s.paperW(), (getHeight() - 2.0 * PAD) / s.paperH());
    }

    private Point2D.Double origin() {
        SheetTemplate s = design.sheet();
        double scale = scale();
        return new Point2D.Double((getWidth() - s.paperW() * scale) / 2, (getHeight() - s.paperH() * scale) / 2);
    }

    /** The label position under a point, -1 for none. */
    public int positionAt(int x, int y) {
        if (design == null) return -1;
        SheetTemplate s = design.sheet();
        double scale = scale();
        Point2D.Double o = origin();
        double mx = (x - o.x) / scale;
        double my = (y - o.y) / scale;
        for (int i = 0; i < s.count(); i++) {
            if (SheetPainter.position(s, i, back).contains(mx, my)) return i;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        LabelPainter.hints(g);
        boolean dark = AmadeUI.theme().isDark();
        g.setColor(dark ? new Color(0x1B, 0x1D, 0x26) : new Color(0xE3, 0xE7, 0xEC));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (design == null) {
            g.dispose();
            return;
        }
        SheetTemplate s = design.sheet();
        double scale = scale();
        Point2D.Double o = origin();
        int w = (int) Math.round(s.paperW() * scale);
        int h = (int) Math.round(s.paperH() * scale);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect((int) o.x + 3, (int) o.y + 4, w, h);
        g.setColor(Color.WHITE);
        g.fillRect((int) o.x, (int) o.y, w, h);
        g.translate(o.x, o.y);
        g.scale(scale, scale);
        g.clipRect(0, 0, (int) Math.ceil(s.paperW()), (int) Math.ceil(s.paperH()));
        painter.paint(g, design, plan, sheet, back, SheetPainter.Options.SCREEN.withHighlight(highlight));
        g.dispose();
    }
}
