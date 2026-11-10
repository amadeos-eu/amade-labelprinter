package eu.amadeos.apps.labelprinter.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.labelprinter.service.LabelFiles;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * What the label printer remembers, in {@code ~/.config/amadeos/labelprinter/labelprinter.properties}: the last
 * sheet and folder, recent colours and files, and the adjustment of each printer. Written at each change.
 */
public class LabelPreferences {
    public static final String FILE = "labelprinter.properties";
    public static final int RECENT_COLORS = 8;
    public static final int RECENT_FILES = 8;
    /** Adjustments larger than this are typing mistakes, not a printer's offset. */
    public static final double MAX_OFFSET = 10;

    private final Path file;
    private final Properties properties;

    @Inject
    public LabelPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public LabelPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Optional<String> lastSheet() {
        return Optional.ofNullable(properties.getProperty("sheet.last")).filter(s -> !s.isBlank());
    }

    public void setLastSheet(String id) {
        set("sheet.last", id);
    }

    public Optional<Path> lastFolder() {
        return Optional.ofNullable(properties.getProperty("folder.last")).filter(s -> !s.isBlank()).map(Path::of);
    }

    public void setLastFolder(Path folder) {
        set("folder.last", folder.toString());
    }

    /** The colours chosen last, newest first. */
    public List<Color> recentColors() {
        List<Color> colors = new ArrayList<>();
        for (String hex : list("colors.recent")) {
            Color c = LabelFiles.color(hex);
            if (c != null) colors.add(c);
        }
        return colors;
    }

    public void addRecentColor(Color color) {
        String hex = LabelFiles.hex(color);
        List<String> list = new ArrayList<>(list("colors.recent"));
        list.remove(hex);
        list.addFirst(hex);
        set("colors.recent", String.join(",", list.subList(0, Math.min(RECENT_COLORS, list.size()))));
    }

    public List<Path> recentFiles() {
        return list("files.recent").stream().map(Path::of).toList();
    }

    public void addRecentFile(Path path) {
        List<String> list = new ArrayList<>(list("files.recent"));
        String s = path.toAbsolutePath().toString();
        list.remove(s);
        list.addFirst(s);
        set("files.recent", String.join("\n", list.subList(0, Math.min(RECENT_FILES, list.size()))));
    }

    /** How far the printer's output must move, in mm: x to the right, y down. */
    public double[] offset(String printer) {
        String value = properties.getProperty("offset." + key(printer), "0,0");
        String[] parts = value.split(",");
        try {
            return new double[]{clamp(Double.parseDouble(parts[0])), clamp(Double.parseDouble(parts[1]))};
        } catch (RuntimeException e) {
            return new double[]{0, 0};
        }
    }

    public void setOffset(String printer, double dx, double dy) {
        set("offset." + key(printer), String.format(Locale.ROOT, "%.1f,%.1f", clamp(dx), clamp(dy)));
    }

    public Optional<String> lastPrinter() {
        return Optional.ofNullable(properties.getProperty("printer.last")).filter(s -> !s.isBlank());
    }

    public void setLastPrinter(String printer) {
        set("printer.last", printer);
    }

    private static double clamp(double v) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v));
    }

    private static String key(String printer) {
        return printer.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private List<String> list(String key) {
        String value = properties.getProperty(key, "");
        String sep = key.startsWith("files") ? "\n" : ",";
        return Arrays.stream(value.split(sep)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private void set(String key, String value) {
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-labelprinter");
    }
}
