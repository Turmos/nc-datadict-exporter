package com.bjuc.datadict.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 极简线性图标集（Graphics2D 手绘，无外部资源依赖）。
 */
public final class Icons {
    private Icons() {
    }

    private interface Drawer {
        void draw(Graphics2D g);
    }

    private static Icon icon(final int w, final int h, final Color color, final Drawer drawer) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawer.draw(g2);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        };
    }

    public static Icon plus(Color c) {
        return icon(14, 14, c, g -> {
            g.draw(new Line2D.Float(7, 2, 7, 12));
            g.draw(new Line2D.Float(2, 7, 12, 7));
        });
    }

    public static Icon trash(Color c) {
        return icon(14, 16, c, g -> {
            Path2D body = new Path2D.Float();
            body.moveTo(3, 5.5);
            body.lineTo(11, 5.5);
            body.lineTo(10.4, 15);
            body.lineTo(3.6, 15);
            body.closePath();
            g.draw(body);
            g.draw(new Line2D.Float(2, 3, 12, 3));
            g.draw(new Line2D.Float(5.5f, 1, 8.5f, 1));
            g.draw(new Line2D.Float(5.4f, 8, 5.7f, 13));
            g.draw(new Line2D.Float(7, 8, 7, 13));
            g.draw(new Line2D.Float(8.6f, 8, 8.3f, 13));
        });
    }

    public static Icon eye(Color c) {
        return icon(15, 13, c, g -> {
            Path2D eye = new Path2D.Float();
            eye.moveTo(2, 6.5);
            eye.curveTo(4, 1.5, 11, 1.5, 13, 6.5);
            eye.curveTo(11, 11.5, 4, 11.5, 2, 6.5);
            g.draw(eye);
            g.fill(new Ellipse2D.Float(6.3f, 5.3f, 2.4f, 2.4f));
        });
    }

    public static Icon eyeOff(Color c) {
        return icon(15, 13, c, g -> {
            Path2D eye = new Path2D.Float();
            eye.moveTo(2, 6.5);
            eye.curveTo(4, 1.5, 11, 1.5, 13, 6.5);
            eye.curveTo(11, 11.5, 4, 11.5, 2, 6.5);
            g.draw(eye);
            g.draw(new Line2D.Float(1.5f, 1, 13.5f, 12));
        });
    }

    public static Icon lock(Color c) {
        return icon(14, 15, c, g -> {
            g.draw(new Arc2D.Double(4.3, 1.8, 5.4, 6.4, 0, 180, Arc2D.OPEN));
            g.fill(new RoundRectangle2D.Float(2.6f, 6.5f, 8.8f, 7, 2, 2));
            g.setColor(new Color(255, 255, 255));
            g.fill(new Ellipse2D.Float(6.2f, 9f, 1.6f, 1.6f));
            g.draw(new Line2D.Float(7, 10.6f, 7, 11.6f));
        });
    }

    public static Icon download(Color c) {
        return icon(15, 15, c, g -> {
            g.draw(new Line2D.Float(7.5f, 1.5f, 7.5f, 9f));
            Path2D head = new Path2D.Float();
            head.moveTo(3.5, 6);
            head.lineTo(7.5, 9.8);
            head.lineTo(11.5, 6);
            g.draw(head);
            g.draw(new Line2D.Float(1.5f, 13.5f, 13.5f, 13.5f));
        });
    }

    public static Icon folder(Color c) {
        return icon(15, 13, c, g -> {
            Path2D f = new Path2D.Float();
            f.moveTo(1.5, 3.3);
            f.lineTo(5.6, 3.3);
            f.lineTo(7.4, 5.1);
            f.lineTo(13.5, 5.1);
            f.lineTo(13.5, 11);
            f.lineTo(1.5, 11);
            f.closePath();
            g.draw(f);
            g.draw(new Line2D.Float(1.5f, 6.8f, 13.5f, 6.8f));
        });
    }

    public static Icon chevronDown(Color c) {
        return icon(13, 12, c, g -> {
            Path2D p = new Path2D.Float();
            p.moveTo(2, 3.5);
            p.lineTo(6.5, 8.5);
            p.lineTo(11, 3.5);
            g.draw(p);
        });
    }

    public static Icon chevronRight(Color c) {
        return icon(13, 13, c, g -> {
            Path2D p = new Path2D.Float();
            p.moveTo(3, 1.5);
            p.lineTo(9.5, 6.5);
            p.lineTo(3, 11.5);
            g.draw(p);
        });
    }

    public static Icon database(Color c) {
        return icon(15, 14, c, g -> {
            g.draw(new Ellipse2D.Float(1.5f, 1.4f, 12, 3.1f));
            g.draw(new Line2D.Float(1.5f, 3, 1.5f, 10.4f));
            g.draw(new Line2D.Float(13.5f, 3, 13.5f, 10.4f));
            g.draw(new Arc2D.Double(1.5, 7.3, 12, 3.1, 0, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(1.5, 7.3, 12, 3.1, 0, -180, Arc2D.OPEN));
        });
    }

    public static Icon save(Color c) {
        return icon(14, 14, c, g -> {
            g.draw(new RoundRectangle2D.Float(1.5f, 1.5f, 11, 11, 2.5f, 2.5f));
            g.draw(new Line2D.Float(4.6f, 1.8f, 4.6f, 5.5f));
            g.draw(new Line2D.Float(9.4f, 1.8f, 9.4f, 5.5f));
            g.draw(new Line2D.Float(3.4f, 12.5f, 3.4f, 8.4f));
            g.draw(new Line2D.Float(10.6f, 12.5f, 10.6f, 8.4f));
            g.draw(new Line2D.Float(3.4f, 10.4f, 10.6f, 10.4f));
        });
    }

    /** 旋转加载指示（加载动画用），angle 为当前旋转角（度） */
    public static Icon spinner(Color c, int angle) {
        return new Icon() {
            @Override
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x + 8, y + 8);
                g2.rotate(Math.toRadians(angle));
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Arc2D.Double(-6, -6, 12, 12, 0, 300, Arc2D.OPEN));
                g2.setStroke(new BasicStroke(0f));
                g2.fill(new Ellipse2D.Double(2.4, -7.2, 3.4, 3.4));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
}
