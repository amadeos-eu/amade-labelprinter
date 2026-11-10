package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.commons.print.PrintJobs;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramResult;

import java.awt.print.Pageable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Exports the sheets as a PDF: Java writes PostScript, Ghostscript's {@code ps2pdf} turns it into PDF at the
 * sheet's paper size.
 */
public final class PdfExport {
    public static final String PROGRAM = "ps2pdf";
    private static final Duration TIME = Duration.ofMinutes(3);

    private final CommandRunner runner;

    public PdfExport(CommandRunner runner) {
        this.runner = runner;
    }

    public void export(Pageable pages, double paperWmm, double paperHmm, Path pdf, String title)
            throws IOException, PrinterException, InterruptedException {
        Path ps = Files.createTempFile("amade-labelprinter-", ".ps");
        try {
            PrintJobs.writePostScript(pages, ps, title);
            int w = (int) Math.round(paperWmm / 25.4 * 72);
            int h = (int) Math.round(paperHmm / 25.4 * 72);
            ProgramResult result = runner.run(List.of(PROGRAM, "-dDEVICEWIDTHPOINTS=" + w, "-dDEVICEHEIGHTPOINTS=" + h,
                    "-dFIXEDMEDIA", "-dPDFSETTINGS=/prepress", ps.toString(), pdf.toString()), TIME);
            if (!result.succeeded()) throw new IOException(result.failure(PROGRAM));
        } finally {
            Files.deleteIfExists(ps);
        }
    }
}
