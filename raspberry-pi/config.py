import os

# Hardware Configuration
TRIG_PIN = int(os.environ.get('TRIG_PIN', 3))
ECHO_PIN = int(os.environ.get('ECHO_PIN', 14))
HOLE_DEPTH = float(os.environ.get('HOLE_DEPTH', 61))

# AWS Configuration
AWS_REGION = os.environ.get('AWS_REGION', 'us-east-1')
DYNAMO_TABLE = os.environ.get('DYNAMO_TABLE', 'Sump_Water_Level')

# Alert Configuration
ENABLE_ALERTS = os.environ.get('ENABLE_ALERTS', 'false').lower() == 'true'
ALERT_THRESHOLD_CM = float(os.environ.get('ALERT_THRESHOLD_CM', 25.0))
# CSV Path for history analysis
CSV_DIR = os.path.join(os.path.dirname(__file__), 'csv')

# Email Configuration (Gmail SMTP)
# Note: Use a Gmail "App Password", not your regular password.
SMTP_SERVER = "smtp.gmail.com"
SMTP_PORT = 587
GMAIL_USER = os.environ.get('GMAIL_USER')
GMAIL_PASS = os.environ.get('GMAIL_PASS')
ALERT_RECIPIENT = os.environ.get('ALERT_RECIPIENT')
