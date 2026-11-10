package eu.amadeos.apps.labelprinter.domain;

import java.awt.Color;

/**
 * The background of a label or card side.
 *
 * @param color null for none
 * @param image the name of a picture in the design's picture store, null for none; it covers the colour
 * @param bleed true to fill up to the cut edge and a little beyond (cards on plain paper are cut through it),
 *              false to keep it inside the label
 */
public record Background(Color color, String image, boolean bleed) {
    public static final Background NONE = new Background(null, null, true);

    public boolean isEmpty() {
        return color == null && image == null;
    }

    public Background withColor(Color c) {
        return new Background(c, null, bleed);
    }

    public Background withImage(String name) {
        return new Background(color, name, bleed);
    }

    public Background withBleed(boolean b) {
        return new Background(color, image, b);
    }
}
