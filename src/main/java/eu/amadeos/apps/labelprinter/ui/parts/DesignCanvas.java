package eu.amadeos.apps.labelprinter.ui.parts;

import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.apps.labelprinter.domain.Side;
import eu.amadeos.apps.labelprinter.service.LabelPainter;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Function;

/**
 * One side of the label, enlarged, with rulers in millimetres. Click selects an element, dragging moves it, the
 * handles resize it, arrow keys nudge it (Shift for 5 mm), Delete removes it; fields dragged from the list are
 * dropped where the mouse is.
 */
public final class DesignCanvas extends JComponent {
    public static final int RULER = 22;
    private static final int MARGIN = 28;
    private static final int HANDLE = 7;
    /** The safe area: keep text this far from the cut edge. */
    public static final double SAFE = 2;

    /** What the canvas tells its owner. */
    public interface Listener {
        void selected(String id);

        /** An element was moved or resized; called once when the mouse is let go. */
        void changed(Element element);

        void delete(String id);

        void dropField(String field, double xMm, double yMm);

        /** A double click on an element, to edit its text or picture. */
        void open(String id);
    }

    private final LabelPainter painter;
    private Listener listener;
    private LabelDesign design;
    private boolean back;
    private Function<String, String> values = f -> null;
    private String selected;
    /** The element being dragged, drawn instead of the one in the design until the mouse is let go. */
    private Element dragging;
    private double zoom = 1;
    private boolean fit = true;

    public DesignCanvas(LabelPainter painter) {
        this.painter = painter;
        setName("design");
        setFocusable(true);
        setPreferredSize(new Dimension(620, 360));
        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        keys();
        setTransferHandler(new FieldDrop());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show(LabelDesign design, boolean back, Function<String, String> values, String selected) {
        this.design = design;
        this.back = back;
        this.values = values;
        this.selected = selected;
        this.dragging = null;
        repaint();
    }

    public String selected() {
        return selected;
    }

    /** Fits the label into the window ({@code fit}) or shows it at a fixed size. */
    public void setZoom(double factor, boolean fit) {
        this.zoom = Math.max(0.25, Math.min(8, factor));
        this.fit = fit;
        repaint();
    }

    public double zoom() {
        return zoom;
    }

    /** Pixels per millimetre now. */
    public double scale() {
        if (design == null) return 4;
        SheetTemplate s = design.sheet();
        double fitScale = Math.min((getWidth() - RULER - 2 * MARGIN) / s.width(),
                (getHeight() - RULER - 2 * MARGIN - 18) / s.height());
        fitScale = Math.max(1, fitScale);
        return fit ? fitScale : fitScale * zoom;
    }

    private double ox() {
        SheetTemplate s = design.sheet();
        return RULER + Math.max(MARGIN, (getWidth() - RULER - s.width() * scale()) / 2);
    }

    private double oy() {
        SheetTemplate s = design.sheet();
        return RULER + Math.max(MARGIN, (getHeight() - RULER - 18 - s.height() * scale()) / 2);
    }

    /** The point in millimetres on the label for a point of the component. */
    public Point2D.Double toMm(Point p) {
        double s = scale();
        return new Point2D.Double((p.x - ox()) / s, (p.y - oy()) / s);
    }

    private Side side() {
        return design.side(back);
    }

    private Element element(String id) {
        if (id == null) return null;
        if (dragging != null && dragging.id().equals(id)) return dragging;
        return side().find(id).orElse(null);
    }

    // ---- painting ---------------------------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        DesktopTheme theme = AmadeUI.theme();
        Graphics2D g = (Graphics2D) g0.create();
        LabelPainter.hints(g);
        Color desk = theme.isDark() ? new Color(0x1B, 0x1D, 0x26) : new Color(0xE3, 0xE7, 0xEC);
        g.setColor(desk);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (design == null) {
            g.dispose();
            return;
        }
        SheetTemplate s = design.sheet();
        double scale = scale();
        double ox = ox();
        double oy = oy();
        rulers(g, theme, s, scale, ox, oy);

        Shape outline = s.shape().outline(0, 0, s.width(), s.height(), s.radius(), s.hole());
        Graphics2D lg = (Graphics2D) g.create();
        lg.translate(ox, oy);
        lg.scale(scale, scale);
        lg.setColor(new Color(0, 0, 0, 70));
        lg.fill(java.awt.geom.AffineTransform.getTranslateInstance(0.6, 0.8).createTransformedShape(outline));
        lg.setColor(Color.WHITE);
        lg.fill(outline);
        Shape clip = lg.getClip();
        lg.clip(outline);
        Side side = side();
        painter.background(lg, side.background(), new Rectangle2D.Double(0, 0, s.width(), s.height()));
        Side shown = dragging == null ? side : side.with(dragging);
        painter.elements(lg, shown, values, true);
        lg.setClip(clip);

        // element frames, the safe area and the selection, in screen pixels
        lg.setStroke(new BasicStroke((float) (1 / scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1,
                new float[]{(float) (3 / scale), (float) (3 / scale)}, 0));
        lg.setColor(new Color(0x9A, 0xC8, 0xD0));
        if (s.shape() == LabelShape.RECTANGLE && s.width() > 3 * SAFE && s.height() > 3 * SAFE) {
            lg.draw(new Rectangle2D.Double(SAFE, SAFE, s.width() - 2 * SAFE, s.height() - 2 * SAFE));
        }
        lg.setColor(new Color(0xB8, 0xC0, 0xC8));
        for (Element e : shown.elements()) {
            if (!e.id().equals(selected)) lg.draw(frame(e));
        }
        lg.setColor(new Color(0xA0, 0xA8, 0xB0));
        lg.setStroke(new BasicStroke((float) (1 / scale)));
        lg.draw(outline);
        lg.dispose();

        Element sel = element(selected);
        if (sel != null) selection(g, sel, scale, ox, oy);

        g.setFont(getFont().deriveFont(11f));
        g.setColor(theme.textInactive());
        g.drawString(s.sizeText(), (int) ox, (int) (oy + s.height() * scale + 18));
        g.dispose();
    }

    private static Shape frame(Element e) {
        if (e instanceof Element.Figure f && f.kind() == ShapeKind.LINE) return LabelPainter.outline(f);
        return e.box().rect();
    }

    private void selection(Graphics2D g, Element e, double scale, double ox, double oy) {
        Color accent = AmadeUI.theme().selectionBackground();
        Box b = e.box();
        Rectangle2D r = new Rectangle2D.Double(ox + b.x() * scale, oy + b.y() * scale, b.w() * scale, b.h() * scale);
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.2f));
        if (e instanceof Element.Figure f && f.kind() == ShapeKind.LINE) {
            g.draw(new Line2D.Double(r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()));
        } else {
            g.draw(r);
        }
        for (Point2D p : handles(r)) {
            Rectangle2D h = new Rectangle2D.Double(p.getX() - HANDLE / 2.0, p.getY() - HANDLE / 2.0, HANDLE, HANDLE);
            g.setColor(Color.WHITE);
            g.fill(h);
            g.setColor(accent);
            g.draw(h);
        }
    }

    /** The four corners: top left, top right, bottom left, bottom right. */
    private static List<Point2D> handles(Rectangle2D r) {
        return List.of(new Point2D.Double(r.getMinX(), r.getMinY()), new Point2D.Double(r.getMaxX(), r.getMinY()),
                new Point2D.Double(r.getMinX(), r.getMaxY()), new Point2D.Double(r.getMaxX(), r.getMaxY()));
    }

    private void rulers(Graphics2D g, DesktopTheme theme, SheetTemplate s, double scale, double ox, double oy) {
        g.setColor(theme.isDark() ? new Color(0x26, 0x29, 0x34) : new Color(0xF4, 0xF6, 0xF8));
        g.fillRect(0, 0, getWidth(), RULER);
        g.fillRect(0, 0, RULER, getHeight());
        g.setColor(theme.textInactive());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        int step = scale >= 4 ? 10 : scale >= 2 ? 20 : 50;
        for (int mm = 0; mm <= Math.ceil(s.width()); mm++) {
            int x = (int) Math.round(ox + mm * scale);
            int len = mm % 10 == 0 ? 10 : mm % 5 == 0 ? 6 : 3;
            if (scale >= 2.5 || mm % 5 == 0) g.drawLine(x, RULER - len, x, RULER);
            if (mm % step == 0) g.drawString(Integer.toString(mm), x + 2, 10);
        }
        for (int mm = 0; mm <= Math.ceil(s.height()); mm++) {
            int y = (int) Math.round(oy + mm * scale);
            int len = mm % 10 == 0 ? 10 : mm % 5 == 0 ? 6 : 3;
            if (scale >= 2.5 || mm % 5 == 0) g.drawLine(RULER - len, y, RULER, y);
            if (mm % step == 0) g.drawString(Integer.toString(mm), 2, y + 10);
        }
    }

    // ---- mouse ------------------------------------------------------------------------------------------------

    /** The topmost element under a point (in mm), with a little room for thin lines. */
    String hit(Point2D mm) {
        if (design == null) return null;
        double tolerance = 4 / scale();
        List<Element> elements = side().elements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element e = elements.get(i);
            if (e instanceof Element.Figure f && f.kind() == ShapeKind.LINE) {
                Box b = f.box();
                if (Line2D.ptSegDist(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), mm.getX(), mm.getY()) <= tolerance) {
                    return e.id();
                }
            } else {
                Rectangle2D r = e.box().rect();
                r.setRect(r.getX() - tolerance, r.getY() - tolerance, r.getWidth() + 2 * tolerance,
                        r.getHeight() + 2 * tolerance);
                if (r.contains(mm)) return e.id();
            }
        }
        return null;
    }

    /** The handle of the selected element under a point of the component, -1 for none. */
    private int handleAt(Point p) {
        Element e = element(selected);
        if (e == null) return -1;
        double s = scale();
        Box b = e.box();
        Rectangle2D r = new Rectangle2D.Double(ox() + b.x() * s, oy() + b.y() * s, b.w() * s, b.h() * s);
        List<Point2D> hs = handles(r);
        for (int i = 0; i < hs.size(); i++) {
            if (hs.get(i).distance(p) <= HANDLE) return i;
        }
        return -1;
    }

    private final class Mouse extends MouseAdapter {
        private Point2D start;
        private Box startBox;
        private int handle = -1;

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (design == null) return;
            handle = handleAt(e.getPoint());
            if (handle < 0) {
                String id = hit(toMm(e.getPoint()));
                if (!java.util.Objects.equals(id, selected)) {
                    selected = id;
                    if (listener != null) listener.selected(id);
                }
            }
            Element el = element(selected);
            start = toMm(e.getPoint());
            startBox = el == null ? null : el.box();
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            Element el = side().find(selected == null ? "" : selected).orElse(null);
            if (el == null || startBox == null) return;
            Point2D now = toMm(e.getPoint());
            double dx = now.getX() - start.getX();
            double dy = now.getY() - start.getY();
            double step = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 ? 1 : 0.1;
            dx = Math.round(dx / step) * step;
            dy = Math.round(dy / step) * step;
            Box b = startBox;
            Box moved = switch (handle) {
                case 0 -> new Box(b.x() + dx, b.y() + dy, b.w() - dx, b.h() - dy);
                case 1 -> new Box(b.x(), b.y() + dy, b.w() + dx, b.h() - dy);
                case 2 -> new Box(b.x() + dx, b.y(), b.w() - dx, b.h() + dy);
                case 3 -> new Box(b.x(), b.y(), b.w() + dx, b.h() + dy);
                default -> b.moved(dx, dy);
            };
            dragging = el.withBox(moved.rounded());
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging != null && listener != null) {
                Element done = dragging;
                dragging = null;
                if (!done.box().equals(startBox)) listener.changed(done);
            }
            dragging = null;
            handle = -1;
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && selected != null && listener != null) listener.open(selected);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int h = handleAt(e.getPoint());
            setCursor(Cursor.getPredefinedCursor(switch (h) {
                case 0 -> Cursor.NW_RESIZE_CURSOR;
                case 1 -> Cursor.NE_RESIZE_CURSOR;
                case 2 -> Cursor.SW_RESIZE_CURSOR;
                case 3 -> Cursor.SE_RESIZE_CURSOR;
                default -> design != null && hit(toMm(e.getPoint())) != null ? Cursor.MOVE_CURSOR
                        : Cursor.DEFAULT_CURSOR;
            }));
        }
    }

    // ---- keys -------------------------------------------------------------------------------------------------

    private void keys() {
        bind("left", KeyEvent.VK_LEFT, 0, () -> nudge(-0.5, 0));
        bind("right", KeyEvent.VK_RIGHT, 0, () -> nudge(0.5, 0));
        bind("up", KeyEvent.VK_UP, 0, () -> nudge(0, -0.5));
        bind("down", KeyEvent.VK_DOWN, 0, () -> nudge(0, 0.5));
        bind("left5", KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, () -> nudge(-5, 0));
        bind("right5", KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, () -> nudge(5, 0));
        bind("up5", KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK, () -> nudge(0, -5));
        bind("down5", KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK, () -> nudge(0, 5));
        bind("delete", KeyEvent.VK_DELETE, 0, this::deleteSelected);
        bind("backspace", KeyEvent.VK_BACK_SPACE, 0, this::deleteSelected);
        bind("escape", KeyEvent.VK_ESCAPE, 0, () -> select(null));
        bind("next", KeyEvent.VK_TAB, 0, () -> cycle(1));
        bind("previous", KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, () -> cycle(-1));
        bind("open", KeyEvent.VK_ENTER, 0, () -> {
            if (selected != null && listener != null) listener.open(selected);
        });
        setFocusTraversalKeysEnabled(false);
    }

    private void bind(String name, int key, int modifiers, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (design != null) action.run();
            }
        });
    }

    void nudge(double dx, double dy) {
        Element e = element(selected);
        if (e != null && listener != null) listener.changed(e.withBox(e.box().moved(dx, dy).rounded()));
    }

    private void deleteSelected() {
        if (selected != null && listener != null) listener.delete(selected);
    }

    private void select(String id) {
        selected = id;
        if (listener != null) listener.selected(id);
        repaint();
    }

    private void cycle(int direction) {
        List<Element> elements = side().elements();
        if (elements.isEmpty()) return;
        int i = -1;
        for (int k = 0; k < elements.size(); k++) if (elements.get(k).id().equals(selected)) i = k;
        int next = Math.floorMod(i + direction, elements.size());
        select(elements.get(next).id());
    }

    // ---- dropping fields --------------------------------------------------------------------------------------

    private final class FieldDrop extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support) || listener == null || design == null) return false;
            try {
                String field = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                Point2D mm = support.isDrop() ? toMm(support.getDropLocation().getDropPoint())
                        : new Point2D.Double(SAFE + 1, SAFE + 1);
                listener.dropField(field.strip(), Math.max(0, mm.getX()), Math.max(0, mm.getY()));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
