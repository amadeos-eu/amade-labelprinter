package eu.amadeos.apps.labelprinter.domain;

import java.awt.Color;
import java.util.Objects;
import java.util.UUID;

/** Something on a label: text, a picture, a shape or a code. Immutable; changes make a new element with the same id. */
public sealed interface Element permits Element.Text, Element.Picture, Element.Figure, Element.Code {
    String id();

    Box box();

    Element withBox(Box box);

    static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Text with fields: {@code «Postcode» «City»} is filled from the record of each label.
     *
     * @param shrink         make the font smaller until the text fits the box
     * @param hideEmptyLines leave out lines whose fields are all empty, so the lines below move up
     */
    record Text(String id, Box box, String text, TextStyle style, boolean shrink, boolean hideEmptyLines)
            implements Element {
        public Text {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(box, "box");
            text = text == null ? "" : text;
            style = style == null ? TextStyle.DEFAULT : style;
        }

        @Override
        public Text withBox(Box b) {
            return new Text(id, b, text, style, shrink, hideEmptyLines);
        }

        public Text withText(String t) {
            return new Text(id, box, t, style, shrink, hideEmptyLines);
        }

        public Text withStyle(TextStyle s) {
            return new Text(id, box, text, s, shrink, hideEmptyLines);
        }

        public Text withShrink(boolean v) {
            return new Text(id, box, text, style, v, hideEmptyLines);
        }

        public Text withHideEmptyLines(boolean v) {
            return new Text(id, box, text, style, shrink, v);
        }
    }

    /**
     * A picture from the design's picture store.
     *
     * @param image      its name in the store
     * @param keepAspect fit the whole picture into the box without stretching it
     */
    record Picture(String id, Box box, String image, boolean keepAspect) implements Element {
        public Picture {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(box, "box");
        }

        @Override
        public Picture withBox(Box b) {
            return new Picture(id, b, image, keepAspect);
        }

        public Picture withImage(String name) {
            return new Picture(id, box, name, keepAspect);
        }

        public Picture withKeepAspect(boolean v) {
            return new Picture(id, box, image, v);
        }
    }

    /**
     * A rectangle, ellipse or line (from the top left to the bottom right corner of the box).
     *
     * @param fill        null for none
     * @param stroke      null for none
     * @param strokeWidth in millimetres
     * @param radius      corner radius of a rectangle
     */
    record Figure(String id, Box box, ShapeKind kind, Color fill, Color stroke, double strokeWidth, double radius)
            implements Element {
        public Figure {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(kind, "kind");
            strokeWidth = Math.max(0, Math.min(20, strokeWidth));
            radius = Math.max(0, radius);
        }

        @Override
        public Figure withBox(Box b) {
            return new Figure(id, b, kind, fill, stroke, strokeWidth, radius);
        }

        public Figure withKind(ShapeKind k) {
            return new Figure(id, box, k, fill, stroke, strokeWidth, radius);
        }

        public Figure withFill(Color c) {
            return new Figure(id, box, kind, c, stroke, strokeWidth, radius);
        }

        public Figure withStroke(Color c) {
            return new Figure(id, box, kind, fill, c, strokeWidth, radius);
        }

        public Figure withStrokeWidth(double w) {
            return new Figure(id, box, kind, fill, stroke, w, radius);
        }

        public Figure withRadius(double r) {
            return new Figure(id, box, kind, fill, stroke, strokeWidth, r);
        }
    }

    /**
     * A barcode or QR code. Its content may hold fields like a text.
     *
     * @param showText print the digits under a barcode
     */
    record Code(String id, Box box, CodeType type, String content, Color color, boolean showText) implements Element {
        public Code {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(type, "type");
            content = content == null ? "" : content;
            color = color == null ? Color.BLACK : color;
        }

        @Override
        public Code withBox(Box b) {
            return new Code(id, b, type, content, color, showText);
        }

        public Code withType(CodeType t) {
            return new Code(id, box, t, content, color, showText);
        }

        public Code withContent(String c) {
            return new Code(id, box, type, c, color, showText);
        }

        public Code withColor(Color c) {
            return new Code(id, box, type, content, c, showText);
        }

        public Code withShowText(boolean v) {
            return new Code(id, box, type, content, color, v);
        }
    }
}
