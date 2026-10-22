package com.submithub.experiments;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 외부 라이브러리 없이 Java2D 로 그리는 간단한 PNG 차트 (꺾은선, 묶음 막대). */
final class ChartPng {

    private static final Color[] PALETTE = {new Color(0x1f77b4), new Color(0xff7f0e), new Color(0x2ca02c),
            new Color(0xd62728), new Color(0x9467bd), new Color(0x8c564b)};
    private static final int W = 960;
    private static final int H = 560;
    private static final int LEFT = 90;
    private static final int RIGHT = 30;
    private static final int TOP = 70;
    private static final int BOTTOM = 120;

    private ChartPng() {
    }

    private static Font font(int style, int size) {
        return new Font("Malgun Gothic", style, size);
    }

    private static BufferedImage canvas() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return img;
    }

    private static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void title(Graphics2D g, String title, String subtitle) {
        g.setColor(Color.BLACK);
        g.setFont(font(Font.BOLD, 20));
        g.drawString(title, LEFT, 34);
        if (subtitle != null) {
            g.setColor(Color.DARK_GRAY);
            g.setFont(font(Font.PLAIN, 13));
            g.drawString(subtitle, LEFT, 56);
        }
    }

    private static void legend(Graphics2D g, String[] names, int x, int y) {
        g.setFont(font(Font.PLAIN, 14));
        for (int i = 0; i < names.length; i++) {
            g.setColor(PALETTE[i % PALETTE.length]);
            g.fillRect(x, y + i * 22, 14, 14);
            g.setColor(Color.BLACK);
            g.drawString(names[i], x + 22, y + 12 + i * 22);
        }
    }

    private static void legendRow(Graphics2D g, String[] names, int x, int y) {
        g.setFont(font(Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (int i = 0; i < names.length; i++) {
            g.setColor(PALETTE[i % PALETTE.length]);
            g.fillRect(cx, y - 11, 13, 13);
            g.setColor(Color.BLACK);
            g.drawString(names[i], cx + 19, y);
            cx += 19 + fm.stringWidth(names[i]) + 22;
        }
    }

    private static void axisLabels(Graphics2D g, String xLabel, String yLabel) {
        g.setColor(Color.BLACK);
        g.setFont(font(Font.PLAIN, 14));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(xLabel, LEFT + (W - LEFT - RIGHT - fm.stringWidth(xLabel)) / 2, H - 14);
        java.awt.geom.AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2);
        g.drawString(yLabel, -(TOP + (H - TOP - BOTTOM + fm.stringWidth(yLabel)) / 2), 22);
        g.setTransform(old);
    }

    private static void write(BufferedImage img, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    /**
     * 꺾은선 차트. y 에 NaN 이 있으면 그 점은 그리지 않고 X 로 표시한다(측정 실패).
     * logY 이면 y 축은 상용로그(y > 0), logX 이면 x 축도 상용로그.
     */
    static void line(Path out, String title, String subtitle, String xLabel, String yLabel, double[] x, String[] names,
                     double[][] y, boolean logX, boolean logY, double yMin, double yMax, String[] xTickLabels)
            throws IOException {
        BufferedImage img = canvas();
        Graphics2D g = g2(img);
        title(g, title, subtitle);
        int pw = W - LEFT - RIGHT;
        int ph = H - TOP - BOTTOM;
        double xMin = x[0];
        double xMax = x[x.length - 1];

        // 격자와 y 눈금
        g.setFont(font(Font.PLAIN, 12));
        if (logY) {
            for (double e = Math.floor(Math.log10(yMin)); e <= Math.ceil(Math.log10(yMax)); e++) {
                double v = Math.pow(10, e);
                int py = TOP + ph - (int) (ph * (Math.log10(v) - Math.log10(yMin)) / (Math.log10(yMax) - Math.log10(yMin)));
                if (py < TOP - 1 || py > TOP + ph + 1) {
                    continue;
                }
                g.setColor(new Color(0xdddddd));
                g.drawLine(LEFT, py, LEFT + pw, py);
                g.setColor(Color.BLACK);
                g.drawString(fmt(v), LEFT - 8 - g.getFontMetrics().stringWidth(fmt(v)), py + 4);
            }
        } else {
            for (int i = 0; i <= 5; i++) {
                double v = yMin + (yMax - yMin) * i / 5;
                int py = TOP + ph - (int) (ph * i / 5.0);
                String label = yMax <= 1.0 ? String.format("%.1f", v) : fmt(v);
                g.setColor(new Color(0xdddddd));
                g.drawLine(LEFT, py, LEFT + pw, py);
                g.setColor(Color.BLACK);
                g.drawString(label, LEFT - 8 - g.getFontMetrics().stringWidth(label), py + 4);
            }
        }
        // x 눈금
        for (int i = 0; i < x.length; i++) {
            int px = xPos(x[i], xMin, xMax, pw, logX);
            g.setColor(new Color(0xeeeeee));
            g.drawLine(LEFT + px, TOP, LEFT + px, TOP + ph);
            g.setColor(Color.BLACK);
            String label = xTickLabels != null ? xTickLabels[i] : fmt(x[i]);
            g.drawString(label, LEFT + px - g.getFontMetrics().stringWidth(label) / 2, TOP + ph + 18);
        }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(LEFT, TOP, pw, ph);

        for (int s = 0; s < y.length; s++) {
            g.setColor(PALETTE[s % PALETTE.length]);
            g.setStroke(new BasicStroke(2.2f));
            int prevX = -1;
            int prevY = -1;
            for (int i = 0; i < x.length; i++) {
                int px = LEFT + xPos(x[i], xMin, xMax, pw, logX);
                if (Double.isNaN(y[s][i])) {
                    g.setStroke(new BasicStroke(2.5f));
                    int py = TOP + ph - 10;
                    g.drawLine(px - 6, py - 6, px + 6, py + 6);
                    g.drawLine(px - 6, py + 6, px + 6, py - 6);
                    g.setStroke(new BasicStroke(2.2f));
                    prevX = -1;
                    continue;
                }
                int py = TOP + ph - yPos(y[s][i], yMin, yMax, ph, logY);
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, px, py);
                }
                g.fillOval(px - 4, py - 4, 8, 8);
                prevX = px;
                prevY = py;
            }
        }
        axisLabels(g, xLabel, yLabel);
        legend(g, names, LEFT + 14, TOP + 10);
        g.dispose();
        write(img, out);
    }

    private static int xPos(double v, double min, double max, int pw, boolean log) {
        double t = log ? (Math.log10(v) - Math.log10(min)) / (Math.log10(max) - Math.log10(min)) : (v - min) / (max - min);
        return (int) (pw * Math.max(0, Math.min(1, t)));
    }

    private static int yPos(double v, double min, double max, int ph, boolean log) {
        double t = log ? (Math.log10(Math.max(v, min)) - Math.log10(min)) / (Math.log10(max) - Math.log10(min))
                : (v - min) / (max - min);
        return (int) (ph * Math.max(0, Math.min(1, t)));
    }

    private static String fmt(double v) {
        if (v == 0) {
            return "0";
        }
        if (v >= 1000 && v == Math.rint(v)) {
            return String.format("%,d", (long) v);
        }
        if (v >= 1 && v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        if (v >= 1) {
            return String.format("%.1f", v);
        }
        return v >= 0.01 ? String.format("%.2f", v) : String.format("%.0e", v);
    }

    /** 묶음 막대 차트 (값 범위 0~yMax). values[시리즈][분류] */
    static void groupedBars(Path out, String title, String subtitle, String yLabel, List<String> categories,
                            String[] names, double[][] values, double yMax, boolean percent) throws IOException {
        BufferedImage img = canvas();
        Graphics2D g = g2(img);
        title(g, title, subtitle);
        int pw = W - LEFT - RIGHT;
        int ph = H - TOP - BOTTOM;
        g.setFont(font(Font.PLAIN, 12));
        for (int i = 0; i <= 5; i++) {
            double v = yMax * i / 5;
            int py = TOP + ph - (int) (ph * i / 5.0);
            g.setColor(new Color(0xdddddd));
            g.drawLine(LEFT, py, LEFT + pw, py);
            g.setColor(Color.BLACK);
            String label = percent ? String.format("%.0f%%", v * 100) : fmt(v);
            g.drawString(label, LEFT - 8 - g.getFontMetrics().stringWidth(label), py + 4);
        }
        g.setColor(Color.BLACK);
        g.drawRect(LEFT, TOP, pw, ph);

        int groups = categories.size();
        double groupW = (double) pw / groups;
        double barW = groupW * 0.8 / names.length;
        for (int c = 0; c < groups; c++) {
            for (int s = 0; s < names.length; s++) {
                double v = values[s][c];
                int bh = (int) (ph * Math.min(1, v / yMax));
                int bx = LEFT + (int) (c * groupW + groupW * 0.1 + s * barW);
                g.setColor(PALETTE[s % PALETTE.length]);
                g.fillRect(bx, TOP + ph - bh, (int) barW - 1, bh);
                g.setColor(Color.BLACK);
                g.setFont(font(Font.PLAIN, 10));
                if (!percent || v < 0.995) {
                    String label = percent ? String.format("%.0f", v * 100) : fmt(v);
                    g.drawString(label, bx + ((int) barW - g.getFontMetrics().stringWidth(label)) / 2, TOP + ph - bh - 3);
                }
            }
            g.setColor(Color.BLACK);
            g.setFont(font(Font.PLAIN, 11));
            String[] parts = categories.get(c).split("\n");
            for (int p = 0; p < parts.length; p++) {
                int cx = LEFT + (int) (c * groupW + groupW / 2);
                g.drawString(parts[p], cx - g.getFontMetrics().stringWidth(parts[p]) / 2, TOP + ph + 18 + p * 14);
            }
        }
        axisLabels(g, "", yLabel);
        legendRow(g, names, LEFT, H - 22);
        g.dispose();
        write(img, out);
    }
}
