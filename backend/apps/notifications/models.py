"""
Notification models for Ethio-Uber.
Handles push notification device tokens and notification history.
"""
from django.db import models
from django.utils.translation import gettext_lazy as _


class DeviceToken(models.Model):
    """FCM device token for push notifications."""

    class DeviceType(models.TextChoices):
        ANDROID = 'ANDROID', _('Android')
        IOS = 'IOS', _('iOS')

    user = models.ForeignKey(
        'accounts.User',
        on_delete=models.CASCADE,
        related_name='device_tokens',
        verbose_name=_('User')
    )
    token = models.CharField(max_length=512, verbose_name=_('FCM Token'))
    device_type = models.CharField(
        max_length=10,
        choices=DeviceType.choices,
        default=DeviceType.ANDROID,
        verbose_name=_('Device Type')
    )
    device_id = models.CharField(
        max_length=255, blank=True,
        verbose_name=_('Device ID'),
        help_text=_('Unique device identifier to avoid duplicate tokens')
    )
    is_active = models.BooleanField(default=True, verbose_name=_('Is Active'))
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Device Token')
        verbose_name_plural = _('Device Tokens')
        unique_together = [('user', 'device_id')]
        indexes = [
            models.Index(fields=['user', 'is_active']),
            models.Index(fields=['token']),
        ]

    def __str__(self):
        return f"{self.user.full_name} - {self.device_type} token"


class Notification(models.Model):
    """In-app notification record for users."""

    class NotificationType(models.TextChoices):
        ORDER_PLACED = 'ORDER_PLACED', _('Order Placed')
        ORDER_ACCEPTED = 'ORDER_ACCEPTED', _('Order Accepted')
        DRIVER_ARRIVED = 'DRIVER_ARRIVED', _('Driver Arrived')
        ORDER_PICKED_UP = 'ORDER_PICKED_UP', _('Order Picked Up')
        ORDER_DELIVERED = 'ORDER_DELIVERED', _('Order Delivered')
        ORDER_CANCELLED = 'ORDER_CANCELLED', _('Order Cancelled')
        PAYMENT_RECEIVED = 'PAYMENT_RECEIVED', _('Payment Received')
        PAYMENT_FAILED = 'PAYMENT_FAILED', _('Payment Failed')
        NEW_ORDER = 'NEW_ORDER', _('New Order Request')
        DRIVER_APPROVED = 'DRIVER_APPROVED', _('Driver Account Approved')
        MERCHANT_APPROVED = 'MERCHANT_APPROVED', _('Merchant Account Approved')
        WITHDRAWAL_PROCESSED = 'WITHDRAWAL_PROCESSED', _('Withdrawal Processed')
        OTP = 'OTP', _('OTP Code')
        GENERAL = 'GENERAL', _('General')

    user = models.ForeignKey(
        'accounts.User',
        on_delete=models.CASCADE,
        related_name='notifications',
        verbose_name=_('User')
    )
    title = models.CharField(max_length=255, verbose_name=_('Title'))
    body = models.TextField(verbose_name=_('Body'))
    notification_type = models.CharField(
        max_length=25,
        choices=NotificationType.choices,
        default=NotificationType.GENERAL,
        verbose_name=_('Notification Type')
    )
    data = models.JSONField(
        default=dict,
        blank=True,
        verbose_name=_('Extra Data'),
        help_text=_('Additional data payload (e.g., order_id, driver_id)')
    )
    is_read = models.BooleanField(default=False, verbose_name=_('Is Read'))
    order = models.ForeignKey(
        'orders.Order',
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='notifications',
        verbose_name=_('Related Order')
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name=_('Created At'))

    class Meta:
        verbose_name = _('Notification')
        verbose_name_plural = _('Notifications')
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', 'is_read']),
            models.Index(fields=['user', 'notification_type']),
            models.Index(fields=['created_at']),
        ]

    def __str__(self):
        return f"{self.notification_type} for {self.user.full_name}"

    def mark_read(self):
        self.is_read = True
        self.save(update_fields=['is_read'])
