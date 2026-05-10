package com.nobudev7;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.junit.jupiter.api.Test;

/**
 * Local test to run the Lambda Handler.
 * Note: This requires AWS credentials configured in ~/.aws/credentials
 * with access to the DynamoDB table and S3 bucket.
 */
public class LocalTest {

    @Test
    public void testLambdaHandler() {
        System.out.println("Starting local Lambda test...");
        
        ChartGeneratorHandler handler = new ChartGeneratorHandler();
        ScheduledEvent event = new ScheduledEvent();
        
        // We pass null for the Context as our current implementation doesn't strictly require it 
        // (except for logging, which will just null-pointer if we don't handle it or use a mock).
        // To be safe, I've updated the handler to use System.out if context is null, 
        // but for a quick test, let's see how it behaves.
        
        try {
            String result = handler.handleRequest(event, new TestContext());
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
