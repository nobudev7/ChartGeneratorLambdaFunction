import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import sys
from datetime import datetime

class AlertNotifier:
    def __init__(self, smtp_user, smtp_pass, recipient):
        self.smtp_user = smtp_user
        self.smtp_pass = smtp_pass
        self.recipient = recipient

    def send_alert(self, alert_level, alert_timestamp):
        """
        Sends an alert email.
        """
        if not self.smtp_user or not self.smtp_pass or not self.recipient:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Notifier Error: Email credentials or recipient not configured.", file=sys.stderr)
            return False

        subject = f"ALERT: High Water Level Detected - {alert_level} cm"
        
        body = (
            f"High water level alert triggered.\n\n"
            f"Summary:\n"
            f"  - Alert Level: {alert_level} cm\n"
            f"  - Event Time: {alert_timestamp}\n\n"
            f"Please check the sump pump immediately."
        )

        msg = MIMEMultipart()
        msg['From'] = self.smtp_user
        msg['To'] = self.recipient
        msg['Subject'] = subject
        msg.attach(MIMEText(body, 'plain'))

        try:
            with smtplib.SMTP("smtp.gmail.com", 587) as server:
                server.starttls()
                server.login(self.smtp_user, self.smtp_pass)
                server.send_message(msg)
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Alert email sent to {self.recipient}")
            return True
        except Exception as e:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Failed to send alert email: {e}", file=sys.stderr)
            return False
