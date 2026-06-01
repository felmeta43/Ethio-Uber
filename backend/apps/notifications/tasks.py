"""
Celery tasks for sending OTP SMS and push notifications asynchronously.
"""
import logging
from celery import shared_task

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=3, default_retry_delay=60, name='notifications.send_otp_sms')
def send_otp_sms_task(self, phone: str, otp: str, purpose: str):
    """Send OTP via SMS asynchronously. Retries up to 3 times on failure."""
    try:
        from .services import send_sms
        purpose_label = purpose.lower().replace('_', ' ')
        message = f"Your Ethio-Uber {purpose_label} code is: {otp}. Valid for 10 minutes. Do not share."
        result = send_sms(phone, message)
        if not result:
            raise Exception(f"SMS delivery failed to {phone}")
        logger.info(f"OTP SMS sent to {phone} for purpose: {purpose}")
        return True
    except Exception as exc:
        logger.error(f"OTP SMS task failed for {phone}: {exc}")
        raise self.retry(exc=exc)


@shared_task(bind=True, max_retries=2, default_retry_delay=30, name='notifications.send_push')
def send_push_notification_task(self, user_id: int, title: str, body: str, data: dict = None):
    """Send FCM push notification to a user asynchronously."""
    try:
        from apps.accounts.models import User
        user = User.objects.get(id=user_id)
        from .services import send_push_to_user
        send_push_to_user(user, title, body, data or {})
        return True
    except Exception as exc:
        logger.error(f"Push notification task failed for user {user_id}: {exc}")
        raise self.retry(exc=exc)


@shared_task(name='notifications.cleanup_old_notifications')
def cleanup_old_notifications_task(days: int = 90):
    """Periodic task: delete notifications older than N days."""
    from django.utils import timezone
    from datetime import timedelta
    from .models import Notification
    cutoff = timezone.now() - timedelta(days=days)
    deleted_count, _ = Notification.objects.filter(created_at__lt=cutoff, is_read=True).delete()
    logger.info(f"Cleaned up {deleted_count} old notifications")
    return deleted_count
