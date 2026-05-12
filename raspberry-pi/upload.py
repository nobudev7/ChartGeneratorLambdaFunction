import boto3
import argparse
from decimal import Decimal

# Parse command line arguments
parser = argparse.ArgumentParser(description='Upload data point to DynamoDB')
parser.add_argument('Date', type=str, help='Date in YYYYMMDD format')
parser.add_argument('Time', type=str, help='Time in HH:MM:SS format')
parser.add_argument('Level', type=str, help='Water level value')
args = parser.parse_args()

# Connect to DynamoDB
dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
table = dynamodb.Table('Sump_Water_Level')

# Data to upload
data_point = {
    'Date': args.Date,
    'Time': args.Time,
    'Level': Decimal(args.Level)
}

# Push to AWS
table.put_item(Item=data_point)
print(f"Data uploaded successfully: {data_point}")
