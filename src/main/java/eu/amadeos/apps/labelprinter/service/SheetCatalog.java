package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.SheetKind;
import eu.amadeos.apps.labelprinter.domain.SheetTemplate;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The sheets the program knows: the catalogue that comes with it ({@code sheets.json}, sizes only, no makers'
 * names) and the user's own sheets in {@code ~/.config/amadeos/labelprinter/own-sheets.json}.
 */
public final class SheetCatalog {
    public static final String OWN_FILE = "own-sheets.json";
    private static final Logger LOG = LoggerFactory.getLogger(SheetCatalog.class);

    private final List<SheetTemplate> builtIn;
    private final Path ownFile;
    private final List<SheetTemplate> own = new ArrayList<>();

    public SheetCatalog(Path ownFile) {
        this.ownFile = Objects.requireNonNull(ownFile, "ownFile");
        this.builtIn = loadBuiltIn();
        loadOwn();
    }

    static List<SheetTemplate> loadBuiltIn() {
        try (InputStream in = SheetCatalog.class.getResourceAsStream("/eu/amadeos/apps/labelprinter/sheets.json")) {
            if (in == null) throw new IllegalStateException("sheets.json is missing");
            return read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<SheetTemplate> read(String text) {
        List<SheetTemplate> sheets = new ArrayList<>();
        for (Object o : Json.list(Json.parse(text), "sheets")) {
            try {
                sheets.add(SheetCodec.read(o));
            } catch (RuntimeException e) {
                LOG.warn("Skipping a sheet that does not fit its paper: {}", e.getMessage());
            }
        }
        return sheets;
    }

    private void loadOwn() {
        own.clear();
        if (!Files.isRegularFile(ownFile)) return;
        try {
            own.addAll(read(Files.readString(ownFile)));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read {}: {}", ownFile, e.toString());
        }
    }

    /** Every sheet: the user's own first, then the catalogue. */
    public List<SheetTemplate> all() {
        List<SheetTemplate> list = new ArrayList<>(own);
        list.addAll(builtIn);
        return list;
    }

    public List<SheetTemplate> own() {
        return List.copyOf(own);
    }

    /**
     * The sheets of a kind (null for all, {@code ownOnly} for the user's) on a paper (null for any) that match the
     * search, smallest labels first within a kind.
     */
    public List<SheetTemplate> find(SheetKind kind, boolean ownOnly, SheetTemplate.Paper paper, String search) {
        return all().stream()
                .filter(s -> !ownOnly || s.isOwn())
                .filter(s -> kind == null || s.kind() == kind)
                .filter(s -> paper == null || paper.matches(s.paperW(), s.paperH()))
                .filter(s -> s.matches(search == null ? "" : search))
                .sorted(Comparator.comparing((SheetTemplate s) -> !s.isOwn()).thenComparing(SheetTemplate::kind)
                        .thenComparingDouble(s -> s.width() * s.height()))
                .toList();
    }

    public Optional<SheetTemplate> byId(String id) {
        return all().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** The sheet a new design starts with: 70 × 37 mm, 24 to an A4 sheet, or the Letter one where Letter is used. */
    public SheetTemplate standard(boolean letter) {
        return byId(letter ? "letter-66_7x25_4-3x10" : "a4-70x37-3x8").orElse(builtIn.getFirst());
    }

    /** Saves an own sheet; a new one gets a fresh {@code own-} id, an own one with the same id is replaced. */
    public SheetTemplate saveOwn(SheetTemplate sheet) throws IOException {
        SheetTemplate saved = sheet.isOwn() ? sheet : sheet.withId("own-" + System.currentTimeMillis());
        own.removeIf(s -> s.id().equals(saved.id()));
        own.addFirst(saved);
        writeOwn();
        return saved;
    }

    public void deleteOwn(String id) throws IOException {
        if (own.removeIf(s -> s.id().equals(id))) writeOwn();
    }

    private void writeOwn() throws IOException {
        Files.createDirectories(ownFile.getParent());
        Path tmp = ownFile.resolveSibling(ownFile.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(Map.of("sheets", own.stream().map(SheetCodec::write).toList())));
        Files.move(tmp, ownFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
