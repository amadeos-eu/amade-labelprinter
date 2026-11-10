package eu.amadeos.apps.labelprinter.ui.parts;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** The toolbar and property icons of the label printer, drawn on the 16-unit grid like the commons glyphs. */
public enum LabelGlyph implements ToolIcon.Glyph {
    TEXT, FIELD, PICTURE, SHAPE, BARCODE, QR, SHEET, DATA, ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, BOLD, ITALIC,
    UNDERLINE, FRONT, BACK, CARD_FRONT, CARD_BACK, TEST_PAGE;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case TEXT -> {
                pen.letter("A", new Font("Inter", Font.BOLD, 13), 13);
                pen.line(9.5, 14.5, 14.5, 14.5);
            }
            case FIELD -> {
                pen.fillAndDraw(new RoundRectangle2D.Double(0.5, 3.5, 15, 9, 3, 3), pen.light());
                pen.letter("«»", new Font("Inter", Font.BOLD, 8), 10.5);
            }
            case PICTURE -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                Path2D m = new Path2D.Double();
                m.moveTo(2, 13);
                m.lineTo(6, 8);
                m.lineTo(9, 11);
                m.lineTo(11, 9);
                m.lineTo(14, 13);
                m.closePath();
                pen.fill(m, pen.ink());
                pen.fill(new Ellipse2D.Double(10, 4.5, 3, 3), pen.light());
            }
            case SHAPE -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 1.5, 8, 8), pen.paper());
                pen.fillAndDraw(new Ellipse2D.Double(6.5, 6.5, 8, 8), pen.light());
            }
            case BARCODE -> {
                int[] w = {1, 1, 2, 1, 1, 2, 1, 2, 1, 1};
                double x = 1.5;
                for (int i = 0; i < w.length; i++) {
                    if (i % 2 == 0) pen.fill(new Rectangle2D.Double(x, 2.5, w[i], 11), pen.ink());
                    x += w[i] + 0.5;
                }
            }
            case QR -> {
                for (double[] f : new double[][]{{1.5, 1.5}, {9.5, 1.5}, {1.5, 9.5}}) {
                    pen.draw(new Rectangle2D.Double(f[0], f[1], 5, 5));
                    pen.fill(new Rectangle2D.Double(f[0] + 1.5, f[1] + 1.5, 2, 2), pen.ink());
                }
                pen.fill(new Rectangle2D.Double(10, 10, 2, 2), pen.ink());
                pen.fill(new Rectangle2D.Double(13, 12, 2, 3), pen.ink());
                pen.fill(new Rectangle2D.Double(10, 13, 2, 2), pen.ink());
            }
            case SHEET -> {
                pen.fillAndDraw(new Rectangle2D.Double(2.5, 0.5, 11, 15), pen.paper());
                for (int r = 0; r < 4; r++)
                    for (int c = 0; c < 2; c++)
                        pen.fill(new Rectangle2D.Double(4 + c * 4.5, 2 + r * 3.4, 3.5, 2.6), pen.light());
            }
            case DATA -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                pen.fill(new Rectangle2D.Double(2, 3, 12, 2.5), pen.light());
                pen.line(1.5, 5.5, 14.5, 5.5);
                pen.line(1.5, 8.5, 14.5, 8.5);
                pen.line(1.5, 11, 14.5, 11);
                pen.line(6.5, 2.5, 6.5, 13.5);
            }
            case ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT -> {
                double[] len = {13, 9, 11, 7};
                for (int i = 0; i < 4; i++) {
                    double x = this == ALIGN_LEFT ? 1.5 : this == ALIGN_RIGHT ? 14.5 - len[i] : 8 - len[i] / 2;
                    pen.line(x, 3.5 + i * 3, x + len[i], 3.5 + i * 3);
                }
            }
            case BOLD -> pen.letter("B", new Font("Inter", Font.BOLD, 12), 12.5);
            case ITALIC -> pen.letter("I", new Font("DejaVu Serif", Font.ITALIC, 12), 12.5);
            case UNDERLINE -> {
                pen.letter("U", new Font("Inter", Font.PLAIN, 11), 11);
                pen.line(3.5, 14.5, 12.5, 14.5);
            }
            case FRONT -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 1.5, 8, 8), pen.paper());
                pen.fillAndDraw(new Rectangle2D.Double(6.5, 6.5, 8, 8), pen.light());
            }
            case BACK -> {
                pen.fillAndDraw(new Rectangle2D.Double(6.5, 6.5, 8, 8), pen.paper());
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 1.5, 8, 8), pen.light());
            }
            case CARD_FRONT -> {
                pen.fillAndDraw(new Rectangle2D.Double(0.5, 3.5, 15, 9), pen.paper());
                pen.line(2.5, 6.5, 9.5, 6.5);
                pen.line(2.5, 9.5, 7.5, 9.5);
                pen.fill(new Rectangle2D.Double(11, 7, 3, 3), pen.light());
            }
            case CARD_BACK -> {
                pen.fillAndDraw(new Rectangle2D.Double(0.5, 3.5, 15, 9), pen.light());
                pen.letter("B", new Font("Inter", Font.BOLD, 7), 10.5);
            }
            case TEST_PAGE -> {
                pen.fillAndDraw(new Rectangle2D.Double(2.5, 0.5, 11, 15), pen.paper());
                for (int r = 0; r < 4; r++) {
                    for (int c = 0; c < 2; c++) pen.draw(new Rectangle2D.Double(4.5 + c * 4.5, 2.5 + r * 3.4, 3, 2.2));
                }
            }
        }
    }
}

