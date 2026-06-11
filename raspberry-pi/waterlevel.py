import sys
import os
import boto3
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from pinsource import usonic

import config
from notifier import AlertNotifier
from analyzer import TrendAnalyzer

STATE_FILE = os.path.join(os.path.dirname(__file__), 'alert_state.txt')

def should_send_alert():
    """Returns True if no alert has been sent recently, or if more than 1 hour has passed."""
    if not os.path.exists(STATE_FILE):
        return True
    
    try:
        with open(STATE_FILE, 'r') as f:
            last_alert_str = f.read().strip()
            if not last_alert_str:
                return True
            last_alert_time = datetime.fromisoformat(last_alert_str)
            if datetime.now() - last_alert_time >= timedelta(hours=1):
                return True
    except Exception:
        return True # Default to sending if state file is corrupted
    return False

def update_alert_state():
    """Records the time of the sent alert."""
    with open(STATE_FILE, 'w') as f:
        f.write(datetime.now().isoformat())

def clear_alert_state():
    """Removes the alert state when water level is back to normal."""
    if os.path.exists(STATE_FILE):
        os.remove(STATE_FILE)

def main():
    """Measure water level using a HCSR04 sensor and upload to DynamoDB with locale time partitioning."""

    # Hardware Configuration from config
    trig_pin = config.TRIG_PIN
    echo_pin = config.ECHO_PIN
    hole_depth = config.HOLE_DEPTH

    # AWS Configuration from config
    region = config.AWS_REGION
    table_name = config.DYNAMO_TABLE

    try:
        value = usonic.Measurement(trig_pin, echo_pin)
        raw_measurement = value.raw_distance()
    except SystemError as e:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] Error: {e}", file=sys.stderr)
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
    dynamodb = boto3.resource('dynamodb', region_name=region)
    table = dynamodb.Table(table_name)

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
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] Failed to upload data: {e}", file=sys.stderr)
        sys.exit(1)

    # Log to CSV
    csv_dir = config.CSV_DIR
    os.makedirs(csv_dir, exist_ok=True)
    
    # Use local date for filename to match the "one day" grouping
    csv_filename = now_local.strftime("%Y-%m-%d.csv")
    csv_path = os.path.join(csv_dir, csv_filename)
    
    with open(csv_path, 'a') as f:
        f.write(f"{utc_timestamp_str},{round(water_level, 1)}\n")

    # Alerting and Trend Analysis Logic
    analyzer = TrendAnalyzer()
    notifier = AlertNotifier(config.GMAIL_USER, config.GMAIL_PASS, config.ALERT_RECIPIENT)
    
    readings = analyzer.get_readings_from_csv(config.CSV_DIR, now_local)
    verified_alert = analyzer.analyze(readings)
    
    if verified_alert:
        if should_send_alert():
            if config.ENABLE_ALERTS and notifier.send_alert(
                alert_level=verified_alert['level'],
                alert_timestamp=verified_alert['timestamp'],
                context_readings=readings
            ):
                update_alert_state()
    else:
        # If the water level is safely below the threshold, reset the alerting state
        # so that the next time it rises, an immediate alert is triggered.
        if water_level < config.ALERT_THRESHOLD_CM:
            clear_alert_state()

if __name__ == "__main__":
    main()
