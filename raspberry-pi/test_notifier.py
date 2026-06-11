import os
import sys
from notifier import AlertNotifier
from datetime import datetime
import config

def test_email():
    print("--- AlertNotifier Standalone Test ---")
    
    # 1. Check Configuration
    user = config.GMAIL_USER
    password = config.GMAIL_PASS
    recipient = config.ALERT_RECIPIENT

    if not all([user, password, recipient]):
        print("Error: Missing configuration! Ensure GMAIL_USER, GMAIL_PASS, and ALERT_RECIPIENT are set.")
        print("Tip: If you are running this manually, use: export GMAIL_USER='...' etc.")
        return

    print(f"Using Account: {user}")
    print(f"To Recipient:  {recipient}")

    # 2. Initialize Notifier
    notifier = AlertNotifier(user, password, recipient)

    # 3. Create Dummy Data
    alert_level = 25.5
    alert_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    # Simulate a rising trend for the context table
    context = [
        {'timestamp': '2026-06-09 10:00:00', 'level': 10.2},
        {'timestamp': '2026-06-09 10:01:00', 'level': 12.5},
        {'timestamp': '2026-06-09 10:02:00', 'level': 18.0},
        {'timestamp': alert_time,           'level': alert_level},
        {'timestamp': '2026-06-09 10:04:00', 'level': 26.1},
        {'timestamp': '2026-06-09 10:05:00', 'level': 25.8},
    ]

    # 4. Attempt to send
    print("\nAttempting to send test email...")
    success = notifier.send_alert(alert_level, alert_time, context)

    if success:
        print("\nSUCCESS! Check your inbox.")
    else:
        print("\nFAILED. Check the error messages above.")
        print("\nCommon fixes:")
        print("1. Ensure you are using a Gmail 'App Password', NOT your main password.")
        print("2. Check if 'Less Secure Apps' or 2FA settings are blocking the connection.")
        print("3. Verify the SMTP port (587) is not blocked by your firewall/ISP.")

if __name__ == "__main__":
    test_email()
