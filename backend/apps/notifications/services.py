"""
Notification service layer for Ethio-Uber.
Handles FCM push notifications, in-app notifications, and SMS.
"""
import logging
import requests
from django.conf import settings
from django.db import transaction

logger = logging.getLogger(__name__)


def create_notification(user, title, body, notification_type, data=None, order=None):
    """Save an in-app notification record and dispatch push asynchronously via Celery."""
    from .models import Notification
    notif = Notification.objects.create(
        user=user,
        title=title,
        body=body,
        notification_type=notification_type,
        data=data or {},
        order=order,
    )
    # Dispatch push notification via Celery so it doesn't block the request
    try:
        from .tasks import send_push_notification_task
        send_push_notification_task.delay(user.id, title, body, data or {})
    except Exception:
        # Fallback to synchronous if Celery isn't available (e.g., during tests)
        send_push_to_user(user, title, body, data or {})
    return notif


def send_push_to_user(user, title, body, data=None):
    """Send FCM push notification to all active devices of a user."""
    from .models import DeviceToken
    tokens = DeviceToken.objects.filter(user=user, is_active=True).values_list('token', flat=True)
    if not tokens:
        return

    fcm_key = getattr(settings, 'FCM_SERVER_KEY', '')
    if not fcm_key:
        logger.warning("FCM_SERVER_KEY not configured, skipping push notification.")
        return

    payload = {
        'registration_ids': list(tokens),
        'notification': {
            'title': title,
            'body': body,
            'sound': 'default',
        },
        'data': data or {},
        'priority': 'high',
    }

    try:
        response = requests.post(
            settings.FCM_BASE_URL,
            json=payload,
            headers={
                'Authorization': f'key={fcm_key}',
                'Content-Type': 'application/json',
            },
            timeout=10,
        )
        response_data = response.json()

        # Deactivate invalid tokens
        if 'results' in response_data:
            token_list = list(tokens)
            for i, result in enumerate(response_data['results']):
                if result.get('error') in ('InvalidRegistration', 'NotRegistered'):
                    if i < len(token_list):
                        DeviceToken.objects.filter(token=token_list[i]).update(is_active=False)
    except Exception as e:
        logger.error(f"FCM push notification error: {e}")


def send_sms(phone, message):
    """Send SMS via AfroMessage (Ethiopian SMS gateway)."""
    api_key = getattr(settings, 'SMS_API_KEY', '')
    if not api_key:
        logger.warning(f"SMS_API_KEY not configured. Would send SMS to {phone}: {message}")
        return False

    try:
        response = requests.post(
            f"{settings.SMS_BASE_URL}/send",
            json={
                'from': settings.SMS_SENDER_ID,
                'to': str(phone),
                'message': message,
            },
            headers={'Authorization': f'Bearer {api_key}'},
            timeout=10,
        )
        return response.status_code == 200
    except Exception as e:
        logger.error(f"SMS send error to {phone}: {e}")
        return False


def send_otp_sms(phone, otp, purpose='verification'):
    """Send OTP SMS to a phone number."""
    message = f"Your Ethio-Uber {purpose} code is: {otp}. Valid for 10 minutes. Do not share."
    return send_sms(phone, message)


# ─── Order lifecycle notifications ───────────────────────────────────────────

def notify_order_placed(order):
    create_notification(
        user=order.customer,
        title='Order Placed',
        body=f'Your order #{order.order_number} has been placed. Finding a driver...',
        notification_type='ORDER_PLACED',
        data={'order_id': order.id, 'order_number': order.order_number},
        order=order,
    )


def notify_order_accepted(order):
    if not order.driver:
        return
    create_notification(
        user=order.customer,
        title='Driver Assigned!',
        body=f'{order.driver.full_name} has accepted your order and is on the way.',
        notification_type='ORDER_ACCEPTED',
        data={
            'order_id': order.id,
            'order_number': order.order_number,
            'driver_name': order.driver.full_name,
            'driver_phone': str(order.driver.phone),
        },
        order=order,
    )


def notify_driver_arrived(order):
    create_notification(
        user=order.customer,
        title='Driver Arrived',
        body=f'Your driver {order.driver.full_name} has arrived at the pickup location.',
        notification_type='DRIVER_ARRIVED',
        data={'order_id': order.id, 'order_number': order.order_number},
        order=order,
    )


def notify_order_picked_up(order):
    create_notification(
        user=order.customer,
        title='Parcel Picked Up',
        body=f'Your parcel has been picked up and is now in transit.',
        notification_type='ORDER_PICKED_UP',
        data={'order_id': order.id, 'order_number': order.order_number},
        order=order,
    )


def notify_order_delivered(order):
    create_notification(
        user=order.customer,
        title='Order Delivered!',
        body=f'Your order #{order.order_number} has been delivered. Please rate your driver.',
        notification_type='ORDER_DELIVERED',
        data={'order_id': order.id, 'order_number': order.order_number},
        order=order,
    )


def notify_order_cancelled(order, reason=''):
    create_notification(
        user=order.customer,
        title='Order Cancelled',
        body=f'Your order #{order.order_number} has been cancelled. {reason}'.strip(),
        notification_type='ORDER_CANCELLED',
        data={'order_id': order.id, 'order_number': order.order_number, 'reason': reason},
        order=order,
    )


def notify_new_order_to_driver(order, driver):
    """Notify a driver about a new delivery request."""
    create_notification(
        user=driver,
        title='New Delivery Request',
        body=f'New order #{order.order_number} available near you. Tap to accept!',
        notification_type='NEW_ORDER',
        data={
            'order_id': order.id,
            'order_number': order.order_number,
            'pickup_address': order.pickup_address,
            'destination_address': order.destination_address,
            'total_fee': str(order.total_fee),
        },
        order=order,
    )


def notify_driver_approved(driver_user):
    create_notification(
        user=driver_user,
        title='Account Approved!',
        body='Congratulations! Your driver account has been approved. You can now start accepting orders.',
        notification_type='DRIVER_APPROVED',
        data={},
    )
    send_sms(
        driver_user.phone,
        "Ethio-Uber: Your driver account has been approved! Open the app to start accepting orders."
    )


def notify_merchant_approved(merchant_user):
    create_notification(
        user=merchant_user,
        title='Merchant Account Approved!',
        body='Your merchant account has been approved. You can now add products and receive orders.',
        notification_type='MERCHANT_APPROVED',
        data={},
    )


def notify_payment_received(driver, order):
    create_notification(
        user=driver,
        title='Payment Received',
        body=f'Payment for order #{order.order_number} has been credited to your wallet.',
        notification_type='PAYMENT_RECEIVED',
        data={'order_id': order.id, 'order_number': order.order_number, 'amount': str(order.total_fee)},
        order=order,
    )


def notify_withdrawal_processed(driver, withdrawal):
    create_notification(
        user=driver,
        title='Withdrawal Processed',
        body=f'Your withdrawal of {withdrawal.amount} ETB has been processed.',
        notification_type='WITHDRAWAL_PROCESSED',
        data={'withdrawal_id': withdrawal.id, 'amount': str(withdrawal.amount)},
    )
