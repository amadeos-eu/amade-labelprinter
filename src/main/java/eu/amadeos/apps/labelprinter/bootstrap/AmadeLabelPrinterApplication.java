package eu.amadeos.apps.labelprinter.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Starts the Label Printer: the main window with an empty label, or the {@code .label} file given as argument
 * (a double-click in Files).
 */
public final class AmadeLabelPrinterApplication {
    public static final AppInfo APP = new AppInfo("amade-labelprinter", "Label Printer", "labelprinter");

    private AmadeLabelPrinterApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeLabelPrinterApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-labelprinter " + version());
            return;
        }
        Optional<Path> file = file(args);
        AmadeApplication.launch(APP, injector -> injector.getInstance(Windows.class).showMain(file),
                new LabelPrinterModule());
    }
    // --8<-- [end:main]

    /** The first argument that is a file; {@code file://} URIs (some file managers pass them) become paths. */
    static Optional<Path> file(String[] args) {
        return Arrays.stream(args).filter(a -> !a.startsWith("--")).findFirst().map(a -> {
            if (a.startsWith("file://")) {
                try {
                    return Path.of(java.net.URI.create(a));
                } catch (IllegalArgumentException e) {
                    return Path.of(a.substring(7));
                }
            }
            return Path.of(a);
        });
    }
}
