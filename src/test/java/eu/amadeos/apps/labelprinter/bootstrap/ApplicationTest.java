package eu.amadeos.apps.labelprinter.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void theFileToOpenComesFromTheArguments() {
        assertEquals(Optional.empty(), AmadeLabelPrinterApplication.file(new String[]{}));
        assertEquals(Optional.of(Path.of("/home/r/cards.label")),
                AmadeLabelPrinterApplication.file(new String[]{"--x", "/home/r/cards.label"}));
        assertEquals(Optional.of(Path.of("/home/r/my cards.label")),
                AmadeLabelPrinterApplication.file(new String[]{"file:///home/r/my%20cards.label"}));
    }

    @Test
    void versionFromTheJarOrDev() {
        assertEquals("dev", AmadeLabelPrinterApplication.version());
        assertEquals("labelprinter", AmadeLabelPrinterApplication.APP.configName());
    }
}
