package eu.amadeos.apps.labelprinter.domain;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** The outline of one label. */
public enum LabelShape {
    RECTANGLE, ELLIPSE, DISC;

    /** The outline at (x, y) in millimetres. */
    public Shape outline(double x, double y, double w, double h, double radius, double hole) {
        return switch (this) {
            case RECTANGLE -> radius > 0 ? new RoundRectangle2D.Double(x, y, w, h, 2 * radius, 2 * radius)
                    : new Rectangle2D.Double(x, y, w, h);
            case ELLIPSE -> new Ellipse2D.Double(x, y, w, h);
            case DISC -> {
                Area disc = new Area(new Ellipse2D.Double(x, y, w, h));
                if (hole > 0) {
                    disc.subtract(new Area(new Ellipse2D.Double(x + (w - hole) / 2, y + (h - hole) / 2, hole, hole)));
                }
                yield disc;
            }
        };
    }
}
