package com.nobudev7;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.Tick;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChartGenerator {

    public byte[] generateChart(List<WaterLevelData> data, String title) throws IOException {
        if (data == null || data.isEmpty()) {
            return null;
        }

        XYSeries series = new XYSeries("Time");
        double maxWaterLevel = 0.0;
        double minWaterLevel = Double.MAX_VALUE;

        for (int i = 0; i < data.size(); i++) {
            WaterLevelData d = data.get(i);
            series.add(i, d.getWaterLevel());
            if (d.getWaterLevel() > maxWaterLevel) {
                maxWaterLevel = d.getWaterLevel();
            }
            if (d.getWaterLevel() < minWaterLevel) {
                minWaterLevel = d.getWaterLevel();
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "Time",
                "Water Level (cm)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                false,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 50));
        chart.getTitle().setPadding(new RectangleInsets(10, 10, 28, 10));

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(Color.DARK_GRAY);
        plot.getRenderer().setSeriesPaint(0, new Color(42, 103, 194));
        plot.getRenderer().setSeriesStroke(0, new BasicStroke(4.0f));
        plot.setOutlineVisible(false);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 36));
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 24));

        // Y min to be no more than 6.0
        if (minWaterLevel < 6.0) {
            rangeAxis.setLowerBound(minWaterLevel);
        } else {
            rangeAxis.setLowerBound(6.0);
        }

        // Y max to be not less than 20.0
        if (maxWaterLevel > 20.0) {
            rangeAxis.setUpperBound(50.0);
            rangeAxis.setTickUnit(new NumberTickUnit(5.0));
        } else {
            rangeAxis.setUpperBound(20.0);
            rangeAxis.setTickUnit(new NumberTickUnit(2.0));
        }

        plot.setDomainAxis(new HourlyNumberAxis(data));
        plot.getDomainAxis().setLabel("Time");
        plot.getDomainAxis().setLabelFont(new Font("SansSerif", Font.PLAIN, 36));
        plot.getDomainAxis().setTickLabelFont(new Font("SansSerif", Font.PLAIN, 24));

        int width = 1600;
        int height = 900;
        int margin = 20;

        BufferedImage image = new BufferedImage(width + margin, height + margin, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width + margin, height + margin);

        chart.draw(g2, new Rectangle2D.Double(0, margin, width, height));
        g2.dispose();

        ByteArrayOutputStream chartByteStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", chartByteStream);
        return chartByteStream.toByteArray();
    }

    public void generateChart(List<WaterLevelData> data, String title, String filePath) throws IOException {
        byte[] chartBytes = generateChart(data, title);
        if (chartBytes != null) {
            java.nio.file.Files.write(new File(filePath).toPath(), chartBytes);
        }
    }

    private static class HourlyNumberAxis extends NumberAxis {
        private final List<WaterLevelData> data;

        public HourlyNumberAxis(List<WaterLevelData> data) {
            super();
            this.data = data;
        }

        @Override
        public List<Tick> refreshTicks(Graphics2D g2, AxisState state, Rectangle2D dataArea, RectangleEdge edge) {
            List<Tick> ticks = new ArrayList<>();
            if (data == null || data.isEmpty()) {
                return ticks;
            }

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm");

            int lastHour = -1;
            for (int i = 0; i < data.size(); i++) {
                LocalTime time = data.get(i).getTime();
                int currentHour = time.getHour();
                if (currentHour % 2 == 0 && currentHour != lastHour) {
                    ticks.add(new NumberTick(i, LocalTime.of(currentHour, 0).format(timeFormatter),
                            TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
                    lastHour = currentHour;
                }
            }
            return ticks;
        }
    }
}
