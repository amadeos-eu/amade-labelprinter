package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.Align;
import eu.amadeos.apps.labelprinter.domain.Background;
import eu.amadeos.apps.labelprinter.domain.Box;
import eu.amadeos.apps.labelprinter.domain.CodeType;
import eu.amadeos.apps.labelprinter.domain.CutGuides;
import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.Element;
import eu.amadeos.apps.labelprinter.domain.LabelDesign;
import eu.amadeos.apps.labelprinter.domain.LabelDocument;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;
import eu.amadeos.apps.labelprinter.domain.ShapeKind;
import eu.amadeos.apps.labelprinter.domain.Side;
import eu.amadeos.apps.labelprinter.domain.TextStyle;
import eu.amadeos.commons.json.Json;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes {@code .label} files: a ZIP with {@code label.json} (the design, the data source and the merge
 * settings) and the pictures under {@code pictures/}. Plain JSON, so other programs and people can read it.
 */
public final class LabelFiles {
    public static final String EXTENSION = ".label";
    public static final String MIME = "application/x-amadeos-label";
    static final String JSON = "label.json";
    static final String PICTURES = "pictures/";
    static final int FORMAT = 1;
    /** Pictures larger than this are not read, so a broken file cannot fill the memory. */
    private static final long MAX_ENTRY = 64L * 1024 * 1024;

    private LabelFiles() {
    }

    public static void write(LabelDocument document, Path file) throws IOException {
        LabelDocument doc = document.pruned();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(JSON));
            zip.write(Json.write(toJson(doc)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> e : doc.images().entrySet()) {
                zip.putNextEntry(new ZipEntry(PICTURES + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static LabelDocument read(Path file) throws IOException {
        String json = null;
        Map<String, byte[]> images = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(file); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals(JSON)) {
                    json = new String(limited(zip), StandardCharsets.UTF_8);
                } else if (name.startsWith(PICTURES) && !entry.isDirectory() && !name.contains("..")) {
                    images.put(name.substring(PICTURES.length()), limited(zip));
                }
            }
        }
        if (json == null) throw new IOException("Not a label file: " + file.getFileName());
        try {
            return fromJson(Json.parseObject(json), images);
        } catch (RuntimeException e) {
            throw new IOException("Damaged label file: " + e.getMessage(), e);
        }
    }

    private static byte[] limited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) > 0) {
            total += n;
            if (total > MAX_ENTRY) throw new IOException("Entry too large");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    // ---- to JSON ----------------------------------------------------------------------------------------------

    static Map<String, Object> toJson(LabelDocument doc) {
        LabelDesign d = doc.design();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("sheet", SheetCodec.write(d.sheet()));
        m.put("front", side(d.front()));
        if (d.back() != null) m.put("back", side(d.back()));
        m.put("cutGuides", d.cutGuides().name());
        m.put("counterStart", d.counterStart());
        m.put("counterDigits", d.counterDigits());
        m.put("data", source(doc.source()));
        m.put("merge", merge(doc.merge()));
        return m;
    }

    private static Map<String, Object> side(Side side) {
        Map<String, Object> m = new LinkedHashMap<>();
        Background b = side.background();
        Map<String, Object> bg = new LinkedHashMap<>();
        if (b.color() != null) bg.put("color", hex(b.color()));
        if (b.image() != null) bg.put("picture", b.image());
        bg.put("bleed", b.bleed());
        m.put("background", bg);
        List<Object> elements = new ArrayList<>();
        for (Element e : side.elements()) elements.add(element(e));
        m.put("elements", elements);
        return m;
    }

    private static Map<String, Object> element(Element e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        switch (e) {
            case Element.Text t -> {
                m.put("type", "text");
                m.put("text", t.text());
                TextStyle s = t.style();
                m.put("font", s.family());
                m.put("size", s.size());
                m.put("bold", s.bold());
                m.put("italic", s.italic());
                m.put("underline", s.underline());
                m.put("align", s.align().name());
                m.put("color", hex(s.color()));
                if (s.background() != null) m.put("background", hex(s.background()));
                m.put("shrink", t.shrink());
                m.put("hideEmptyLines", t.hideEmptyLines());
            }
            case Element.Picture p -> {
                m.put("type", "picture");
                if (p.image() != null) m.put("picture", p.image());
                m.put("keepAspect", p.keepAspect());
            }
            case Element.Figure f -> {
                m.put("type", "shape");
                m.put("shape", f.kind().name());
                if (f.fill() != null) m.put("fill", hex(f.fill()));
                if (f.stroke() != null) m.put("stroke", hex(f.stroke()));
                m.put("strokeWidth", f.strokeWidth());
                m.put("radius", f.radius());
            }
            case Element.Code c -> {
                m.put("type", "code");
                m.put("code", c.type().name());
                m.put("content", c.content());
                m.put("color", hex(c.color()));
                m.put("showText", c.showText());
            }
        }
        Box b = e.box();
        m.put("x", b.x());
        m.put("y", b.y());
        m.put("width", b.w());
        m.put("height", b.h());
        return m;
    }

    private static Map<String, Object> source(DataSource s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", s.kind().name());
        if (!s.path().isEmpty()) m.put("path", s.path());
        if (!s.group().isEmpty()) m.put("group", s.group());
        if (!s.text().isEmpty()) m.put("text", s.text());
        m.put("header", s.header());
        return m;
    }

    private static Map<String, Object> merge(MergeSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("excluded", s.excluded().stream().sorted().map(i -> (Object) i).toList());
        m.put("filter", s.filter());
        m.put("sortBy", s.sortBy());
        m.put("copies", s.copies());
        m.put("mapping", new LinkedHashMap<String, Object>(s.mapping()));
        return m;
    }

    // ---- from JSON --------------------------------------------------------------------------------------------

    static LabelDocument fromJson(Map<String, Object> m, Map<String, byte[]> images) {
        LabelDesign design = new LabelDesign(SheetCodec.read(m.get("sheet")), side(m.get("front")),
                m.containsKey("back") ? side(m.get("back")) : null,
                enumOf(CutGuides.class, SheetCodec.str(m, "cutGuides", "NONE"), CutGuides.NONE),
                (int) SheetCodec.num(m, "counterStart", 1), (int) SheetCodec.num(m, "counterDigits", 1));
        return new LabelDocument(design, images, source(m.get("data")), merge(m.get("merge")));
    }

    private static Side side(Object json) {
        if (!(json instanceof Map<?, ?> m)) return Side.EMPTY;
        Background background = Background.NONE;
        if (m.get("background") instanceof Map<?, ?> bg) {
            background = new Background(color(bg.get("color")),
                    bg.get("picture") instanceof String s ? s : null, bool(bg, "bleed", true));
        }
        List<Element> elements = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (m.get("elements") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> e)) continue;
                Element element = element(e);
                if (element != null && ids.add(element.id())) elements.add(element);
            }
        }
        return new Side(background, elements);
    }

    private static Element element(Map<?, ?> m) {
        String id = SheetCodec.str(m, "id", Element.newId());
        Box box = new Box(SheetCodec.num(m, "x", 0), SheetCodec.num(m, "y", 0), SheetCodec.num(m, "width", 10),
                SheetCodec.num(m, "height", 5));
        return switch (SheetCodec.str(m, "type", "")) {
            case "text" -> new Element.Text(id, box, SheetCodec.str(m, "text", ""),
                    new TextStyle(SheetCodec.str(m, "font", TextStyle.DEFAULT_FAMILY), SheetCodec.num(m, "size", 10),
                            bool(m, "bold", false), bool(m, "italic", false), bool(m, "underline", false),
                            enumOf(Align.class, SheetCodec.str(m, "align", "LEFT"), Align.LEFT),
                            color(m.get("color")), color(m.get("background"))),
                    bool(m, "shrink", true), bool(m, "hideEmptyLines", true));
            case "picture" -> new Element.Picture(id, box, m.get("picture") instanceof String s ? s : null,
                    bool(m, "keepAspect", true));
            case "shape" -> new Element.Figure(id, box,
                    enumOf(ShapeKind.class, SheetCodec.str(m, "shape", "RECTANGLE"), ShapeKind.RECTANGLE),
                    color(m.get("fill")), color(m.get("stroke")), SheetCodec.num(m, "strokeWidth", 0.3),
                    SheetCodec.num(m, "radius", 0));
            case "code" -> new Element.Code(id, box,
                    enumOf(CodeType.class, SheetCodec.str(m, "code", "QR"), CodeType.QR),
                    SheetCodec.str(m, "content", ""), color(m.get("color")), bool(m, "showText", true));
            default -> null;
        };
    }

    private static DataSource source(Object json) {
        if (!(json instanceof Map<?, ?> m)) return DataSource.NONE;
        return new DataSource(enumOf(DataSource.Kind.class, SheetCodec.str(m, "kind", "NONE"), DataSource.Kind.NONE),
                SheetCodec.str(m, "path", ""), SheetCodec.str(m, "group", ""), SheetCodec.str(m, "text", ""),
                bool(m, "header", true));
    }

    private static MergeSettings merge(Object json) {
        if (!(json instanceof Map<?, ?> m)) return MergeSettings.ALL;
        Set<Integer> excluded = new HashSet<>();
        if (m.get("excluded") instanceof List<?> list) {
            for (Object o : list) if (o instanceof Number n) excluded.add(n.intValue());
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        if (m.get("mapping") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String value) mapping.put(key, value);
            });
        }
        return new MergeSettings(excluded, SheetCodec.str(m, "filter", ""), SheetCodec.str(m, "sortBy", ""),
                (int) SheetCodec.num(m, "copies", 1), mapping);
    }

    private static boolean bool(Map<?, ?> m, String key, boolean fallback) {
        return m.get(key) instanceof Boolean b ? b : fallback;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** {@code #1A2B3C}, or {@code #1A2B3C80} with transparency. */
    public static String hex(Color c) {
        String s = String.format(Locale.ROOT, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        return c.getAlpha() == 255 ? s : s + String.format(Locale.ROOT, "%02X", c.getAlpha());
    }

    /** Reads {@code #RGB}, {@code #RRGGBB} and {@code #RRGGBBAA}; anything else is no colour. */
    public static Color color(Object value) {
        if (!(value instanceof String s)) return null;
        String h = s.strip();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            }
            if (h.length() == 6) return new Color(Integer.parseInt(h, 16));
            if (h.length() == 8) {
                long v = Long.parseLong(h, 16);
                return new Color((int) (v >> 24) & 255, (int) (v >> 16) & 255, (int) (v >> 8) & 255, (int) v & 255);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}
