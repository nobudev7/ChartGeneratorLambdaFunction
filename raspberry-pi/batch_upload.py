import boto3
import argparse
import time
import os
import re

def main():
    parser = argparse.ArgumentParser(description='Upload CSV data to DynamoDB')
    parser.add_argument('file_path', type=str, help='Path to the YYYY-MM-DD.csv file')
    args = parser.parse_args()

    file_path = args.file_path
    filename = os.path.basename(file_path)
    
    # Extract date from YYYY-MM-DD.csv
    match = re.search(r'(\d{4})-(\d{2})-(\d{2})\.csv', filename)
    if not match:
        print(f"Error: Filename '{filename}' does not match pattern YYYY-MM-DD.csv")
        return
    
    date_str = f"{match.group(1)}{match.group(2)}{match.group(3)}" # YYYYMMDD
    
    # Connect to DynamoDB
    dynamodb = boto3.client('dynamodb', region_name='us-east-1')
    table_name = 'Sump_Water_Level'

    # Read and parse data
    items = []
    try:
        with open(file_path, 'r') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                
                parts = line.split(',')
                if len(parts) != 2:
                    continue
                
                timestamp_str = parts[0]
                level_str = parts[1]
                
                # Prepare DynamoDB PutRequest item
                items.append({
                    'PutRequest': {
                        'Item': {
                            'Date': {'S': date_str},
                            'Timestamp': {'S': timestamp_str},
                            'Level': {'N': str(level_str)}
                        }
                    }
                })
    except FileNotFoundError:
        print(f"Error: File not found at {file_path}")
        return

    print(f"Starting upload for date {date_str} ({len(items)} items total)...")

    # Batch write 20 items at a time
    batch_size = 20
    for i in range(0, len(items), batch_size):
        batch = items[i:i + batch_size]
        
        request_items = {
            table_name: batch
        }
        
        # Execute batch write
        try:
            response = dynamodb.batch_write_item(RequestItems=request_items)
            # Handle unprocessed items if any (simplified)
            if response.get('UnprocessedItems') and response['UnprocessedItems']:
                print(f"Warning: Some items were not processed in batch {i//batch_size + 1}")
            print(f"Uploaded batch {i//batch_size + 1} (up to {len(batch)} items)")
        except Exception as e:
            print(f"Error uploading batch: {e}")
            break
        
        # Wait 1 second between batches
        if i + batch_size < len(items):
            time.sleep(1)

    print("Upload complete.")

if __name__ == "__main__":
    main()
