import os

# Biometric settings
BIOMETRIC_MIN_IMAGE_QUALITY = 40  # Minimum acceptable image quality (0-100)

# Auto-sync settings
EMR_AUTO_SYNC_ENABLED = True
EMR_AUTO_SYNC_INTERVAL_MINUTES = 5

# Ensure SECRET_KEY is set
SECRET_KEY = os.environ.get('SECRET_KEY', 'your-secret-key-here-change-in-production')
