package eu.amadeos.apps.labelprinter.domain;

import java.util.Map;
import java.util.Set;

/**
 * Which records of the data are printed, how often and in which order, and which column fills which field.
 *
 * @param excluded rows (indexes into the data) the user unticked
 * @param filter   {@code City = Esslingen}, {@code City != Ulm}, or text that any column contains; "" for all
 * @param sortBy   the column to sort by, "" to keep the order of the data
 * @param copies   labels per record, at least 1
 * @param mapping  field of the design to column of the data, for fields whose column has another name
 */
public record MergeSettings(Set<Integer> excluded, String filter, String sortBy, int copies,
                            Map<String, String> mapping) {
    public static final MergeSettings ALL = new MergeSettings(Set.of(), "", "", 1, Map.of());

    public MergeSettings {
        excluded = excluded == null ? Set.of() : Set.copyOf(excluded);
        filter = filter == null ? "" : filter.strip();
        sortBy = sortBy == null ? "" : sortBy;
        copies = Math.max(1, Math.min(999, copies));
        mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
    }

    public MergeSettings withExcluded(Set<Integer> v) {
        return new MergeSettings(v, filter, sortBy, copies, mapping);
    }

    public MergeSettings withFilter(String v) {
        return new MergeSettings(excluded, v, sortBy, copies, mapping);
    }

    public MergeSettings withSortBy(String v) {
        return new MergeSettings(excluded, filter, v, copies, mapping);
    }

    public MergeSettings withCopies(int v) {
        return new MergeSettings(excluded, filter, sortBy, v, mapping);
    }

    public MergeSettings withMapping(Map<String, String> v) {
        return new MergeSettings(excluded, filter, sortBy, copies, v);
    }
}
