# ChartGeneratorLambdaFunction

An AWS Lambda function designed to automate the generation of sump pump water level charts from data stored in DynamoDB and serve them via S3.

## Purpose
This project migrates the original [SumpDataVisualizer CLI tool](https://github.com/nobudev7/SumpDataVisualizerCli) to a serverless architecture. It automatically do the following:
1.  Retrieves water level data for a specific date from a DynamoDB table.
2.  Generates a professional XY line chart (PNG) using JFreeChart.
3.  Uploads the chart to an S3 bucket with organized directory structures.
4.  Updates a central `file-list.json` on S3 to ensure the frontend website can discover new images immediately.

## How it Works
-   **Trigger:** Designed to be triggered by a new data point write to DynamoDB table.
-   **Data Retrieval:** Queries the `Sump_Water_Level` DynamoDB table using a partition key (`Date` in `YYYYMMDD`) and sort key (`Time` in `HH:MM:SS`).
-   **Chart Generation:** Uses `JFreeChart` to create a 1600x900 PNG image. The image is processed entirely in memory (as a `byte[]`) to avoid Lambda filesystem limitations.
-   **Storage & Organization:**
    -   Images are stored at: `output/YYYY/MM/waterlevel-YYYYMMDD.png`
    -   Metadata is stored at: `output/file-list.json`
-   **Cache Management:** To ensure CloudFront serves fresh content without manual invalidations, the function sets a `Cache-Control: max-age=60` header on all S3 uploads.

## Local Testing
The project includes a JUnit test (`LocalTest.java`) that allows you to run the full Lambda logic from your local machine (IntelliJ or Maven).

### What the Unit Test Does:
1.  Instantiates the `ChartGeneratorHandler`.
2.  Mocks the AWS Lambda `Context` (for logging) and `ScheduledEvent`.
3.  Invokes the handler logic, which will:
    -   Connect to your **real** AWS DynamoDB table.
    -   Generate a **real** chart image.
    -   Upload both the image and the updated JSON to your **real** S3 bucket.

## Requirements
### Software
-   **Java 21**
-   **Maven 3.8+**
-   **IntelliJ IDEA** (Optional, but recommended)

### AWS Configuration
The code uses the Default Credentials Provider Chain. For local testing, you must have an **AWS Access Key ID** and **AWS Secret Access Key** with the necessary permissions.

#### 1. Automatic Configuration (Recommended)
Install the [AWS CLI](https://aws.amazon.com/cli/) and run:
```bash
aws configure
```

#### 2. Manual Configuration
You can manually create/edit the AWS configuration files in your home directory (`~/.aws/` on macOS/Linux or `%USERPROFILE%\.aws\` on Windows).

**~/.aws/credentials** (Stores your API keys):
```ini
[default]
aws_access_key_id = YOUR_ACCESS_KEY_ID
aws_secret_access_key = YOUR_SECRET_ACCESS_KEY
```

**~/.aws/config** (Stores your region preference):
```ini
[default]
region = us-east-1
output = json
```

### IAM Permissions
The credentials used (either locally or by the Lambda role) require:
-   `dynamodb:Query` on the `Sump_Water_Level` table.
-   `s3:PutObject` and `s3:GetObject` on the `sump-water-level` bucket.

## Build and Package
To generate the "fat JAR" for deployment to the AWS Lambda Console:
```bash
mvn clean package
```
The JAR will be located at: `target/ChartGeneratorLambdaFunction-1.0-SNAPSHOT.jar`.
