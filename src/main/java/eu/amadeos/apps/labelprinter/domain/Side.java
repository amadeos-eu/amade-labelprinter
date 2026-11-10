package eu.amadeos.apps.labelprinter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One side of a label or card: its background and its elements, from back to front. */
public record Side(Background background, List<Element> elements) {
    public static final Side EMPTY = new Side(Background.NONE, List.of());

    public Side {
        background = background == null ? Background.NONE : background;
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    public Optional<Element> find(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Side withBackground(Background b) {
        return new Side(b, elements);
    }

    public Side with(Element element) {
        List<Element> list = new ArrayList<>(elements);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(element.id())) {
                list.set(i, element);
                return new Side(background, list);
            }
        }
        list.add(element);
        return new Side(background, list);
    }

    public Side without(String id) {
        return new Side(background, elements.stream().filter(e -> !e.id().equals(id)).toList());
    }

    /** Moves the element to the front (last) or the back (first). */
    public Side reordered(String id, boolean toFront) {
        Optional<Element> element = find(id);
        if (element.isEmpty()) return this;
        List<Element> list = new ArrayList<>(without(id).elements());
        if (toFront) list.add(element.get());
        else list.addFirst(element.get());
        return new Side(background, list);
    }
}
