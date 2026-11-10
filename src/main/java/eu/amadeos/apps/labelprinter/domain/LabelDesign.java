package eu.amadeos.apps.labelprinter.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A whole design: the sheet, the front and (for two-sided cards) the back, the cut guides and the counter.
 * Immutable, so undo keeps the earlier designs.
 *
 * @param back         null for one-sided labels
 * @param counterStart the first number of the «Counter» field
 * @param counterDigits the counter is padded with zeros to this many digits (1 for no padding)
 */
public record LabelDesign(SheetTemplate sheet, Side front, Side back, CutGuides cutGuides, int counterStart,
                          int counterDigits) {
    public LabelDesign {
        Objects.requireNonNull(sheet, "sheet");
        front = front == null ? Side.EMPTY : front;
        cutGuides = cutGuides == null ? CutGuides.NONE : cutGuides;
        counterDigits = Math.max(1, Math.min(9, counterDigits));
    }

    public static LabelDesign empty(SheetTemplate sheet) {
        CutGuides guides = sheet.kind() == SheetKind.BUSINESS_CARD && sheet.gapX() > 0 ? CutGuides.CROP_MARKS
                : CutGuides.NONE;
        return new LabelDesign(sheet, Side.EMPTY, null, guides, 1, 1);
    }

    public boolean twoSided() {
        return back != null;
    }

    public Side side(boolean backSide) {
        return backSide && back != null ? back : front;
    }

    public LabelDesign withSheet(SheetTemplate s) {
        return new LabelDesign(s, front, back, cutGuides, counterStart, counterDigits);
    }

    public LabelDesign withSide(boolean backSide, Side side) {
        return backSide ? new LabelDesign(sheet, front, side, cutGuides, counterStart, counterDigits)
                : new LabelDesign(sheet, side, back, cutGuides, counterStart, counterDigits);
    }

    public LabelDesign withTwoSided(boolean two) {
        if (two == twoSided()) return this;
        return new LabelDesign(sheet, front, two ? Side.EMPTY : null, cutGuides, counterStart, counterDigits);
    }

    public LabelDesign withCutGuides(CutGuides g) {
        return new LabelDesign(sheet, front, back, g, counterStart, counterDigits);
    }

    public LabelDesign withCounter(int start, int digits) {
        return new LabelDesign(sheet, front, back, cutGuides, start, digits);
    }

    /** Every field the design uses, in order of first use, on both sides. */
    public Set<String> fields() {
        Set<String> fields = new LinkedHashSet<>();
        for (Side side : back == null ? List.of(front) : List.of(front, back)) {
            for (Element e : side.elements()) {
                if (e instanceof Element.Text t) fields.addAll(Placeholders.fields(t.text()));
                if (e instanceof Element.Code c) fields.addAll(Placeholders.fields(c.content()));
            }
        }
        fields.removeAll(Placeholders.BUILT_IN);
        return fields;
    }

    /** The names of the pictures the design uses. */
    public Set<String> images() {
        Set<String> names = new LinkedHashSet<>();
        for (Side side : back == null ? List.of(front) : List.of(front, back)) {
            if (side.background().image() != null) names.add(side.background().image());
            for (Element e : side.elements()) {
                if (e instanceof Element.Picture p && p.image() != null) names.add(p.image());
            }
        }
        return names;
    }
}
