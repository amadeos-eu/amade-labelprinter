package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.LabelShape;
import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A sheet as a JSON object, for the catalogue, the user's own sheets and the label files. */
final class SheetCodec {
    private SheetCodec() {
    }

    static Map<String, Object> write(SheetTemplate s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("kind", s.kind().name());
        m.put("paperWidth", s.paperW());
        m.put("paperHeight", s.paperH());
        m.put("columns", s.columns());
        m.put("rows", s.rows());
        m.put("width", s.width());
        m.put("height", s.height());
        m.put("left", s.left());
        m.put("top", s.top());
        m.put("pitchX", s.pitchX());
        m.put("pitchY", s.pitchY());
        if (s.radius() > 0) m.put("radius", s.radius());
        if (s.shape() != LabelShape.RECTANGLE) m.put("shape", s.shape().name());
        if (s.hole() > 0) m.put("hole", s.hole());
        return m;
    }

    /** Reads a sheet; the catalogue names the paper ({@code "paper": "A4"}) instead of giving its size. */
    static SheetTemplate read(Object json) {
        if (!(json instanceof Map<?, ?> m)) throw new IllegalArgumentException("Not a sheet");
        double paperW;
        double paperH;
        Object paper = m.get("paper");
        if (paper instanceof String name) {
            SheetTemplate.Paper p = SheetTemplate.Paper.valueOf(name.toUpperCase(Locale.ROOT));
            paperW = p.width();
            paperH = p.height();
        } else {
            paperW = num(m, "paperWidth", 210);
            paperH = num(m, "paperHeight", 297);
        }
        double width = num(m, "width", 0);
        double height = num(m, "height", 0);
        return new SheetTemplate(str(m, "id", "own-sheet"),
                SheetKind.valueOf(str(m, "kind", SheetKind.MULTI_PURPOSE.name())), paperW, paperH,
                (int) num(m, "columns", 1), (int) num(m, "rows", 1), width, height, num(m, "left", 0),
                num(m, "top", 0), num(m, "pitchX", width), num(m, "pitchY", height), num(m, "radius", 0),
                LabelShape.valueOf(str(m, "shape", LabelShape.RECTANGLE.name())), num(m, "hole", 0));
    }

    static double num(Map<?, ?> m, String key, double fallback) {
        return m.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    static String str(Map<?, ?> m, String key, String fallback) {
        return m.get(key) instanceof String s ? s : fallback;
    }
}
