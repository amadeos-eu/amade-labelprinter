package eu.amadeos.apps.labelprinter.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.Placeholders;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.Side;
import eu.amadeos.apps.labelprinter.domain.TextStyle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Draws one side of one label with the values of one record. The graphics are in millimetres with the origin at
 * the label's top left corner; the caller clips to the label's outline. The same drawing serves the design view,
 * the sheet preview, the printer and the PDF.
 */
public final class LabelPainter {
    /** Millimetres per point. */
    public static final double MM_PER_PT = 25.4 / 72;
    /** Text never shrinks below this size, in points. */
    public static final double MIN_POINTS = 4;
    private static final Color ERROR = new Color(0xD0, 0x40, 0x40);

    private final Function<String, BufferedImage> images;

    /** @param images the picture with a name from the design's store, null if there is none */
    public LabelPainter(Function<String, BufferedImage> images) {
        this.images = images;
    }

    /** Sets the rendering hints the painter needs; call once on a fresh graphics. */
    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Fills the background: the colour, then the picture cropped to fill the area.
     *
     * @param area the label, or more for a background that bleeds over the cut edge
     */
    public void background(Graphics2D g, Background background, Rectangle2D area) {
        if (background.color() != null) {
            g.setColor(background.color());
            g.fill(area);
        }
        BufferedImage image = background.image() == null ? null : images.apply(background.image());
        if (image != null) {
            double scale = Math.max(area.getWidth() / image.getWidth(), area.getHeight() / image.getHeight());
            double w = image.getWidth() * scale;
            double h = image.getHeight() * scale;
            drawImage(g, image, area.getCenterX() - w / 2, area.getCenterY() - h / 2, w, h);
        }
    }

    /**
     * Draws the elements of a side.
     *
     * @param values     a field's value; null keeps {@code «Field»} visible (design without data)
     * @param showErrors marks codes whose content cannot be encoded (only on screen)
     */
    public void elements(Graphics2D g, Side side, Function<String, String> values, boolean showErrors) {
        for (Element e : side.elements()) element(g, e, values, showErrors);
    }

    public void element(Graphics2D g, Element e, Function<String, String> values, boolean showErrors) {
        switch (e) {
            case Element.Text t -> text(g, t, values);
            case Element.Picture p -> picture(g, p, showErrors);
            case Element.Figure f -> figure(g, f);
            case Element.Code c -> code(g, c, values, showErrors);
        }
    }

    // ---- text -------------------------------------------------------------------------------------------------

    /** The lines of a text with its fields filled, as they are drawn. */
    public static List<String> lines(Element.Text t, Function<String, String> values) {
        String filled = Placeholders.fill(t.text(), values, t.hideEmptyLines());
        return filled.isEmpty() ? List.of() : List.of(filled.split("\n", -1));
    }

    /** The font size in points the text is drawn at: its size, or smaller to fit the box. */
    public static double fittedPoints(Element.Text t, List<String> lines, FontRenderContext frc) {
        TextStyle s = t.style();
        if (!t.shrink() || lines.isEmpty()) return s.size();
        double points = s.size();
        double mm = points * MM_PER_PT;
        Font font = s.font(mm);
        double widest = 0;
        for (String line : lines) widest = Math.max(widest, font.getStringBounds(line, frc).getWidth());
        double height = lines.size() * mm * 1.2;
        double factor = Math.min(1, Math.min(t.box().w() / Math.max(widest, 0.01), t.box().h() / height));
        return Math.max(MIN_POINTS, points * factor * 0.995);
    }

    private void text(Graphics2D g, Element.Text t, Function<String, String> values) {
        Box b = t.box();
        TextStyle s = t.style();
        if (s.background() != null) {
            g.setColor(s.background());
            g.fill(b.rect());
        }
        List<String> lines = lines(t, values);
        if (lines.isEmpty()) return;
        double points = fittedPoints(t, lines, g.getFontRenderContext());
        double mm = points * MM_PER_PT;
        Font font = s.font(mm);
        g.setFont(font);
        g.setColor(s.color());
        java.awt.font.LineMetrics lm = font.getLineMetrics("Ag", g.getFontRenderContext());
        double lineHeight = mm * 1.2;
        double y = b.y() + (lineHeight - lm.getAscent() - lm.getDescent()) / 2 + lm.getAscent();
        for (String line : lines) {
            double width = font.getStringBounds(line, g.getFontRenderContext()).getWidth();
            double x = switch (s.align()) {
                case LEFT -> b.x();
                case CENTER -> b.x() + (b.w() - width) / 2;
                case RIGHT -> b.x() + b.w() - width;
            };
            if (!line.isEmpty()) g.drawString(line, (float) x, (float) y);
            if (s.underline() && !line.isBlank()) {
                g.setStroke(new BasicStroke((float) (mm * 0.06)));
                double uy = y + mm * 0.12;
                g.draw(new Line2D.Double(x, uy, x + width, uy));
            }
            y += lineHeight;
        }
    }

    // ---- picture ----------------------------------------------------------------------------------------------

    private void picture(Graphics2D g, Element.Picture p, boolean showErrors) {
        Box b = p.box();
        BufferedImage image = p.image() == null ? null : images.apply(p.image());
        if (image == null) {
            if (showErrors) placeholder(g, b.rect());
            return;
        }
        if (p.keepAspect()) {
            double scale = Math.min(b.w() / image.getWidth(), b.h() / image.getHeight());
            double w = image.getWidth() * scale;
            double h = image.getHeight() * scale;
            drawImage(g, image, b.x() + (b.w() - w) / 2, b.y() + (b.h() - h) / 2, w, h);
        } else {
            drawImage(g, image, b.x(), b.y(), b.w(), b.h());
        }
    }

    private static void drawImage(Graphics2D g, BufferedImage image, double x, double y, double w, double h) {
        Graphics2D ig = (Graphics2D) g.create();
        ig.translate(x, y);
        ig.scale(w / image.getWidth(), h / image.getHeight());
        ig.drawImage(image, 0, 0, null);
        ig.dispose();
    }

    private static void placeholder(Graphics2D g, Rectangle2D r) {
        g.setColor(new Color(0x90, 0x90, 0x90));
        g.setStroke(new BasicStroke(0.2f));
        g.draw(r);
        g.draw(new Line2D.Double(r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY()));
        g.draw(new Line2D.Double(r.getMaxX(), r.getMinY(), r.getMinX(), r.getMaxY()));
    }

    // ---- shapes -----------------------------------------------------------------------------------------------

    public static Shape outline(Element.Figure f) {
        Box b = f.box();
        return switch (f.kind()) {
            case RECTANGLE -> f.radius() > 0
                    ? new RoundRectangle2D.Double(b.x(), b.y(), b.w(), b.h(), 2 * f.radius(), 2 * f.radius())
                    : new Rectangle2D.Double(b.x(), b.y(), b.w(), b.h());
            case ELLIPSE -> new Ellipse2D.Double(b.x(), b.y(), b.w(), b.h());
            case LINE -> new Line2D.Double(b.x(), b.y(), b.x() + b.w(), b.y() + b.h());
        };
    }

    private static void figure(Graphics2D g, Element.Figure f) {
        Shape shape = outline(f);
        if (f.fill() != null && f.kind() != ShapeKind.LINE) {
            g.setColor(f.fill());
            g.fill(shape);
        }
        if (f.stroke() != null && f.strokeWidth() > 0) {
            g.setColor(f.stroke());
            g.setStroke(new BasicStroke((float) f.strokeWidth(), f.kind() == ShapeKind.LINE ? BasicStroke.CAP_ROUND
                    : BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(shape);
        }
    }

    // ---- codes ------------------------------------------------------------------------------------------------

    private static final Map<CodeType, BarcodeFormat> FORMATS = new EnumMap<>(Map.of(
            CodeType.QR, BarcodeFormat.QR_CODE, CodeType.DATA_MATRIX, BarcodeFormat.DATA_MATRIX,
            CodeType.EAN_13, BarcodeFormat.EAN_13, CodeType.EAN_8, BarcodeFormat.EAN_8,
            CodeType.CODE_128, BarcodeFormat.CODE_128, CodeType.CODE_39, BarcodeFormat.CODE_39));

    /** The modules of a code, or null if the content cannot be encoded (such as letters in an EAN). */
    public static BitMatrix encode(CodeType type, String content) {
        if (content == null || content.isEmpty()) return null;
        Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        if (type == CodeType.QR) hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        try {
            return new MultiFormatWriter().encode(content, FORMATS.get(type), 0, 0, hints);
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }

    private static void code(Graphics2D g, Element.Code c, Function<String, String> values, boolean showErrors) {
        Box b = c.box();
        String content = Placeholders.fill(c.content(), field -> {
            String v = values.apply(field);
            return v == null ? "" : v;
        }, false).replace("\n", c.type().square() ? "\n" : " ");
        BitMatrix matrix = encode(c.type(), content);
        if (matrix == null) {
            if (showErrors) {
                placeholder(g, b.rect());
                g.setColor(ERROR);
                Font font = new Font(Font.SANS_SERIF, Font.BOLD, 10).deriveFont((float) Math.min(b.h(), b.w()) * 0.5f);
                g.setFont(font);
                double w = font.getStringBounds("!", g.getFontRenderContext()).getWidth();
                float ascent = font.getLineMetrics("!", g.getFontRenderContext()).getAscent();
                g.drawString("!", (float) (b.x() + (b.w() - w) / 2), (float) (b.y() + b.h() / 2 + ascent / 2.5));
            }
            return;
        }
        g.setColor(c.color());
        if (c.type().square()) {
            double size = Math.min(b.w(), b.h());
            double module = size / Math.max(matrix.getWidth(), matrix.getHeight());
            double x0 = b.x() + (b.w() - module * matrix.getWidth()) / 2;
            double y0 = b.y() + (b.h() - module * matrix.getHeight()) / 2;
            fillModules(g, matrix, x0, y0, module, module);
        } else {
            double textHeight = c.showText() ? Math.min(b.h() * 0.25, 3.2) : 0;
            double barHeight = b.h() - textHeight;
            double module = b.w() / matrix.getWidth();
            fillModules(g, matrix, b.x(), b.y(), module, barHeight / matrix.getHeight());
            if (c.showText()) {
                Font font = new Font(Font.MONOSPACED, Font.PLAIN, 10).deriveFont((float) (textHeight * 0.9));
                g.setFont(font);
                double w = font.getStringBounds(content, g.getFontRenderContext()).getWidth();
                float ascent = font.getLineMetrics(content, g.getFontRenderContext()).getAscent();
                g.drawString(content, (float) (b.x() + (b.w() - w) / 2), (float) (b.y() + barHeight + ascent));
            }
        }
    }

    /** Fills runs of dark modules as rectangles, joined so there are no hairlines between them. */
    private static void fillModules(Graphics2D g, BitMatrix m, double x0, double y0, double mw, double mh) {
        Path2D path = new Path2D.Double();
        for (int y = 0; y < m.getHeight(); y++) {
            int x = 0;
            while (x < m.getWidth()) {
                if (!m.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < m.getWidth() && m.get(x, y)) x++;
                path.append(new Rectangle2D.Double(x0 + start * mw, y0 + y * mh, (x - start) * mw, mh * 1.0001),
                        false);
            }
        }
        g.fill(path);
    }

    /** Words for screen readers and tests: the texts of a side as filled for a record. */
    public static List<String> texts(Side side, Function<String, String> values) {
        List<String> texts = new ArrayList<>();
        for (Element e : side.elements()) {
            if (e instanceof Element.Text t) texts.add(String.join("\n", lines(t, values)));
        }
        return texts;
    }
}
