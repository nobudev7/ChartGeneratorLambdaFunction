package com.nobudev7;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChartGeneratorTest {

    private static final String CSV_FILE_PATH = "raspberry-pi/waterlevel-20260316.csv";
    private static final String OUTPUT_DIR = "target/test-output";
    private static final String OUTPUT_FILE_PATH = OUTPUT_DIR + "/chart-20260316.png";

    @Test
    public void testGenerateChartFromCsv() throws IOException {
        // 1. Load data from CSV
        List<WaterLevelData> data = loadDataFromCsv(CSV_FILE_PATH);
        assertNotNull(data, "Data should not be null");
        assertFalse(data.isEmpty(), "Data should not be empty");

        // 2. Ensure output directory exists
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // 3. Generate chart
        ChartGenerator generator = new ChartGenerator();
        generator.generateChart(data, "Water Level - 2026-05-10", OUTPUT_FILE_PATH);

        // 4. Verify output file exists
        File outputFile = new File(OUTPUT_FILE_PATH);
        assertTrue(outputFile.exists(), "Output chart file should exist");
        assertTrue(outputFile.length() > 0, "Output chart file should not be empty");
        
        System.out.println("Chart generated successfully at: " + outputFile.getAbsolutePath());
    }

    private List<WaterLevelData> loadDataFromCsv(String filePath) throws IOException {
        List<WaterLevelData> dataList = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    LocalTime time = LocalTime.parse(parts[0], timeFormatter);
                    double waterLevel = Double.parseDouble(parts[1]);
                    dataList.add(new WaterLevelData(time, waterLevel));
                }
            }
        }
        return dataList;
    }
}
