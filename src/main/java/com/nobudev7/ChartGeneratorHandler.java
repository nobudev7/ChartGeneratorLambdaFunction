package com.nobudev7;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ChartGeneratorHandler implements RequestHandler<ScheduledEvent, String> {

    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final ChartGenerator chartGenerator;
    private final Gson gson;

    private static final String TABLE_NAME = "Sump_Water_Level";
    private static final String BUCKET_NAME = "sump-water-level";
    private static final String FILE_LIST_JSON_KEY = "output/file-list.json";

    public ChartGeneratorHandler() {
        this.dynamoDbClient = DynamoDbClient.create();
        this.s3Client = S3Client.create();
        this.chartGenerator = new ChartGenerator();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        // Set to today's date: 2026-05-10
        LocalDate targetDate = LocalDate.now();
        String dateStr = targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        context.getLogger().log("Processing data for date: " + dateStr);

        try {
            List<WaterLevelData> data = fetchDataFromDynamoDB(dateStr);
            if (data.isEmpty()) {
                context.getLogger().log("No data found for " + dateStr);
                return "No data found for " + dateStr;
            }

            String chartTitle = "Water Level on " + targetDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            byte[] chartImage = chartGenerator.generateChart(data, chartTitle);

            if (chartImage != null) {
                String s3Key = uploadToS3(chartImage, targetDate, dateStr, context);
                updateFileListJson(s3Key, targetDate, context);
                return "Successfully generated chart, uploaded to S3, and updated file-list.json for " + dateStr;
            } else {
                return "Failed to generate chart image.";
            }

        } catch (Exception e) {
            context.getLogger().log("Error during processing: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private List<WaterLevelData> fetchDataFromDynamoDB(String dateStr) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("#d = :dateValue")
                .expressionAttributeNames(Map.of("#d", "Date"))
                .expressionAttributeValues(Map.of(":dateValue", AttributeValue.builder().s(dateStr).build()))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        return response.items().stream()
                .map(item -> {
                    LocalTime time = LocalTime.parse(item.get("Time").s(), timeFormatter);
                    double level = Double.parseDouble(item.get("Level").n());
                    return new WaterLevelData(time, level);
                })
                .collect(Collectors.toList());
    }

    private String uploadToS3(byte[] content, LocalDate date, String dateStr, Context context) {
        String year = String.valueOf(date.getYear());
        String month = date.format(DateTimeFormatter.ofPattern("MM"));
        // Path: output/YYYY/MM/waterlevel-YYYYMMDD.png
        String s3Key = String.format("output/%s/%s/waterlevel-%s.png", year, month, dateStr);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .contentType("image/png")
                .cacheControl("max-age=60") // Allow caching for 60 seconds
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
        context.getLogger().log("Uploaded chart to S3 with Cache-Control: s3://" + BUCKET_NAME + "/" + s3Key);
        return s3Key;
    }

    private void updateFileListJson(String newImageKey, LocalDate date, Context context) {
        Map<String, Map<String, List<String>>> fileTree;

        // 1. Download existing file-list.json
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(FILE_LIST_JSON_KEY)
                    .build();
            
            InputStreamReader reader = new InputStreamReader(s3Client.getObject(getObjectRequest));
            Type type = new TypeToken<TreeMap<String, Map<String, List<String>>>>(){}.getType();
            fileTree = gson.fromJson(reader, type);
            if (fileTree == null) fileTree = new TreeMap<>();
            
        } catch (Exception e) {
            context.getLogger().log("Could not find or read existing file-list.json, creating new one. Error: " + e.getMessage());
            fileTree = new TreeMap<>();
        }

        // 2. Update the tree
        String year = String.valueOf(date.getYear());
        String month = date.format(DateTimeFormatter.ofPattern("MM"));
        
        Map<String, List<String>> months = fileTree.computeIfAbsent(year, k -> new HashMap<>());
        List<String> images = months.computeIfAbsent(month, k -> new ArrayList<>());
        
        if (!images.contains(newImageKey)) {
            images.add(newImageKey);
            Collections.sort(images);
        }

        // 3. Upload back to S3
        String json = gson.toJson(fileTree);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(FILE_LIST_JSON_KEY)
                .contentType("application/json")
                .cacheControl("max-age=60") // Allow caching for 60 seconds
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromString(json));
        context.getLogger().log("Updated file-list.json on S3 with Cache-Control: max-age=60");
    }
}
