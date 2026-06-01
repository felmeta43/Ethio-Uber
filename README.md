# Ethio-Uber — Ethiopian Delivery Platform

A full-stack delivery platform for Ethiopian cities (starting with Shashemene), built with Django REST Framework backend and native Android apps. Modeled after Uber but localized for Ethiopia with Telebirr, CBE Birr, and Chapa payment integrations.

---

## Table of Contents

- [System Overview](#system-overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Backend Setup](#backend-setup)
- [Android Apps Setup](#android-apps-setup)
- [API Documentation](#api-documentation)
- [Environment Variables](#environment-variables)
- [Deployment (Production)](#deployment-production)
- [Project Structure](#project-structure)
- [Feature Summary](#feature-summary)

---

## System Overview

| User Type | App | Description |
|---|---|---|
| **Customer** | Android (Customer App) | Orders deliveries, tracks in real-time, pays online |
| **Driver** | Android (Driver App) | Accepts orders, navigates, confirms delivery via OTP |
| **Merchant** | Web API / future app | Lists products, manages orders |
| **Admin** | Django Admin + API | Approves drivers/merchants, monitors all orders |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Client Layer                                │
│   Customer App (Android/Kotlin)   Driver App (Android/Kotlin)   │
└──────────────────────┬──────────────────────────────────────────┘
                       │ REST + WebSocket
┌──────────────────────▼──────────────────────────────────────────┐
│                     Nginx (Reverse Proxy)                       │
│                  HTTP → Daphne   WS → Daphne                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────────┐
│              Django Backend (Daphne / ASGI)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────────┐ │
│  │ accounts │ │  orders  │ │payments  │ │  tracking (WS)     │ │
│  │merchants │ │analytics │ │notifs    │ │  Django Channels   │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────────────┘ │
└────────┬──────────────────────┬───────────────────────────────┘
         │                      │
┌────────▼──────┐    ┌──────────▼──────┐    ┌──────────────────┐
│ PostgreSQL    │    │   Redis          │    │  Celery Worker   │
│ + PostGIS     │    │ (Cache/Channels/ │    │  (SMS, Push      │
│               │    │  Celery broker)  │    │   notifications) │
└───────────────┘    └─────────────────┘    └──────────────────┘
```

---

## Prerequisites

### Backend
- Docker 24+ and Docker Compose v2+
- OR: Python 3.11+, PostgreSQL 15 with PostGIS extension, Redis 7+

### Android Apps
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK (minSdk 26 / targetSdk 34)
- Google Maps API key
- Firebase project (for FCM push notifications)

---

## Backend Setup

### Option A — Docker (Recommended)

**1. Clone the repository**
```bash
git clone https://github.com/felmeta43/Ethio-Uber.git
cd Ethio-Uber
```

**2. Configure environment variables**
```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env` with your values (see [Environment Variables](#environment-variables)):
```bash
nano backend/.env
```

Minimum required changes:
```env
DJANGO_SECRET_KEY=your-very-long-random-secret-key-here
DB_PASSWORD=choose-a-strong-password
CHAPA_SECRET_KEY=CHASECK_TEST-your-key    # from dashboard.chapa.co
```

**3. Build and start all services**
```bash
docker compose up --build
```

This starts:
- `db` — PostgreSQL 15 with PostGIS
- `redis` — Redis 7
- `backend` — Django/Daphne on port 8000
- `celery` — Celery worker (SMS + push notifications)
- `celery-beat` — Celery beat scheduler
- `nginx` — Nginx on port 80

**4. Verify the backend is running**
```bash
curl http://localhost/api/docs/
```
Open your browser at `http://localhost/api/docs/` to see the interactive Swagger UI.

**Default admin credentials** (set in `.env`):
- Phone: `+251911000000`
- Password: `Admin@1234`
- Admin panel: `http://localhost/admin/`

---

### Option B — Manual Setup (Without Docker)

**1. Install system dependencies**
```bash
# Ubuntu / Debian
sudo apt-get update && sudo apt-get install -y \
  python3.11 python3.11-venv python3-pip \
  gdal-bin libgdal-dev libgeos-dev libproj-dev binutils \
  postgresql-15 postgresql-15-postgis-3 \
  redis-server

# macOS (Homebrew)
brew install python@3.11 gdal geos proj postgresql@15 redis
brew install postgis
```

**2. Create PostgreSQL database**
```sql
-- Run as postgres user
sudo -u postgres psql
CREATE USER ethio_uber_user WITH PASSWORD 'yourpassword';
CREATE DATABASE ethio_uber_db OWNER ethio_uber_user;
\c ethio_uber_db
CREATE EXTENSION postgis;
\q
```

**3. Create Python virtual environment**
```bash
cd backend
python3.11 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

**4. Set environment variables**
```bash
cp .env.example .env
# Edit .env with your database credentials
export DJANGO_SETTINGS_MODULE=config.settings.development
```

**5. Run migrations and setup**
```bash
python manage.py migrate
python manage.py createsuperuser  # will prompt for phone/password
```

**6. Start services**

In separate terminals:
```bash
# Terminal 1 — Django (ASGI/WebSocket)
daphne -b 0.0.0.0 -p 8000 config.asgi:application

# Terminal 2 — Celery worker
celery -A config worker -l info

# Terminal 3 — Celery beat
celery -A config beat -l info

# Terminal 4 — Redis (if not running)
redis-server
```

---

## Android Apps Setup

Both apps share the same setup process.

### 1. Get a Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable: **Maps SDK for Android**, **Geocoding API**, **Directions API**
4. Create an API Key under **Credentials**
5. Restrict it to your app's package name + SHA-1 fingerprint

### 2. Set up Firebase (for Push Notifications)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project
3. Add an Android app:
   - Customer app: package `com.ethiouber.customer`
   - Driver app: package `com.ethiouber.driver`
4. Download `google-services.json` for each app
5. Copy to the respective app directory:
   ```bash
   cp ~/Downloads/google-services.json android/customer-app/app/
   cp ~/Downloads/google-services.json android/driver-app/app/
   ```
6. In Firebase Console → Project Settings → Cloud Messaging → copy **Server Key**
7. Add to your backend `.env`: `FCM_SERVER_KEY=your-server-key`

### 3. Configure the Customer App

```bash
cd android/customer-app
```

Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="google_maps_key">YOUR_GOOGLE_MAPS_API_KEY</string>
```

Edit `app/build.gradle.kts` — update `BASE_URL` for your environment:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://YOUR_SERVER_IP/api/v1/\"")
buildConfigField("String", "WS_BASE_URL", "\"ws://YOUR_SERVER_IP/ws/\"")
```

For local development with Android Emulator:
```kotlin
// Use 10.0.2.2 to reach host machine from emulator
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/api/v1/\"")
buildConfigField("String", "WS_BASE_URL", "\"ws://10.0.2.2:8000/ws/\"")
```

For physical device on same WiFi network:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://192.168.1.x:8000/api/v1/\"")
```

### 4. Configure the Driver App

Same steps as the customer app but in `android/driver-app/`.

### 5. Open in Android Studio

```bash
# Open customer app
studio android/customer-app

# Open driver app (separate window)
studio android/driver-app
```

Or: **File → Open** → select the respective directory.

### 6. Build and Run

1. Connect an Android device (API 26+) or start an emulator
2. Click **Run ▶** or press `Shift+F10`
3. Select your device

**First run checklist:**
- [ ] Grant location permission when prompted
- [ ] Grant notification permission (Android 13+)
- [ ] Allow background location for the Driver App

---

## API Documentation

Once the backend is running, access the full interactive API docs:

| URL | Description |
|---|---|
| `http://localhost/api/docs/` | Swagger UI (try all endpoints) |
| `http://localhost/api/redoc/` | ReDoc (clean reference) |
| `http://localhost/api/schema/` | Raw OpenAPI JSON schema |

### Authentication

All authenticated endpoints require a Bearer token:
```
Authorization: Bearer <access_token>
```

Token lifecycle:
- Access token expires in **1 hour**
- Refresh token expires in **7 days**
- Refresh: `POST /api/v1/accounts/token/refresh/` with `{"refresh": "<refresh_token>"}`

### WebSocket Endpoints

| WebSocket URL | Used By | Purpose |
|---|---|---|
| `ws://host/ws/tracking/{order_id}/?token=<jwt>` | Customer | Real-time driver location during delivery |
| `ws://host/ws/driver/availability/?token=<jwt>` | Driver | Receive new order requests, send location updates |

**WebSocket message format (driver → server):**
```json
{"type": "location_update", "lat": 7.0621, "lng": 38.7468, "heading": 180.0, "speed": 35.0}
{"type": "accept_order", "order_id": 42}
{"type": "reject_order", "order_id": 42}
```

**WebSocket message format (server → customer):**
```json
{"type": "location_update", "lat": 7.0621, "lng": 38.7468, "driver_name": "Abebe", "order_status": "IN_TRANSIT"}
{"type": "order_status_update", "status": "PICKED_UP", "message": "Driver picked up your parcel"}
```

---

## Environment Variables

Full reference for `backend/.env`:

```env
# ── Django ──────────────────────────────────────────────────────
DJANGO_SECRET_KEY=              # REQUIRED: long random string (50+ chars)
DJANGO_SETTINGS_MODULE=config.settings.development
DEBUG=True
ALLOWED_HOSTS=localhost,127.0.0.1,your-domain.com

# ── Database ─────────────────────────────────────────────────────
DB_NAME=ethio_uber_db
DB_USER=ethio_uber_user
DB_PASSWORD=                    # REQUIRED
DB_HOST=db                      # 'db' for Docker, 'localhost' for manual
DB_PORT=5432

# ── Redis ────────────────────────────────────────────────────────
REDIS_URL=redis://redis:6379/0  # 'redis' for Docker, 'localhost' for manual
CELERY_BROKER_URL=redis://redis:6379/1
CELERY_RESULT_BACKEND=redis://redis:6379/2

# ── JWT ──────────────────────────────────────────────────────────
JWT_ACCESS_TOKEN_LIFETIME_HOURS=1
JWT_REFRESH_TOKEN_LIFETIME_DAYS=7

# ── Payment Gateways ─────────────────────────────────────────────
# Chapa (https://dashboard.chapa.co)
CHAPA_SECRET_KEY=CHASECK_TEST-xxxx
CHAPA_PUBLIC_KEY=CHAPUBK_TEST-xxxx
CHAPA_WEBHOOK_SECRET=
CHAPA_BASE_URL=https://api.chapa.co/v1

# Telebirr (from Ethio Telecom developer portal)
TELEBIRR_APP_ID=
TELEBIRR_APP_KEY=
TELEBIRR_MERCHANT_CODE=
TELEBIRR_BASE_URL=https://196.188.120.3:38443/apiaccess/payment/gateway

# CBE Birr (from Commercial Bank of Ethiopia)
CBE_BIRR_MERCHANT_ID=
CBE_BIRR_API_KEY=
CBE_BIRR_BASE_URL=https://api.cbebirr.com

# ── SMS (Afro Message) ───────────────────────────────────────────
# Register at: https://afromessage.com
SMS_API_KEY=
SMS_SENDER_ID=EthioUber
SMS_BASE_URL=https://api.afromessage.com/api

# ── Firebase Cloud Messaging ─────────────────────────────────────
# From Firebase Console → Project Settings → Cloud Messaging
FCM_SERVER_KEY=
FCM_BASE_URL=https://fcm.googleapis.com/fcm/send

# ── Default Admin ────────────────────────────────────────────────
DJANGO_SUPERUSER_PHONE=+251911000000
DJANGO_SUPERUSER_PASSWORD=Admin@1234
DJANGO_SUPERUSER_NAME=System Admin

# ── Platform Settings ────────────────────────────────────────────
DEFAULT_DRIVER_SEARCH_RADIUS_KM=2   # Expands to 5km, then 10km automatically
OTP_EXPIRY_MINUTES=10
DELIVERY_OTP_LENGTH=4
PHONE_OTP_LENGTH=6

# ── CORS ─────────────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

---

## Deployment (Production)

### 1. Server requirements
- Ubuntu 22.04 LTS (recommended)
- 2 vCPU, 4 GB RAM minimum (8 GB recommended)
- 20 GB SSD
- Open ports: 80, 443

### 2. Update `.env` for production
```env
DJANGO_SETTINGS_MODULE=config.settings.production
DEBUG=False
ALLOWED_HOSTS=yourdomain.com,www.yourdomain.com
DJANGO_SECRET_KEY=<generate with: python -c "import secrets; print(secrets.token_urlsafe(50))">
DB_HOST=db
DB_PASSWORD=<strong random password>
```

### 3. Add SSL with Let's Encrypt
```bash
# Install Certbot
sudo apt-get install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d yourdomain.com

# Update nginx.conf to listen on 443 and redirect 80→443
```

Update `nginx/nginx.conf` to use the certificates from `/etc/letsencrypt/live/yourdomain.com/`.

### 4. Start production stack
```bash
docker compose -f docker-compose.yml up -d --build
```

### 5. Update Android apps for production
Change `BASE_URL` in both apps' `build.gradle.kts`:
```kotlin
buildConfigField("String", "BASE_URL", "\"https://yourdomain.com/api/v1/\"")
buildConfigField("String", "WS_BASE_URL", "\"wss://yourdomain.com/ws/\"")
```

Then build release APKs:
- **Build → Generate Signed Bundle/APK** → APK → create/select keystore → Release

---

## Project Structure

```
Ethio-Uber/
├── backend/                          # Django REST API
│   ├── apps/
│   │   ├── accounts/                 # Users, OTP, profiles (Customer/Driver/Merchant/Admin)
│   │   ├── orders/                   # Order lifecycle, fee calculation, driver matching
│   │   ├── tracking/                 # WebSocket consumers, real-time GPS
│   │   ├── payments/                 # Telebirr, CBE Birr, Chapa, wallet, withdrawals
│   │   ├── notifications/            # FCM push, SMS (AfroMessage), in-app notifs
│   │   ├── merchants/                # Product catalog, merchant order management
│   │   └── analytics/               # Admin KPIs, heatmap, revenue reports
│   ├── config/
│   │   ├── settings/                 # base, development, production
│   │   ├── asgi.py                   # ASGI entry point (HTTP + WebSocket)
│   │   ├── celery.py                 # Celery config + beat schedule
│   │   └── urls.py                   # Root URL routing
│   ├── requirements.txt
│   └── .env.example
├── android/
│   ├── customer-app/                 # Customer Android app (Kotlin/MVVM/Hilt)
│   └── driver-app/                   # Driver Android app (Kotlin/MVVM/Hilt)
├── nginx/
│   └── nginx.conf                    # Nginx reverse proxy config
├── scripts/
│   └── entrypoint.sh                 # Docker entrypoint (migrations, seeds, static)
├── docker-compose.yml
├── Dockerfile
└── README.md
```

---

## Feature Summary

### Customer App
| Feature | Status |
|---|---|
| Phone registration + OTP verification | ✅ |
| Login / logout | ✅ |
| GPS auto-detect location | ✅ |
| Select pickup & destination on map | ✅ |
| Delivery fee estimation | ✅ |
| Parcel size + urgency options | ✅ |
| Create delivery order | ✅ |
| Real-time driver tracking (WebSocket) | ✅ |
| Delivery OTP display | ✅ |
| Order history | ✅ |
| Rate driver (1-5 stars) | ✅ |
| Telebirr / CBE Birr / Chapa payment | ✅ |
| Wallet top-up and payment | ✅ |
| Saved addresses | ✅ |
| Push notifications (FCM) | ✅ |
| Cancel order | ✅ |

### Driver App
| Feature | Status |
|---|---|
| Registration with document upload (ID, license, vehicle) | ✅ |
| OTP verification | ✅ |
| Online / offline toggle | ✅ |
| Receive new order requests in real-time (WebSocket) | ✅ |
| Accept / reject order with 30s countdown | ✅ |
| GPS navigation to pickup | ✅ |
| Order status updates (step by step) | ✅ |
| OTP delivery verification (fraud prevention) | ✅ |
| Background location tracking (ForegroundService) | ✅ |
| Daily / weekly earnings dashboard | ✅ |
| Wallet balance | ✅ |
| Bank withdrawal request | ✅ |
| Rate customer | ✅ |
| Push notifications (FCM) | ✅ |

### Backend
| Feature | Status |
|---|---|
| PostGIS nearest-driver search (2→5→10km expanding) | ✅ |
| Driver auto-assignment + re-assignment on rejection | ✅ |
| Real-time WebSocket tracking (Django Channels + Redis) | ✅ |
| Chapa payment + webhook verification | ✅ |
| Telebirr H5Pay integration | ✅ |
| CBE Birr integration | ✅ |
| Wallet credit/debit with idempotency | ✅ |
| Driver earnings auto-credit after OTP delivery | ✅ |
| 20% platform commission calculation | ✅ |
| SMS via AfroMessage (OTP, notifications) | ✅ |
| FCM push via Celery (non-blocking) | ✅ |
| Merchant product catalog & order flow | ✅ |
| Admin driver/merchant approval API | ✅ |
| Analytics: KPIs, revenue, heatmap, live orders | ✅ |
| Swagger/ReDoc API documentation | ✅ |
| Docker Compose full stack | ✅ |

---

## Payment Gateway Registration

| Gateway | Register At | Notes |
|---|---|---|
| **Chapa** | [dashboard.chapa.co](https://dashboard.chapa.co) | Most documented Ethiopian gateway, sandbox available |
| **Telebirr** | Contact Ethio Telecom | Requires business registration |
| **CBE Birr** | Contact CBE | Requires business account |
| **AfroMessage (SMS)** | [afromessage.com](https://afromessage.com) | Ethiopian SMS, affordable rates |

---

## Troubleshooting

### PostGIS extension not found
```bash
# Inside the db container
docker compose exec db psql -U ethio_uber_user -d ethio_uber_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### GDAL library not found (manual setup)
```bash
# Ubuntu
sudo apt-get install gdal-bin libgdal-dev
# Then in your venv:
pip install GDAL==$(gdal-config --version)
```

### WebSocket connection refused
- Ensure Daphne is running (not just `runserver`)
- Check `config/asgi.py` has WebSocket routing
- Ensure Redis is running: `redis-cli ping` → should return `PONG`

### Android emulator can't reach backend
- Use `10.0.2.2` instead of `localhost` or `127.0.0.1`
- Ensure backend is bound to `0.0.0.0`, not just `127.0.0.1`

### Push notifications not working
- Verify `FCM_SERVER_KEY` is set in `.env`
- Ensure `google-services.json` is in `app/` directory
- Check device FCM token is registered: `POST /api/v1/notifications/device/`

---

## License

Proprietary — All rights reserved. Built for Ethiopia 🇪🇹.
