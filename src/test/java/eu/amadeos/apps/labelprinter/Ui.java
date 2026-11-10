package eu.amadeos.apps.labelprinter;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Finding components by name, answering modal dialogs, and collecting every text a window shows. */
public final class Ui {
    private Ui() {
    }

    public static <T extends Component> T find(Container root, Class<T> type, String name) {
        T found = findOrNull(root, type, name);
        if (found == null) throw new AssertionError("no " + type.getSimpleName() + " " + name);
        return found;
    }

    public static <T extends Component> T findOrNull(Container root, Class<T> type, String name) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container inner) {
                T found = findOrNull(inner, type, name);
                if (found != null) return found;
            }
        }
        if (root instanceof JMenu menu) {
            for (Component c : menu.getMenuComponents()) {
                if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
                if (c instanceof Container inner) {
                    T found = findOrNull(inner, type, name);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Runs {@code action} on the next modal dialog that opens. The dialog is closed afterwards if the action did not
     * close it; a failure of the action is kept in {@code failure}.
     */
    public static void whenDialog(Consumer<JDialog> action, AtomicReference<Throwable> failure) {
        whenDialog(JDialog.class, action, failure);
    }

    /** Like {@link #whenDialog(Consumer, AtomicReference)}, for the next dialog of a class. */
    public static void whenDialog(Class<? extends JDialog> type, Consumer<JDialog> action,
                                  AtomicReference<Throwable> failure) {
        Timer t = new Timer(100, null);
        t.addActionListener(e -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && type.isInstance(d) && d.isShowing() && d.isModal()) {
                    t.stop();
                    try {
                        action.accept(d);
                    } catch (Throwable x) {
                        failure.set(x);
                        d.dispose();
                    }
                    return;
                }
            }
        });
        SwingUtilities.invokeLater(t::start);
    }

    /** Runs a step after a short pause, for work the dialog does through invokeLater. */
    public static void later(int ms, Runnable step, JDialog dialog, AtomicReference<Throwable> failure) {
        Timer t = new Timer(ms, e -> {
            try {
                step.run();
            } catch (Throwable x) {
                failure.set(x);
                dialog.dispose();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    public static void rethrow(AtomicReference<Throwable> failure) throws Exception {
        Throwable t = failure.get();
        if (t instanceof Error e) throw e;
        if (t instanceof Exception e) throw e;
    }

    /** Whether a text holds a missing message, which {@code Messages} shows as {@code !key!}. */
    public static boolean missing(String text) {
        return java.util.regex.Pattern.compile("![a-z][a-z0-9.\\-]*!").matcher(text).find();
    }

    /** Every text and tooltip of a component tree and its menus. */
    public static List<String> texts(Component root) {
        List<String> texts = new ArrayList<>();
        collect(root, texts);
        return texts;
    }

    private static void collect(Component c, List<String> texts) {
        if (c instanceof AbstractButton b) {
            add(texts, b.getText());
        }
        if (c instanceof JLabel l) add(texts, l.getText());
        if (c instanceof JComponent j) add(texts, j.getToolTipText());
        if (c instanceof JMenu m) for (Component inner : m.getMenuComponents()) collect(inner, texts);
        if (c instanceof Window w && w instanceof javax.swing.RootPaneContainer r) {
            JRootPane pane = r.getRootPane();
            JMenuBar bar = pane.getJMenuBar();
            if (bar != null) collect(bar, texts);
        }
        if (c instanceof Container inner) for (Component child : inner.getComponents()) collect(child, texts);
    }

    private static void add(List<String> texts, String text) {
        if (text != null && !text.isEmpty()) texts.add(text);
    }
}
