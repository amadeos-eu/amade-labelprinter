package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.ui.main.MainPresenter;
import eu.amadeos.commons.desktop.DesktopLauncher;

import java.io.IOException;

/** Opens the handbook through the desktop. */
public class DesktopOpener implements MainPresenter.Opener {
    /** The handbook section about the Label Printer. */
    public static final String HANDBOOK = "https://docs.amadeos.eu/amadeos/43-writepad/#label-printer";

    private final DesktopLauncher launcher;

    public DesktopOpener(DesktopLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void handbook() throws IOException {
        launcher.browse(HANDBOOK);
    }
}
