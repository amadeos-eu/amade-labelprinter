package eu.amadeos.apps.labelprinter.domain;

import java.awt.geom.Rectangle2D;

/** Where an element is on its label, in millimetres from the label's top left corner. */
public record Box(double x, double y, double w, double h) {
    /** Elements are never smaller than this, so they can still be grabbed. */
    public static final double MIN = 1;

    public Box {
        w = Math.max(MIN, w);
        h = Math.max(MIN, h);
    }

    public Box moved(double dx, double dy) {
        return new Box(x + dx, y + dy, w, h);
    }

    public Rectangle2D rect() {
        return new Rectangle2D.Double(x, y, w, h);
    }

    /** Rounded to 0.1 mm, for boxes moved with the mouse. */
    public Box rounded() {
        return new Box(r(x), r(y), r(w), r(h));
    }

    private static double r(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
