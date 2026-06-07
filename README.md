# ChartGeneratorLambdaFunction

An AWS Lambda function designed to automate the generation of sump pump water level charts from data stored in DynamoDB and serve them via S3.

## Purpose
This project migrates the original [SumpDataVisualizer CLI tool](https://github.com/nobudev7/SumpDataVisualizerCli) to a serverless architecture. It automatically do the following:
1.  Retrieves water level data for a specific date from a DynamoDB table.
2.  Generates a professional XY line chart (PNG) using JFreeChart.
3.  Uploads the chart to an S3 bucket with organized directory structures.
4.  Updates a central `file-list.json` on S3 to ensure the frontend website can discover new images immediately.

## How it Works
-   **Trigger:** Can be triggered manually, on a schedule (Amazon EventBridge), or via other AWS services.
-   **Date Selection:** By default, it generates a chart for the current day. It can also be configured to generate a chart for "yesterday" or any specific date (see [Input Parameters](#input-parameters)).
-   **Data Retrieval:** Queries the `Sump_Water_Level` DynamoDB table using a partition key (`Date` in `YYYYMMDD`) and sort key (`Time` in `HH:MM:SS`).
-   **Chart Generation:** Uses `JFreeChart` to create a 1600x900 PNG image. The image is processed entirely in memory (as a `byte[]`) to avoid Lambda filesystem limitations.
-   **Storage & Organization:**
    -   Images are stored at: `output/YYYY/MM/waterlevel-YYYYMMDD.png`
    -   Metadata is stored at: `output/file-list.json`
-   **Cache Management:** To ensure CloudFront serves fresh content without manual invalidations, the function sets a `Cache-Control: max-age=60` header on all S3 uploads.

## Input Parameters
The Lambda function accepts a JSON payload to control which date the chart is generated for:

| Parameter | Type | Value Example | Description |
| :--- | :--- | :--- | :--- |
| `target` | String | `"yesterday"` | Generates a chart for the previous day. |
| `date` | String | `"2026-05-10"` | Generates a chart for the specified `YYYY-MM-DD` date. |

**Example (Yesterday):**
```json
{ "target": "yesterday" }
```

**Example (Specific Date):**
```json
{ "date": "2026-05-15" }
```

If no parameters are provided, it defaults to the current day.

## AWS Lambda Setup & Scheduling

### Deployment
1.  **Build the JAR:** Run `mvn clean package`.
2.  **Create Lambda:** In the AWS Console, create a new Java 21 Lambda function.
3.  **Upload JAR:** Upload the `target/ChartGeneratorLambdaFunction-1.0-SNAPSHOT.jar`.
4.  **Handler Configuration:** Set the handler to `com.nobudev7.ChartGeneratorHandler::handleRequest`.
5.  **Runtime Settings:** Optional - Set 512MB of memory (JFreeChart can be memory intensive).
6.  **IAM Role:** Assign a role with the permissions described in the [IAM Permissions](#iam-permissions) section.

### Scheduling Yesterday's Report
To generate a chart for "yesterday" shortly after midnight every day:
1.  Open **Amazon EventBridge** -> **Schedules**.
2.  Click **Create schedule**.
3.  **Schedule pattern:** `cron(5 0 * * ? *)` (Runs every day at 00:05 UTC).
4.  **Target:** Select your **Lambda function**.
5.  **Payload (Constant JSON):**
    ```json
    { "target": "yesterday" }
    ```
6.  Finish the wizard to activate the schedule.

## Local Testing
The project includes a JUnit test (`LocalTest.java`) that allows you to run the full Lambda logic from your local machine.

### What the Unit Test Does:
1.  Instantiates the `ChartGeneratorHandler`.
2.  Mocks the AWS Lambda `Context` (for logging).
3.  Invokes the handler with different scenarios:
    -   **Default:** Generates today's chart.
    -   **Yesterday:** Generates yesterday's chart.
    -   **Specific Date:** Generates a chart for a provided date.
4.  The tests connect to your **real** AWS resources.

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

## Supporting Scripts (Raspberry Pi)
The `raspberry-pi/` directory contains Python scripts designed to run on a Raspberry Pi (or similar device) to feed data into the system.

### IAM Permissions (for Python Scripts)
To allow the scripts to communicate with AWS, the IAM user or role running them must have the following policy. This grants the specific permissions required for both real-time and historical data ingestion:

- **`dynamodb:PutItem`**: Required for `upload.py` to insert individual water level readings every minute.
- **`dynamodb:UpdateItem`**: Allows for granular updates to existing data points if necessary.
- **`dynamodb:BatchWriteItem`**: Required for `batch_upload.py` to efficiently upload multiple historical records in a single request.

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "dynamodb:PutItem",
                "dynamodb:UpdateItem",
                "dynamodb:BatchWriteItem"
            ],
            "Resource": "arn:aws:dynamodb:<REGION>:<YOUR_ACCOUNT_ID>:table/<TABLE_NAME>"
        }
    ]
}
```

### [upload.py](https://github.com/nobudev7/ChartGeneratorLambdaFunction/blob/main/raspberry-pi/upload.py)
Used for real-time data uploads. It accepts three parameters: `Date`, `Time`, and `Level`.
**Recommended Cron Job (every minute):**
```cronexp
* * * * * /usr/bin/python3 /path/to/raspberry-pi/upload.py $(date +\%Y\%m\%d) $(date +\%H:\%M:\%S) $(/path/to/get_water_level.sh)
```

### [batch_upload.py](https://github.com/nobudev7/ChartGeneratorLambdaFunction/blob/main/raspberry-pi/batch_upload.py)
Used for uploading a full day's worth of data from a CSV file. It extracts the date from the filename (e.g., `waterlevel-20260510.csv`), parses the data, and batch writes 20 points at a time with a 1-second delay between batches to respect rate limits.
**Recommended Cron Job (daily after midnight):**
```cronexp
5 0 * * * /usr/bin/python3 /path/to/raspberry-pi/batch_upload.py /path/to/data/waterlevel-$(date -d "yesterday" +\%Y\%m\%d).csv
```
