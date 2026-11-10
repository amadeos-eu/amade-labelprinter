package eu.amadeos.apps.labelprinter.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.amadeos.apps.labelprinter.repository.LabelPreferences;
import eu.amadeos.apps.labelprinter.service.DataLoader;
import eu.amadeos.apps.labelprinter.service.DesktopOpener;
import eu.amadeos.apps.labelprinter.service.PdfExport;
import eu.amadeos.apps.labelprinter.service.SheetCatalog;
import eu.amadeos.apps.labelprinter.ui.main.MainPresenter;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.swing.EdtExecutor;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * amade-labelprinter bindings. Theme, language, directories, the address books and the other shared services come
 * from {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class LabelPrinterModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.labelprinter.messages";

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(LabelPrinterModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    LabelPreferences preferences(AppDirectories directories) {
        return new LabelPreferences(directories);
    }

    // --8<-- [start:windows]
    @Provides
    @Singleton
    Windows windows(Messages msg, LocaleSettings locales, LabelPreferences prefs, AppDirectories directories,
                    XdgDirectories xdg, ContactDirectory contacts, DesktopLauncher launcher,
                    ExecutorService background, EdtExecutor edt) {
        MainPresenter.Services services = new MainPresenter.Services(
                new SheetCatalog(directories.configFile(SheetCatalog.OWN_FILE)),
                new DataLoader(contacts, msg::get), prefs, new PdfExport(CommandRunner.system()),
                new DesktopOpener(launcher), background, edt, () -> System.exit(0), Clock.systemDefaultZone(),
                xdg.documents(), xdg.pictures());
        return new Windows(msg, locales, services, AmadeLabelPrinterApplication.version());
    }
    // --8<-- [end:windows]
}
