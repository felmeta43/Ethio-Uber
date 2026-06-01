"""
Merchant models for Ethio-Uber.
Handles merchant products, categories, and merchant-specific orders.
"""
from django.db import models
from django.utils.translation import gettext_lazy as _


class MerchantCategory(models.Model):
    """Category of products sold by merchants."""
    name = models.CharField(max_length=100, unique=True, verbose_name=_('Category Name'))
    name_am = models.CharField(
        max_length=100, blank=True,
        verbose_name=_('Name (Amharic)'),
        help_text=_('Category name in Amharic')
    )
    icon = models.ImageField(
        upload_to='merchant_categories/',
        null=True, blank=True,
        verbose_name=_('Icon')
    )
    is_active = models.BooleanField(default=True)
    sort_order = models.PositiveIntegerField(default=0, verbose_name=_('Sort Order'))

    class Meta:
        verbose_name = _('Merchant Category')
        verbose_name_plural = _('Merchant Categories')
        ordering = ['sort_order', 'name']

    def __str__(self):
        return self.name


class MerchantProduct(models.Model):
    """Product listed by a merchant."""
    merchant = models.ForeignKey(
        'accounts.MerchantProfile',
        on_delete=models.CASCADE,
        related_name='products',
        verbose_name=_('Merchant')
    )
    category = models.ForeignKey(
        MerchantCategory,
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='products',
        verbose_name=_('Category')
    )
    name = models.CharField(max_length=255, verbose_name=_('Product Name'))
    name_am = models.CharField(max_length=255, blank=True, verbose_name=_('Name (Amharic)'))
    description = models.TextField(blank=True, verbose_name=_('Description'))
    price = models.DecimalField(max_digits=10, decimal_places=2, verbose_name=_('Price (ETB)'))
    image = models.ImageField(
        upload_to='merchant_products/',
        null=True, blank=True,
        verbose_name=_('Product Image')
    )
    is_available = models.BooleanField(default=True, verbose_name=_('Is Available'))
    preparation_time_minutes = models.PositiveIntegerField(
        default=0,
        verbose_name=_('Preparation Time (minutes)'),
        help_text=_('How long to prepare this item before pickup')
    )
    sort_order = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Merchant Product')
        verbose_name_plural = _('Merchant Products')
        ordering = ['sort_order', 'name']
        indexes = [
            models.Index(fields=['merchant', 'is_available']),
            models.Index(fields=['category']),
        ]

    def __str__(self):
        return f"{self.name} - {self.merchant.business_name}"


class MerchantOrder(models.Model):
    """Merchant-specific order details linked to the main delivery Order."""

    class Status(models.TextChoices):
        PENDING = 'PENDING', _('Pending Merchant Review')
        ACCEPTED = 'ACCEPTED', _('Accepted by Merchant')
        REJECTED = 'REJECTED', _('Rejected by Merchant')
        PREPARING = 'PREPARING', _('Being Prepared')
        READY = 'READY', _('Ready for Pickup')

    order = models.OneToOneField(
        'orders.Order',
        on_delete=models.CASCADE,
        related_name='merchant_order',
        verbose_name=_('Delivery Order')
    )
    merchant = models.ForeignKey(
        'accounts.MerchantProfile',
        on_delete=models.PROTECT,
        related_name='merchant_orders',
        verbose_name=_('Merchant')
    )
    status = models.CharField(
        max_length=15,
        choices=Status.choices,
        default=Status.PENDING,
        verbose_name=_('Merchant Order Status')
    )
    special_instructions = models.TextField(
        blank=True,
        verbose_name=_('Special Instructions')
    )
    estimated_ready_time = models.DateTimeField(
        null=True, blank=True,
        verbose_name=_('Estimated Ready Time')
    )
    subtotal = models.DecimalField(
        max_digits=10, decimal_places=2, default=0,
        verbose_name=_('Items Subtotal (ETB)')
    )
    rejection_reason = models.TextField(blank=True, verbose_name=_('Rejection Reason'))
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Merchant Order')
        verbose_name_plural = _('Merchant Orders')
        ordering = ['-created_at']

    def __str__(self):
        return f"Merchant Order for {self.order.order_number}"

    def calculate_subtotal(self):
        self.subtotal = sum(item.subtotal for item in self.items.all())
        self.save(update_fields=['subtotal'])
        return self.subtotal


class MerchantOrderItem(models.Model):
    """Individual item in a merchant order."""
    merchant_order = models.ForeignKey(
        MerchantOrder,
        on_delete=models.CASCADE,
        related_name='items',
        verbose_name=_('Merchant Order')
    )
    product = models.ForeignKey(
        MerchantProduct,
        on_delete=models.PROTECT,
        related_name='order_items',
        verbose_name=_('Product')
    )
    quantity = models.PositiveIntegerField(default=1, verbose_name=_('Quantity'))
    unit_price = models.DecimalField(
        max_digits=10, decimal_places=2,
        verbose_name=_('Unit Price at Time of Order (ETB)')
    )
    subtotal = models.DecimalField(
        max_digits=10, decimal_places=2,
        verbose_name=_('Subtotal (ETB)')
    )
    note = models.CharField(max_length=255, blank=True, verbose_name=_('Item Note'))

    class Meta:
        verbose_name = _('Merchant Order Item')
        verbose_name_plural = _('Merchant Order Items')

    def save(self, *args, **kwargs):
        self.subtotal = self.unit_price * self.quantity
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.quantity}x {self.product.name}"
