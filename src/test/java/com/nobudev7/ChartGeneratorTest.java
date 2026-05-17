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

    private static final String INPUT_DIR = "raspberry-pi";
    private static final String OUTPUT_DIR = "target/test-output";

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
            List<WaterLevelData> data = loadDataFromCsv(csvFile.getAbsolutePath());
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
