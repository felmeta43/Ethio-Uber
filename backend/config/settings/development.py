"""
Development settings for Ethio-Uber project.
"""

from .base import *
from dotenv import load_dotenv
import os

load_dotenv()

DEBUG = True

ALLOWED_HOSTS = ['*']

# Development: allow all origins
CORS_ALLOW_ALL_ORIGINS = True

# Email backend for development (print to console)
EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'

# Django Debug Toolbar (optional)
INSTALLED_APPS += ['django.contrib.admindocs']

# Logging
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'verbose': {
            'format': '{levelname} {asctime} {module} {process:d} {thread:d} {message}',
            'style': '{',
        },
        'simple': {
            'format': '{levelname} {message}',
            'style': '{',
        },
    },
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
            'formatter': 'verbose',
        },
    },
    'root': {
        'handlers': ['console'],
        'level': 'INFO',
    },
    'loggers': {
        'django': {
            'handlers': ['console'],
            'level': os.environ.get('DJANGO_LOG_LEVEL', 'INFO'),
            'propagate': False,
        },
        'apps': {
            'handlers': ['console'],
            'level': 'DEBUG',
            'propagate': False,
        },
    },
}

# Development media storage (local)
DEFAULT_FILE_STORAGE = 'django.core.files.storage.FileSystemStorage'

# Celery eager execution in tests (optional, set to False for async)
CELERY_TASK_ALWAYS_EAGER = os.environ.get('CELERY_TASK_ALWAYS_EAGER', 'False') == 'True'
