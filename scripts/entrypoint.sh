#!/bin/bash
set -e

echo "=== Ethio-Uber Backend Startup ==="

# Wait for PostgreSQL
echo "Waiting for PostgreSQL..."
until nc -z "${DB_HOST:-db}" "${DB_PORT:-5432}"; do
    echo "  PostgreSQL not ready yet, retrying in 2s..."
    sleep 2
done
echo "PostgreSQL is up."

# Wait for Redis
echo "Waiting for Redis..."
until nc -z redis 6379; do
    echo "  Redis not ready yet, retrying in 2s..."
    sleep 2
done
echo "Redis is up."

# Run database migrations
echo "Running migrations..."
python manage.py migrate --noinput

# Create default superuser if not exists
echo "Checking for superuser..."
python manage.py shell -c "
from apps.accounts.models import User
if not User.objects.filter(is_superuser=True).exists():
    import os
    phone = os.environ.get('DJANGO_SUPERUSER_PHONE', '+251911000000')
    password = os.environ.get('DJANGO_SUPERUSER_PASSWORD', 'admin@EthioUber2024!')
    name = os.environ.get('DJANGO_SUPERUSER_NAME', 'System Admin')
    user = User.objects.create_superuser(phone=phone, password=password, full_name=name)
    print(f'Superuser created: {phone}')
else:
    print('Superuser already exists.')
"

# Create default delivery fee config
python manage.py shell -c "
from apps.orders.models import DeliveryFeeConfig
if not DeliveryFeeConfig.objects.exists():
    DeliveryFeeConfig.objects.create()
    print('Default delivery fee config created.')
"

# Create default merchant categories
python manage.py shell -c "
from apps.merchants.models import MerchantCategory
categories = [
    ('Restaurant', 'ምግብ ቤት', 1),
    ('Pharmacy', 'ፋርማሲ', 2),
    ('Grocery', 'ምርቶች', 3),
    ('Electronics', 'ኤሌክትሮኒክስ', 4),
    ('Clothing', 'ልብስ', 5),
    ('Bakery', 'ዳቦ ቤት', 6),
    ('Other', 'ሌሎች', 99),
]
for name, name_am, order in categories:
    MerchantCategory.objects.get_or_create(name=name, defaults={'name_am': name_am, 'sort_order': order})
print('Merchant categories initialized.')
"

# Collect static files
echo "Collecting static files..."
python manage.py collectstatic --noinput --clear

echo "=== Starting server ==="
exec "$@"
