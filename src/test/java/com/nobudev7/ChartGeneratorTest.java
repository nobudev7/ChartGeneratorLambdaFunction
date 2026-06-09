package com.nobudev7;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChartGeneratorTest {

    private static final String INPUT_DIR = "raspberry-pi";
    private static final String OUTPUT_DIR = "target/test-output";
    private static final ZoneId ZONE_ID = ZoneId.of("America/New_York");

    @Test
    public void testGenerateChartsFromAllCsvs() throws IOException {
        // 1. Ensure output directory exists
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // 2. Find all CSV files in the input directory
        File inputDir = new File(INPUT_DIR);
        File[] csvFiles = inputDir.listFiles((dir, name) -> name.endsWith(".csv"));

        assertNotNull(csvFiles, "CSV files array should not be null");
        assertTrue(csvFiles.length > 0, "There should be at least one CSV file in " + INPUT_DIR);

        ChartGenerator generator = new ChartGenerator();

        for (File csvFile : csvFiles) {
            String fileName = csvFile.getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String outputFilePath = OUTPUT_DIR + "/" + baseName + ".png";

            System.out.println("Processing: " + fileName + " -> " + outputFilePath);

            // 3. Load data
            List<WaterLevelData> data = loadDataFromCsv(csvFile.getAbsolutePath(), fileName);
            assertNotNull(data, "Data for " + fileName + " should not be null");
            assertFalse(data.isEmpty(), "Data for " + fileName + " should not be empty");

            // 4. Generate chart
            String datePart = baseName.replace("waterlevel-", ""); // YYYYMMDD
            String formattedDate = datePart.substring(0, 4) + "/" + datePart.substring(4, 6) + "/" + datePart.substring(6, 8);
            generator.generateChart(data, "Water Level - " + formattedDate, outputFilePath);

            // 5. Verify output
            File outputFile = new File(outputFilePath);
            assertTrue(outputFile.exists(), "Output chart file should exist for " + fileName);
            assertTrue(outputFile.length() > 0, "Output chart file should not be empty for " + fileName);
        }
    }

    @Test
    public void testGenerateChart_DSTFallback() throws IOException {
        // Test case for 2026-11-01 (DST Ends - 25 hour day)
        LocalDate dstEndDate = LocalDate.of(2026, 11, 1);
        List<WaterLevelData> data = new ArrayList<>();
        
        // Add points every 15 mins for 25 hours
        ZonedDateTime current = dstEndDate.atStartOfDay(ZONE_ID);
        ZonedDateTime nextDay = current.plusDays(1);
        
        double level = 10.0;
        while (current.isBefore(nextDay)) {
            data.add(new WaterLevelData(current, level));
            current = current.plusMinutes(15);
            level += 0.1;
        }

        ChartGenerator generator = new ChartGenerator();
        String outputPath = OUTPUT_DIR + "/test-dst-fallback.png";
        generator.generateChart(data, "Water Level - DST Fallback 2026", outputPath);

        File outputFile = new File(outputPath);
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
    }

    private List<WaterLevelData> loadDataFromCsv(String filePath, String fileName) throws IOException {
        List<WaterLevelData> dataList = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        // Extract date from filename (waterlevel-YYYYMMDD.csv)
        String datePart = fileName.replace("waterlevel-", "").replace(".csv", "");
        LocalDate fileDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    LocalTime time = LocalTime.parse(parts[0], timeFormatter);
                    // Treat as local time on the file date
                    ZonedDateTime zdt = time.atDate(fileDate).atZone(ZONE_ID);
                    double waterLevel = Double.parseDouble(parts[1]);
                    dataList.add(new WaterLevelData(zdt, waterLevel));
                }
            }
        }
        return dataList;
    }
}
