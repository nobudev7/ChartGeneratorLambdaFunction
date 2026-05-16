package com.nobudev7;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

/**
 * Local test to run the Lambda Handler.
 * Note: This requires AWS credentials configured in ~/.aws/credentials
 * with access to the DynamoDB table and S3 bucket.
 */
public class LocalTest {

    @Test
    public void testLambdaHandler_Default() {
        System.out.println("Starting local Lambda test (Default - Today)...");
        runHandler(null);
    }

    @Test
    public void testLambdaHandler_Yesterday() {
        System.out.println("Starting local Lambda test (Yesterday)...");
        Map<String, Object> input = new HashMap<>();
        input.put("target", "yesterday");
        runHandler(input);
    }

    @Test
    public void testLambdaHandler_SpecificDate() {
        System.out.println("Starting local Lambda test (Specific Date: 2026-05-10)...");
        Map<String, Object> input = new HashMap<>();
        input.put("date", "2026-05-10");
        runHandler(input);
    }

    private void runHandler(Map<String, Object> input) {
        ChartGeneratorHandler handler = new ChartGeneratorHandler();
        try {
            String result = handler.handleRequest(input, new TestContext());
            System.out.println("Result: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Simple mock context for local logging
    private static class TestContext implements com.amazonaws.services.lambda.runtime.Context {
        public String getAwsRequestId() { return "local-test-id"; }
        public String getLogGroupName() { return "local-log-group"; }
        public String getLogStreamName() { return "local-log-stream"; }
        public String getFunctionName() { return "ChartGeneratorFunction"; }
        public String getFunctionVersion() { return "1"; }
        public String getInvokedFunctionArn() { return "arn:aws:lambda:local:123:function:ChartGeneratorFunction"; }
        public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
        public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
        public int getRemainingTimeInMillis() { return 30000; }
        public int getMemoryLimitInMB() { return 512; }
        public com.amazonaws.services.lambda.runtime.LambdaLogger getLogger() {
            return new com.amazonaws.services.lambda.runtime.LambdaLogger() {
                public void log(String message) { System.out.println("[LAMBDA] " + message); }
                public void log(byte[] message) { System.out.println("[LAMBDA] " + new String(message)); }
            };
        }
    }
}
