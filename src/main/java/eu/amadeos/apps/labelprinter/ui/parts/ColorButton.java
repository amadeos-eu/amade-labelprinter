package eu.amadeos.apps.labelprinter.ui.parts;

import eu.amadeos.apps.labelprinter.service.LabelFiles;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A button that shows a colour and opens a palette: the usual colours, the ones chosen last, "none" where a colour
 * may be left out, a field for {@code #RRGGBB} and the full colour chooser.
 */
public final class ColorButton extends JButton {
    /** The palette: greys, strong colours and light tints that print well. */
    public static final List<Color> PALETTE = List.of(
            Color.WHITE, new Color(0xF2F2F2), new Color(0xBFBFBF), new Color(0x7F7F7F), new Color(0x404040),
            Color.BLACK,
            new Color(0xC0392B), new Color(0xE67E22), new Color(0xF1C40F), new Color(0x27AE60), new Color(0x147A84),
            new Color(0x2980B9),
            new Color(0x2C3E80), new Color(0x8E44AD), new Color(0xD85C8A), new Color(0x8B5A2B), new Color(0xFDF2D0),
            new Color(0xE3F4F1));

    private final Messages msg;
    private final boolean allowNone;
    private final Supplier<List<Color>> recent;
    private final Consumer<Color> chosen;
    private Color color;

    /**
     * @param allowNone whether "no colour" (null) may be chosen
     * @param recent    the colours chosen last
     * @param chosen    told about the new colour (null for none); the caller remembers it as recent
     */
    public ColorButton(Messages msg, boolean allowNone, Supplier<List<Color>> recent, Consumer<Color> chosen) {
        this.msg = msg;
        this.allowNone = allowNone;
        this.recent = recent;
        this.chosen = chosen;
        setHorizontalAlignment(SwingConstants.LEFT);
        setIconTextGap(8);
        setIcon(new Swatch());
        addActionListener(e -> showPalette());
        setColor(null);
    }

    public Color color() {
        return color;
    }

    /** Shows the colour without telling anybody. */
    public void setColor(Color c) {
        this.color = c;
        setText(c == null ? msg.get("color.none") : LabelFiles.hex(c));
        repaint();
    }

    private void pick(Color c) {
        setColor(c);
        chosen.accept(c);
    }

    void showPalette() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(palette(popup));
        popup.show(this, 0, getHeight());
    }

    /** The popup's content; package visible for tests and screenshots. */
    JComponent palette(JPopupMenu popup) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel grid = new JPanel(new GridLayout(3, 6, 4, 4));
        for (Color c : PALETTE) grid.add(swatch(c, popup));
        p.add(grid, BorderLayout.NORTH);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        List<Color> last = recent.get();
        if (!last.isEmpty()) {
            middle.add(new JLabel(msg.get("color.recent")));
            for (Color c : last) middle.add(swatch(c, popup));
        }
        p.add(middle, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        JTextField hex = new JTextField(color == null ? "" : LabelFiles.hex(color), 8);
        hex.setName("color.hex");
        hex.addActionListener(e -> {
            Color c = LabelFiles.color(hex.getText());
            if (c != null) {
                popup.setVisible(false);
                pick(c);
            }
        });
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(new JLabel(msg.get("color.hex")));
        left.add(hex);
        bottom.add(left, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (allowNone) {
            JButton none = new JButton(msg.get("color.none"));
            none.setName("color.none");
            none.addActionListener(e -> {
                popup.setVisible(false);
                pick(null);
            });
            buttons.add(none);
        }
        JButton more = new JButton(msg.get("color.more"));
        more.setName("color.more");
        more.addActionListener(e -> {
            popup.setVisible(false);
            Color c = JColorChooser.showDialog(this, msg.get("color.title"), color == null ? Color.BLACK : color);
            if (c != null) pick(c);
        });
        buttons.add(more);
        bottom.add(buttons, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent swatch(Color c, JPopupMenu popup) {
        JComponent s = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c);
                g.fillRect(2, 2, getWidth() - 4, getHeight() - 4);
                boolean selected = c.equals(color);
                g.setColor(selected ? AmadeUI.theme().selectionBackground() : AmadeUI.theme().textInactive());
                g.setStroke(new BasicStroke(selected ? 2f : 1f));
                g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g.dispose();
            }
        };
        s.setPreferredSize(new Dimension(24, 24));
        s.setToolTipText(LabelFiles.hex(c));
        s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        s.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                pick(c);
            }
        });
        return s;
    }

    /** The colour, or a white box crossed out for none. */
    private final class Swatch implements Icon {
        @Override
        public int getIconWidth() {
            return 22;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (color == null) {
                g.setColor(Color.WHITE);
                g.fillRect(x, y, 22, 14);
                g.setColor(new Color(0xD04040));
                g.drawLine(x + 1, y + 13, x + 21, y + 1);
            } else {
                g.setColor(color);
                g.fillRect(x, y, 22, 14);
            }
            g.setColor(AmadeUI.theme().textInactive());
            g.drawRect(x, y, 21, 13);
            g.dispose();
        }
    }
}
