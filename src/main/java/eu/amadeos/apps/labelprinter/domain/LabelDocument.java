package eu.amadeos.apps.labelprinter.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a {@code .label} file holds: the design, its pictures (name to file bytes), where the data comes from and
 * how it is merged.
 */
public record LabelDocument(LabelDesign design, Map<String, byte[]> images, DataSource source, MergeSettings merge) {
    public LabelDocument {
        Objects.requireNonNull(design, "design");
        images = images == null ? Map.of() : Map.copyOf(images);
        source = source == null ? DataSource.NONE : source;
        merge = merge == null ? MergeSettings.ALL : merge;
    }

    public LabelDocument withDesign(LabelDesign d) {
        return new LabelDocument(d, images, source, merge);
    }

    public LabelDocument withSource(DataSource s, MergeSettings m) {
        return new LabelDocument(design, images, s, m);
    }

    public LabelDocument withMerge(MergeSettings m) {
        return new LabelDocument(design, images, source, m);
    }

    /** With a picture added under a name that is not taken yet; returns the document and the name. */
    public Named withImage(String wanted, byte[] bytes) {
        String base = wanted.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) base = "picture";
        String name = base;
        int n = 2;
        while (images.containsKey(name) && !java.util.Arrays.equals(images.get(name), bytes)) {
            int dot = base.lastIndexOf('.');
            name = dot > 0 ? base.substring(0, dot) + "-" + n + base.substring(dot) : base + "-" + n;
            n++;
        }
        Map<String, byte[]> copy = new LinkedHashMap<>(images);
        copy.put(name, bytes);
        return new Named(new LabelDocument(design, copy, source, merge), name);
    }

    /** Without pictures the design no longer uses. */
    public LabelDocument pruned() {
        Map<String, byte[]> used = new LinkedHashMap<>();
        for (String name : design.images()) {
            if (images.containsKey(name)) used.put(name, images.get(name));
        }
        return new LabelDocument(design, used, source, merge);
    }

    public record Named(LabelDocument document, String name) {
    }
}
