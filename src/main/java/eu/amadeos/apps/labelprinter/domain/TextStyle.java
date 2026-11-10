package eu.amadeos.apps.labelprinter.domain;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

/**
 * How a text looks.
 *
 * @param family     font family
 * @param size       in points
 * @param background the box behind the text, null for none
 */
public record TextStyle(String family, double size, boolean bold, boolean italic, boolean underline, Align align,
                        Color color, Color background) {
    public static final String DEFAULT_FAMILY = "Inter";
    public static final TextStyle DEFAULT = new TextStyle(DEFAULT_FAMILY, 10, false, false, false, Align.LEFT,
            Color.BLACK, null);

    public TextStyle {
        family = family == null || family.isBlank() ? DEFAULT_FAMILY : family;
        size = Math.max(3, Math.min(200, size));
        Objects.requireNonNull(align, "align");
        color = color == null ? Color.BLACK : color;
    }

    /** The AWT font at {@code points}, which may be smaller than {@link #size()} for text shrunk to fit. */
    public Font font(double points) {
        int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        return new Font(family, style, 12).deriveFont((float) points);
    }

    public TextStyle withFamily(String v) {
        return new TextStyle(v, size, bold, italic, underline, align, color, background);
    }

    public TextStyle withSize(double v) {
        return new TextStyle(family, v, bold, italic, underline, align, color, background);
    }

    public TextStyle withBold(boolean v) {
        return new TextStyle(family, size, v, italic, underline, align, color, background);
    }

    public TextStyle withItalic(boolean v) {
        return new TextStyle(family, size, bold, v, underline, align, color, background);
    }

    public TextStyle withUnderline(boolean v) {
        return new TextStyle(family, size, bold, italic, v, align, color, background);
    }

    public TextStyle withAlign(Align v) {
        return new TextStyle(family, size, bold, italic, underline, v, color, background);
    }

    public TextStyle withColor(Color v) {
        return new TextStyle(family, size, bold, italic, underline, align, v, background);
    }

    public TextStyle withBackground(Color v) {
        return new TextStyle(family, size, bold, italic, underline, align, color, v);
    }
}
