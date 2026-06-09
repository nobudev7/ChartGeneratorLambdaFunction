import sys
import os
import boto3
from datetime import datetime, timezone
from decimal import Decimal
from pinsource import usonic

def main():
    """Measure water level using a HCSR04 sensor and upload to DynamoDB with locale time partitioning."""

    trig_pin = 3
    echo_pin = 14
    hole_depth = 61  # centimeters

    try:
        value = usonic.Measurement(trig_pin, echo_pin)
        raw_measurement = value.raw_distance()
    except SystemError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    water_level = value.depth(raw_measurement, hole_depth)
    if water_level <= 0:
        water_level = 0.0

    # Strategy 1: Local Date for partitioning, UTC Timestamp for ordering
    now_local = datetime.now()
    now_utc = datetime.now(timezone.utc)
    
    local_date_str = now_local.strftime("%Y%m%d")
    utc_timestamp_str = now_utc.strftime("%Y-%m-%dT%H:%M:%SZ")

    # Connect to DynamoDB
    dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
    table = dynamodb.Table('Sump_Water_Level')

    # Data to upload
    data_point = {
        'Date': local_date_str,       # Partition Key: Local Date
        'Timestamp': utc_timestamp_str, # Sort Key: UTC Full Timestamp
        'Level': Decimal(str(round(water_level, 1)))
    }

    # Push to AWS
    try:
        table.put_item(Item=data_point)
    except Exception as e:
        print(f"Failed to upload data: {e}", file=sys.stderr)
        sys.exit(1)

    # Log to CSV
    csv_dir = os.path.join(os.path.dirname(__file__), 'csv')
    os.makedirs(csv_dir, exist_ok=True)
    
    # Use local date for filename to match the "one day" grouping
    csv_filename = now_local.strftime("%Y-%m-%d.csv")
    csv_path = os.path.join(csv_dir, csv_filename)
    
    with open(csv_path, 'a') as f:
        f.write(f"{utc_timestamp_str},{round(water_level, 1)}\n")

if __name__ == "__main__":
    main()
