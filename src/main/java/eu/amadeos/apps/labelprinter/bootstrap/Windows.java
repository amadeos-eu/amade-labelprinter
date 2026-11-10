package eu.amadeos.apps.labelprinter.bootstrap;

import eu.amadeos.apps.labelprinter.ui.main.MainPresenter;
import eu.amadeos.apps.labelprinter.ui.main.MainWindow;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import java.nio.file.Path;
import java.util.Optional;

/** The one main window of the program, made when it is first needed. */
public final class Windows {
    private final Messages msg;
    private final LocaleSettings locales;
    private final MainPresenter.Services services;
    private final String version;
    private MainWindow main;
    private MainPresenter presenter;

    public Windows(Messages msg, LocaleSettings locales, MainPresenter.Services services, String version) {
        this.msg = msg;
        this.locales = locales;
        this.services = services;
        this.version = version;
    }

    /** Shows the main window; a file is opened. */
    public void showMain(Optional<Path> file) {
        EdtExecutor.requireEdt();
        if (main == null || !main.isDisplayable()) {
            main = new MainWindow(msg, locales, services, version);
            presenter = new MainPresenter(main, msg, locales, services);
            main.bind(presenter);
            main.setVisible(true);
            presenter.start(file);
        } else {
            file.ifPresent(f -> {
                if (presenter.confirmed()) presenter.openFile(f);
            });
            main.toFront();
        }
    }

    public Optional<MainWindow> mainWindow() {
        return Optional.ofNullable(main);
    }

    public Optional<MainPresenter> mainPresenter() {
        return Optional.ofNullable(presenter);
    }
}
