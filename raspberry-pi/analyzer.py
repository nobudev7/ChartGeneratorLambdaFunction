import os
from datetime import datetime, timezone, timedelta
import config

class TrendAnalyzer:
    def __init__(self, threshold_cm=config.ALERT_THRESHOLD_CM):
        self.threshold = threshold_cm

    def _utc_to_local(self, utc_str):
        """Converts UTC ISO format (2026-06-09T02:05:06Z) to a local readable string."""
        try:
            # Handle the 'Z' suffix
            clean_utc = utc_str.replace('Z', '+00:00')
            dt_utc = datetime.fromisoformat(clean_utc)
            # Convert to local timezone
            dt_local = dt_utc.astimezone(None) 
            return dt_local.strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            return utc_str

    def get_readings_from_csv(self, csv_dir, current_date):
        """
        Reads recent readings. Only spans across midnight if the current 
        day's file doesn't have enough data (at least 12 readings).
        current_date: datetime object of the current local time
        """
        all_lines = []
        current_filename = current_date.strftime("%Y-%m-%d.csv")
        current_path = os.path.join(csv_dir, current_filename)

        # 1. Read current day first
        if os.path.exists(current_path):
            try:
                with open(current_path, 'r') as f:
                    all_lines = f.readlines()
            except Exception as e:
                print(f"Error reading current CSV: {e}")

        # 2. Only if we don't have enough data (12 readings), look at previous day
        if len(all_lines) < 12:
            previous_date = current_date - timedelta(days=1)
            previous_filename = previous_date.strftime("%Y-%m-%d.csv")
            previous_path = os.path.join(csv_dir, previous_filename)
            
            if os.path.exists(previous_path):
                try:
                    with open(previous_path, 'r') as f:
                        # Prepend the last lines from yesterday to the start of today's lines
                        yesterday_lines = f.readlines()
                        all_lines = yesterday_lines[-(12 - len(all_lines)):] + all_lines
                except Exception as e:
                    print(f"Error reading previous CSV: {e}")

        # 3. Parse the last 12 readings
        readings = []
        for line in all_lines[-12:]:
            parts = line.strip().split(',')
            if len(parts) == 2:
                readings.append({
                    'timestamp_utc': parts[0],
                    'timestamp': self._utc_to_local(parts[0]),
                    'level': float(parts[1])
                })
        
        return readings

    def analyze(self, readings):
        """
        Analyzes readings to determine if a verified alert exists.
        Logic:
        - Needs at least 8 readings (5 baseline, 1 candidate, 2 confirmation).
        - Candidate is at index -3.
        - Checks if candidate > threshold.
        - Verifies it wasn't a spike (compares to baseline and confirmations).
        """
        if len(readings) < 8:
            return None # Not enough data to verify trend yet

        candidate = readings[-3]
        baseline = readings[-8:-3]
        confirmations = readings[-2:]

        # Check threshold
        if candidate['level'] < self.threshold:
            return None

        # 1. Abnormal Spike Check: 
        # If the candidate is much higher than BOTH the baseline avg AND the confirmation avg, 
        # it's likely a sensor error.
        baseline_avg = sum(r['level'] for r in baseline) / len(baseline)
        confirm_avg = sum(r['level'] for r in confirmations) / len(confirmations)
        
        # Example: baseline 10, candidate 25, confirmation 10 -> Spike.
        # But if confirmation is also high (e.g. 24), then it's a real trend.
        if candidate['level'] > (baseline_avg + 10) and candidate['level'] > (confirm_avg + 10):
            # The "10cm jump" is a heuristic for an abnormal spike in a 1-minute window
            return None

        # 2. Pump Check:
        # If the level was high but dropped significantly in the confirmations, 
        # the pump activated. We still return the alert because it HIT the threshold.
        
        # 3. Trend Verification:
        # If the candidate is higher than the baseline average, and the confirmations 
        # stay high or continue to rise, it's a verified alert.
        if confirm_avg > (baseline_avg + 2) or confirm_avg >= self.threshold:
            return candidate

        return None
