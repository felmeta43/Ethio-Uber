"""
Order models for Ethio-Uber delivery platform.
"""
import random
import string
from datetime import date
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils import timezone
from django.utils.translation import gettext_lazy as _


class DeliveryFeeConfig(models.Model):
    """Configurable delivery fee parameters managed by admin."""
    base_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=30.00,
        verbose_name=_('Base Fee (ETB)')
    )
    per_km_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=15.00,
        verbose_name=_('Per KM Fee (ETB)')
    )
    urgent_multiplier = models.DecimalField(
        max_digits=4, decimal_places=2, default=1.50,
        verbose_name=_('Urgent Delivery Multiplier')
    )
    small_parcel_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=0.00,
        verbose_name=_('Small Parcel Extra Fee (ETB)')
    )
    medium_parcel_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=20.00,
        verbose_name=_('Medium Parcel Extra Fee (ETB)')
    )
    large_parcel_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=50.00,
        verbose_name=_('Large Parcel Extra Fee (ETB)')
    )
    extra_large_fee = models.DecimalField(
        max_digits=10, decimal_places=2, default=100.00,
        verbose_name=_('Extra Large Parcel Extra Fee (ETB)')
    )
    platform_commission_percent = models.DecimalField(
        max_digits=5, decimal_places=2, default=20.00,
        verbose_name=_('Platform Commission (%)')
    )
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Delivery Fee Config')
        verbose_name_plural = _('Delivery Fee Configs')

    def __str__(self):
        return f"Fee Config (Base: {self.base_fee} ETB/km: {self.per_km_fee})"

    @classmethod
    def get_active(cls):
        """Get the active fee configuration."""
        config = cls.objects.filter(is_active=True).first()
        if not config:
            config = cls.objects.create()
        return config


class Order(models.Model):
    """Main delivery order model."""

    class Status(models.TextChoices):
        PENDING = 'PENDING', _('Pending')
        FINDING_DRIVER = 'FINDING_DRIVER', _('Finding Driver')
        DRIVER_ASSIGNED = 'DRIVER_ASSIGNED', _('Driver Assigned')
        DRIVER_ON_WAY = 'DRIVER_ON_WAY', _('Driver On The Way')
        ARRIVED_AT_PICKUP = 'ARRIVED_AT_PICKUP', _('Driver Arrived at Pickup')
        PICKED_UP = 'PICKED_UP', _('Parcel Picked Up')
        IN_TRANSIT = 'IN_TRANSIT', _('In Transit')
        ARRIVED_AT_DESTINATION = 'ARRIVED_AT_DESTINATION', _('Arrived at Destination')
        DELIVERED = 'DELIVERED', _('Delivered')
        CANCELLED = 'CANCELLED', _('Cancelled')
        FAILED = 'FAILED', _('Failed')

    class ParcelSize(models.TextChoices):
        SMALL = 'SMALL', _('Small (fits in a bag)')
        MEDIUM = 'MEDIUM', _('Medium (fits in a box)')
        LARGE = 'LARGE', _('Large (requires two hands)')
        EXTRA_LARGE = 'EXTRA_LARGE', _('Extra Large (bulky item)')

    class PaymentMethod(models.TextChoices):
        TELEBIRR = 'TELEBIRR', _('Telebirr')
        CBE_BIRR = 'CBE_BIRR', _('CBE Birr')
        CHAPA = 'CHAPA', _('Chapa')
        CASH = 'CASH', _('Cash on Delivery')
        WALLET = 'WALLET', _('Wallet')

    class PaymentStatus(models.TextChoices):
        PENDING = 'PENDING', _('Pending')
        PAID = 'PAID', _('Paid')
        FAILED = 'FAILED', _('Failed')
        REFUNDED = 'REFUNDED', _('Refunded')

    # Order identification
    order_number = models.CharField(
        max_length=30, unique=True, editable=False,
        verbose_name=_('Order Number')
    )

    # Parties
    customer = models.ForeignKey(
        'accounts.User',
        on_delete=models.PROTECT,
        related_name='customer_orders',
        limit_choices_to={'user_type': 'CUSTOMER'},
        verbose_name=_('Customer')
    )
    driver = models.ForeignKey(
        'accounts.User',
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='driver_orders',
        limit_choices_to={'user_type': 'DRIVER'},
        verbose_name=_('Driver')
    )
    merchant = models.ForeignKey(
        'accounts.MerchantProfile',
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='merchant_orders',
        verbose_name=_('Merchant')
    )

    # Locations
    pickup_address = models.CharField(max_length=500, verbose_name=_('Pickup Address'))
    pickup_location = gis_models.PointField(srid=4326, verbose_name=_('Pickup Location'))
    destination_address = models.CharField(max_length=500, verbose_name=_('Destination Address'))
    destination_location = gis_models.PointField(srid=4326, verbose_name=_('Destination Location'))

    # Parcel info
    description = models.TextField(blank=True, verbose_name=_('Parcel Description'))
    parcel_size = models.CharField(
        max_length=15, choices=ParcelSize.choices,
        default=ParcelSize.SMALL, verbose_name=_('Parcel Size')
    )
    is_urgent = models.BooleanField(default=False, verbose_name=_('Urgent Delivery'))

    # Status
    status = models.CharField(
        max_length=30, choices=Status.choices,
        default=Status.PENDING, verbose_name=_('Order Status')
    )

    # Delivery verification
    delivery_otp = models.CharField(max_length=6, null=True, blank=True, verbose_name=_('Delivery OTP'))
    otp_verified = models.BooleanField(default=False, verbose_name=_('OTP Verified'))

    # Pricing
    base_fee = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Base Fee (ETB)'))
    distance_fee = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Distance Fee (ETB)'))
    urgent_fee = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Urgent Fee (ETB)'))
    parcel_size_fee = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Parcel Size Fee (ETB)'))
    total_fee = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Total Fee (ETB)'))
    distance_km = models.DecimalField(max_digits=8, decimal_places=3, default=0, verbose_name=_('Distance (KM)'))
    driver_earnings = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Driver Earnings (ETB)'))
    platform_commission = models.DecimalField(max_digits=10, decimal_places=2, default=0, verbose_name=_('Platform Commission (ETB)'))

    # Payment
    payment_method = models.CharField(
        max_length=15, choices=PaymentMethod.choices,
        default=PaymentMethod.CASH, verbose_name=_('Payment Method')
    )
    payment_status = models.CharField(
        max_length=15, choices=PaymentStatus.choices,
        default=PaymentStatus.PENDING, verbose_name=_('Payment Status')
    )

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True, verbose_name=_('Created At'))
    accepted_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Accepted At'))
    picked_up_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Picked Up At'))
    delivered_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Delivered At'))
    cancelled_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Cancelled At'))

    # Ratings
    customer_rating = models.IntegerField(
        null=True, blank=True,
        choices=[(i, i) for i in range(1, 6)],
        verbose_name=_('Customer Rating (1-5)')
    )
    customer_feedback = models.TextField(blank=True, verbose_name=_('Customer Feedback'))
    driver_rating = models.IntegerField(
        null=True, blank=True,
        choices=[(i, i) for i in range(1, 6)],
        verbose_name=_('Driver Rating (1-5)')
    )

    # Rejection tracking
    rejected_driver_ids = models.JSONField(default=list, blank=True, verbose_name=_('Rejected Driver IDs'))

    class Meta:
        verbose_name = _('Order')
        verbose_name_plural = _('Orders')
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['order_number']),
            models.Index(fields=['customer', 'status']),
            models.Index(fields=['driver', 'status']),
            models.Index(fields=['status', 'created_at']),
            models.Index(fields=['payment_status']),
        ]

    def __str__(self):
        return f"Order {self.order_number} - {self.status}"

    def save(self, *args, **kwargs):
        if not self.order_number:
            self.order_number = self._generate_order_number()
        super().save(*args, **kwargs)

    @staticmethod
    def _generate_order_number():
        """Generate unique order number: EU-YYYYMMDD-XXXX"""
        today = date.today()
        date_str = today.strftime('%Y%m%d')
        suffix = ''.join(random.choices(string.digits, k=4))
        order_number = f"EU-{date_str}-{suffix}"
        # Ensure uniqueness
        while Order.objects.filter(order_number=order_number).exists():
            suffix = ''.join(random.choices(string.digits, k=4))
            order_number = f"EU-{date_str}-{suffix}"
        return order_number

    @property
    def is_active(self):
        """Check if order is in an active state."""
        return self.status not in [
            self.Status.DELIVERED, self.Status.CANCELLED, self.Status.FAILED
        ]

    def update_status(self, new_status, note='', location=None):
        """Update order status and create history entry."""
        old_status = self.status
        self.status = new_status

        # Update relevant timestamps
        now = timezone.now()
        if new_status == self.Status.DRIVER_ASSIGNED:
            self.accepted_at = now
        elif new_status == self.Status.PICKED_UP:
            self.picked_up_at = now
        elif new_status == self.Status.DELIVERED:
            self.delivered_at = now
        elif new_status in [self.Status.CANCELLED, self.Status.FAILED]:
            self.cancelled_at = now

        self.save()

        # Create history entry
        OrderStatusHistory.objects.create(
            order=self,
            status=new_status,
            note=note or f"Status changed from {old_status} to {new_status}",
            location=location,
        )

        return self


class OrderStatusHistory(models.Model):
    """Track every status change for an order."""
    order = models.ForeignKey(
        Order, on_delete=models.CASCADE,
        related_name='status_history',
        verbose_name=_('Order')
    )
    status = models.CharField(max_length=30, choices=Order.Status.choices, verbose_name=_('Status'))
    timestamp = models.DateTimeField(auto_now_add=True, verbose_name=_('Timestamp'))
    note = models.TextField(blank=True, verbose_name=_('Note'))
    location = gis_models.PointField(srid=4326, null=True, blank=True, verbose_name=_('Location at Status Change'))

    class Meta:
        verbose_name = _('Order Status History')
        verbose_name_plural = _('Order Status Histories')
        ordering = ['timestamp']

    def __str__(self):
        return f"{self.order.order_number} -> {self.status} at {self.timestamp}"
