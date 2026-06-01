"""
Business logic services for the orders app.
"""
import random
import string
import logging
from decimal import Decimal
from django.contrib.gis.geos import Point
from django.contrib.gis.db.models.functions import Distance

logger = logging.getLogger(__name__)


def find_nearest_driver(pickup_point, exclude_driver_ids=None):
    """
    Find the nearest available approved online driver within expanding radius.
    Searches 2km -> 5km -> 10km radius incrementally.

    Args:
        pickup_point: GEOSGeometry Point object (srid=4326)
        exclude_driver_ids: List of driver user IDs to exclude (already rejected)

    Returns:
        DriverProfile instance or None
    """
    from apps.accounts.models import DriverProfile

    if exclude_driver_ids is None:
        exclude_driver_ids = []

    for radius_km in [2, 5, 10]:
        drivers = DriverProfile.objects.filter(
            is_online=True,
            is_approved=True,
            user__is_active=True,
            current_location__isnull=False,
        ).exclude(
            user_id__in=exclude_driver_ids
        ).annotate(
            distance=Distance('current_location', pickup_point)
        ).filter(
            distance__lte=radius_km * 1000  # meters
        ).order_by('distance')

        if drivers.exists():
            logger.info(f"Found driver within {radius_km}km radius")
            return drivers.first()

    logger.warning("No available drivers found within 10km radius")
    return None


def calculate_delivery_fee(pickup_point, destination_point, parcel_size, is_urgent=False):
    """
    Calculate delivery fee based on distance, parcel size, and urgency.

    Args:
        pickup_point: GEOSGeometry Point
        destination_point: GEOSGeometry Point
        parcel_size: str (SMALL, MEDIUM, LARGE, EXTRA_LARGE)
        is_urgent: bool

    Returns:
        dict with fee breakdown
    """
    from apps.orders.models import DeliveryFeeConfig
    from django.contrib.gis.measure import D
    from django.contrib.gis.geos import GEOSGeometry

    config = DeliveryFeeConfig.get_active()

    # Calculate distance using PostGIS (in meters)
    from django.db import connection
    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT ST_Distance(ST_GeographyFromText(%s), ST_GeographyFromText(%s))",
            [
                f"POINT({pickup_point.x} {pickup_point.y})",
                f"POINT({destination_point.x} {destination_point.y})"
            ]
        )
        distance_meters = cursor.fetchone()[0]

    distance_km = Decimal(str(round(distance_meters / 1000, 3)))

    # Base fee
    base_fee = config.base_fee

    # Distance fee
    distance_fee = distance_km * config.per_km_fee

    # Parcel size fee
    parcel_size_fees = {
        'SMALL': config.small_parcel_fee,
        'MEDIUM': config.medium_parcel_fee,
        'LARGE': config.large_parcel_fee,
        'EXTRA_LARGE': config.extra_large_fee,
    }
    parcel_fee = parcel_size_fees.get(parcel_size, Decimal('0'))

    # Subtotal before urgent
    subtotal = base_fee + distance_fee + parcel_fee

    # Urgent fee
    urgent_fee = Decimal('0')
    if is_urgent:
        urgent_fee = subtotal * (config.urgent_multiplier - Decimal('1'))
        total_fee = subtotal * config.urgent_multiplier
    else:
        total_fee = subtotal

    # Round to 2 decimal places
    total_fee = total_fee.quantize(Decimal('0.01'))
    urgent_fee = urgent_fee.quantize(Decimal('0.01'))

    # Platform commission and driver earnings
    commission_rate = config.platform_commission_percent / Decimal('100')
    platform_commission = (total_fee * commission_rate).quantize(Decimal('0.01'))
    driver_earnings = (total_fee - platform_commission).quantize(Decimal('0.01'))

    return {
        'base_fee': base_fee,
        'distance_fee': distance_fee.quantize(Decimal('0.01')),
        'parcel_size_fee': parcel_fee,
        'urgent_fee': urgent_fee,
        'total_fee': total_fee,
        'distance_km': distance_km,
        'platform_commission': platform_commission,
        'driver_earnings': driver_earnings,
    }


def generate_delivery_otp(length=4):
    """Generate a random numeric OTP for delivery verification."""
    return ''.join(random.choices(string.digits, k=length))


def assign_driver_to_order(order, notify=True):
    """
    Find and assign the nearest available driver to an order.
    Updates order status to DRIVER_ASSIGNED.

    Args:
        order: Order instance
        notify: bool - whether to send push notification to driver

    Returns:
        DriverProfile if assigned, None if no driver found
    """
    from apps.orders.models import Order

    driver_profile = find_nearest_driver(
        order.pickup_location,
        exclude_driver_ids=order.rejected_driver_ids
    )

    if driver_profile:
        order.driver = driver_profile.user
        order.update_status(
            Order.Status.DRIVER_ASSIGNED,
            note=f"Driver {driver_profile.user.full_name} assigned"
        )

        if notify:
            try:
                from apps.notifications.services import notify_new_order_to_driver
                notify_new_order_to_driver(order, driver_profile.user)
            except Exception as e:
                logger.error(f"Failed to notify driver of new order: {e}")

        return driver_profile

    # No driver found - mark as pending with note
    order.update_status(
        Order.Status.FINDING_DRIVER,
        note="Searching for available drivers..."
    )
    return None


def cancel_order(order, cancelled_by, reason=''):
    """
    Cancel an order and handle refunds if payment was made.

    Args:
        order: Order instance
        cancelled_by: User instance
        reason: str
    """
    from apps.orders.models import Order

    if order.status in [Order.Status.DELIVERED, Order.Status.CANCELLED, Order.Status.FAILED]:
        raise ValueError("Cannot cancel an order that is already completed/cancelled.")

    order.update_status(
        Order.Status.CANCELLED,
        note=f"Cancelled by {cancelled_by.full_name}: {reason}"
    )

    # Handle refund if payment was made
    if order.payment_status == Order.PaymentStatus.PAID:
        try:
            from apps.payments.services import process_refund
            process_refund(order)
        except Exception as e:
            logger.error(f"Failed to process refund for order {order.order_number}: {e}")

    # Notify relevant parties
    try:
        from apps.notifications.services import notify_order_cancelled
        notify_order_cancelled(order, reason=reason)
    except Exception as e:
        logger.error(f"Failed to send cancellation notification: {e}")
