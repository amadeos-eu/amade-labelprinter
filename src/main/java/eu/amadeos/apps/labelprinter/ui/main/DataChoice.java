package eu.amadeos.apps.labelprinter.ui.main;

import eu.amadeos.apps.labelprinter.domain.DataSource;
import eu.amadeos.apps.labelprinter.domain.DataTable;
import eu.amadeos.apps.labelprinter.domain.MergeSettings;

/** What the data window chose: the source, the data it read and how to merge it. */
public record DataChoice(DataSource source, DataTable table, MergeSettings merge) {
}
