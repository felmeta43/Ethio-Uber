"""
Base settings for Ethio-Uber project.
"""

import os
from pathlib import Path
from datetime import timedelta
from dotenv import load_dotenv

load_dotenv()

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent.parent

# Security
SECRET_KEY = os.environ.get('DJANGO_SECRET_KEY', 'django-insecure-change-this-in-production')

# Application definition
DJANGO_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.gis',
]

THIRD_PARTY_APPS = [
    'rest_framework',
    'rest_framework_simplejwt',
    'rest_framework_simplejwt.token_blacklist',
    'corsheaders',
    'channels',
    'django_filters',
    'drf_spectacular',
    'phonenumber_field',
    'django_extensions',
]

LOCAL_APPS = [
    'apps.accounts',
    'apps.orders',
    'apps.tracking',
    'apps.payments',
    'apps.notifications',
    'apps.merchants',
    'apps.analytics',
]

INSTALLED_APPS = DJANGO_APPS + THIRD_PARTY_APPS + LOCAL_APPS

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'whitenoise.middleware.WhiteNoiseMiddleware',
    'corsheaders.middleware.CorsMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'config.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [BASE_DIR / 'templates'],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'config.wsgi.application'
ASGI_APPLICATION = 'config.asgi.application'

# Database - PostgreSQL + PostGIS
DATABASES = {
    'default': {
        'ENGINE': 'django.contrib.gis.db.backends.postgis',
        'NAME': os.environ.get('DB_NAME', 'ethio_uber_db'),
        'USER': os.environ.get('DB_USER', 'ethio_uber_user'),
        'PASSWORD': os.environ.get('DB_PASSWORD', 'password'),
        'HOST': os.environ.get('DB_HOST', 'localhost'),
        'PORT': os.environ.get('DB_PORT', '5432'),
    }
}

# Redis
REDIS_URL = os.environ.get('REDIS_URL', 'redis://localhost:6379/0')

# Django Channels
CHANNEL_LAYERS = {
    'default': {
        'BACKEND': 'channels_redis.core.RedisChannelLayer',
        'CONFIG': {
            'hosts': [REDIS_URL],
        },
    },
}

# Cache
CACHES = {
    'default': {
        'BACKEND': 'django_redis.cache.RedisCache',
        'LOCATION': REDIS_URL,
        'OPTIONS': {
            'CLIENT_CLASS': 'django_redis.client.DefaultClient',
        }
    }
}

# Celery Configuration
CELERY_BROKER_URL = os.environ.get('CELERY_BROKER_URL', 'redis://localhost:6379/1')
CELERY_RESULT_BACKEND = os.environ.get('CELERY_RESULT_BACKEND', 'redis://localhost:6379/2')
CELERY_ACCEPT_CONTENT = ['json']
CELERY_TASK_SERIALIZER = 'json'
CELERY_RESULT_SERIALIZER = 'json'
CELERY_TIMEZONE = 'Africa/Addis_Ababa'
CELERY_BEAT_SCHEDULE = {}

# Custom Auth User
AUTH_USER_MODEL = 'accounts.User'

# Password validation
AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator'},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'},
    {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'},
]

# Internationalization
LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'Africa/Addis_Ababa'
USE_I18N = True
USE_TZ = True

# Static files
STATIC_URL = '/static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'
STATICFILES_DIRS = [BASE_DIR / 'static']
STATICFILES_STORAGE = 'whitenoise.storage.CompressedManifestStaticFilesStorage'

# Media files
MEDIA_URL = '/media/'
MEDIA_ROOT = BASE_DIR / 'media'

# Default primary key field type
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# Django REST Framework
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ],
    'DEFAULT_PERMISSION_CLASSES': [
        'rest_framework.permissions.IsAuthenticated',
    ],
    'DEFAULT_FILTER_BACKENDS': [
        'django_filters.rest_framework.DjangoFilterBackend',
        'rest_framework.filters.SearchFilter',
        'rest_framework.filters.OrderingFilter',
    ],
    'DEFAULT_PAGINATION_CLASS': 'rest_framework.pagination.PageNumberPagination',
    'PAGE_SIZE': 20,
    'DEFAULT_SCHEMA_CLASS': 'drf_spectacular.openapi.AutoSchema',
    'DEFAULT_RENDERER_CLASSES': [
        'rest_framework.renderers.JSONRenderer',
    ],
    'EXCEPTION_HANDLER': 'apps.accounts.utils.custom_exception_handler',
}

# JWT Settings
JWT_ACCESS_TOKEN_LIFETIME_HOURS = int(os.environ.get('JWT_ACCESS_TOKEN_LIFETIME_HOURS', 1))
JWT_REFRESH_TOKEN_LIFETIME_DAYS = int(os.environ.get('JWT_REFRESH_TOKEN_LIFETIME_DAYS', 7))

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=JWT_ACCESS_TOKEN_LIFETIME_HOURS),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=JWT_REFRESH_TOKEN_LIFETIME_DAYS),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
    'UPDATE_LAST_LOGIN': True,
    'ALGORITHM': 'HS256',
    'SIGNING_KEY': SECRET_KEY,
    'AUTH_HEADER_TYPES': ('Bearer',),
    'AUTH_HEADER_NAME': 'HTTP_AUTHORIZATION',
    'USER_ID_FIELD': 'id',
    'USER_ID_CLAIM': 'user_id',
    'USER_AUTHENTICATION_RULE': 'rest_framework_simplejwt.authentication.default_user_authentication_rule',
    'AUTH_TOKEN_CLASSES': ('rest_framework_simplejwt.tokens.AccessToken',),
    'TOKEN_TYPE_CLAIM': 'token_type',
    'TOKEN_USER_CLASS': 'rest_framework_simplejwt.models.TokenUser',
}

# CORS Settings
CORS_ALLOWED_ORIGINS = os.environ.get(
    'CORS_ALLOWED_ORIGINS',
    'http://localhost:3000,http://127.0.0.1:3000'
).split(',')

CORS_ALLOW_CREDENTIALS = True
CORS_ALLOW_HEADERS = [
    'accept',
    'accept-encoding',
    'authorization',
    'content-type',
    'dnt',
    'origin',
    'user-agent',
    'x-csrftoken',
    'x-requested-with',
]

# Phone number settings
PHONENUMBER_DEFAULT_REGION = 'ET'

# drf-spectacular (Swagger/OpenAPI)
SPECTACULAR_SETTINGS = {
    'TITLE': 'Ethio-Uber API',
    'DESCRIPTION': 'A delivery platform API for Ethiopia starting with Shashemene. '
                   'Supports Customer, Driver, Merchant, and Admin user types.',
    'VERSION': '1.0.0',
    'SERVE_INCLUDE_SCHEMA': False,
    'CONTACT': {'email': 'support@ethiouber.com'},
    'LICENSE': {'name': 'Proprietary'},
    'TAGS': [
        {'name': 'Authentication', 'description': 'User registration, login, OTP verification'},
        {'name': 'Accounts', 'description': 'User profiles and account management'},
        {'name': 'Orders', 'description': 'Delivery order lifecycle'},
        {'name': 'Tracking', 'description': 'Real-time GPS tracking'},
        {'name': 'Payments', 'description': 'Payment processing and wallet'},
        {'name': 'Notifications', 'description': 'Push notifications and device tokens'},
        {'name': 'Merchants', 'description': 'Merchant products and orders'},
        {'name': 'Analytics', 'description': 'Admin dashboard and analytics'},
    ],
    'COMPONENT_SPLIT_REQUEST': True,
    'SORT_OPERATIONS': False,
}

# Payment Gateway Settings
CHAPA_SECRET_KEY = os.environ.get('CHAPA_SECRET_KEY', '')
CHAPA_PUBLIC_KEY = os.environ.get('CHAPA_PUBLIC_KEY', '')
CHAPA_WEBHOOK_SECRET = os.environ.get('CHAPA_WEBHOOK_SECRET', '')
CHAPA_BASE_URL = os.environ.get('CHAPA_BASE_URL', 'https://api.chapa.co/v1')

TELEBIRR_APP_ID = os.environ.get('TELEBIRR_APP_ID', '')
TELEBIRR_APP_KEY = os.environ.get('TELEBIRR_APP_KEY', '')
TELEBIRR_MERCHANT_CODE = os.environ.get('TELEBIRR_MERCHANT_CODE', '')
TELEBIRR_BASE_URL = os.environ.get('TELEBIRR_BASE_URL', 'https://196.188.120.3:38443/apiaccess/payment/gateway')

CBE_BIRR_MERCHANT_ID = os.environ.get('CBE_BIRR_MERCHANT_ID', '')
CBE_BIRR_API_KEY = os.environ.get('CBE_BIRR_API_KEY', '')
CBE_BIRR_BASE_URL = os.environ.get('CBE_BIRR_BASE_URL', 'https://api.cbebirr.com')

# SMS Settings
SMS_API_KEY = os.environ.get('SMS_API_KEY', '')
SMS_SENDER_ID = os.environ.get('SMS_SENDER_ID', 'EthioUber')
SMS_BASE_URL = os.environ.get('SMS_BASE_URL', 'https://api.afromessage.com/api')

# FCM (Push Notifications)
FCM_SERVER_KEY = os.environ.get('FCM_SERVER_KEY', '')
FCM_BASE_URL = os.environ.get('FCM_BASE_URL', 'https://fcm.googleapis.com/fcm/send')

# App-specific settings
DEFAULT_DRIVER_SEARCH_RADIUS_KM = int(os.environ.get('DEFAULT_DRIVER_SEARCH_RADIUS_KM', 2))
MAX_DRIVER_SEARCH_RADIUS_KM = int(os.environ.get('MAX_DRIVER_SEARCH_RADIUS_KM', 10))
ORDER_NUMBER_PREFIX = os.environ.get('ORDER_NUMBER_PREFIX', 'EU')
OTP_EXPIRY_MINUTES = int(os.environ.get('OTP_EXPIRY_MINUTES', 10))
DELIVERY_OTP_LENGTH = int(os.environ.get('DELIVERY_OTP_LENGTH', 4))
PHONE_OTP_LENGTH = int(os.environ.get('PHONE_OTP_LENGTH', 6))

# Delivery fee defaults (ETB)
DEFAULT_BASE_FEE = 30.00
DEFAULT_PER_KM_FEE = 15.00
DEFAULT_URGENT_MULTIPLIER = 1.5
DEFAULT_PLATFORM_COMMISSION_PERCENT = 20.0
