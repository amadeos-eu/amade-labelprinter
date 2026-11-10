package eu.amadeos.apps.labelprinter.ui.dialogs;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/** Small pieces the dialogs share. */
final class DialogParts {
    private DialogParts() {
    }

    static JButton button(Messages msg, String key, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), msg.get(key));
        b.setName(key);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** OK and Cancel at the bottom right; Escape cancels and OK is the default button. */
    static JPanel okCancel(JDialog dialog, JButton ok, JButton cancel, JComponent... left) {
        JPanel bar = new JPanel(new java.awt.BorderLayout());
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JComponent c : left) l.add(c);
        bar.add(l, java.awt.BorderLayout.WEST);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        r.add(cancel);
        r.add(ok);
        bar.add(r, java.awt.BorderLayout.EAST);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> cancel.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return bar;
    }

    static JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    /** A small grey explanation, wrapped at {@code width} pixels. */
    static JLabel hint(String text, int width) {
        JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>");
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        l.setForeground(AmadeUI.theme().textInactive());
        return l;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** A spinner for millimetres with one decimal place. */
    static JSpinner mm(double value, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, step));
        s.setEditor(new JSpinner.NumberEditor(s, "0.0#"));
        s.setPreferredSize(new Dimension(76, s.getPreferredSize().height));
        return s;
    }

    static JSpinner count(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, 1));
        s.setPreferredSize(new Dimension(64, s.getPreferredSize().height));
        return s;
    }

    static double number(JSpinner s) {
        return ((Number) s.getValue()).doubleValue();
    }

    static JPanel flow(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JComponent c : parts) p.add(c);
        return p;
    }

    /** Label and field rows. */
    static final class Form extends JPanel {
        private final GridBagConstraints c = new GridBagConstraints();

        Form() {
            super(new GridBagLayout());
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(3, 0, 3, 8);
            c.gridy = 0;
        }

        void row(String label, JComponent field) {
            c.gridx = 0;
            c.gridwidth = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            if (label != null) add(new JLabel(label), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(field, c);
            c.gridy++;
        }

        void wide(JComponent field) {
            c.gridx = 0;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(field, c);
            c.gridy++;
        }

        void filler() {
            c.gridx = 0;
            c.gridwidth = 2;
            c.weighty = 1;
            add(new JLabel(""), c);
            c.weighty = 0;
            c.gridy++;
        }
    }

    /** An action for key bindings. */
    static AbstractAction run(Runnable r) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }
}
