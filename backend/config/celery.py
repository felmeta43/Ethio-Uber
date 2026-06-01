"""
Celery configuration for Ethio-Uber.
"""

import os
from celery import Celery
from celery.schedules import crontab

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings.development')

app = Celery('ethio_uber')
app.config_from_object('django.conf:settings', namespace='CELERY')
app.autodiscover_tasks()

# Periodic task schedule
app.conf.beat_schedule = {
    # Clean up old read notifications daily at 3am Addis Ababa time
    'cleanup-old-notifications': {
        'task': 'notifications.cleanup_old_notifications',
        'schedule': crontab(hour=3, minute=0),
        'kwargs': {'days': 90},
    },
}


@app.task(bind=True, ignore_result=True)
def debug_task(self):
    print(f'Request: {self.request!r}')
