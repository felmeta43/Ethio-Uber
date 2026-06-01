"""
Payment models for Ethio-Uber.
Handles wallets, transactions, payments, and withdrawal requests.
"""
from django.db import models
from django.utils.translation import gettext_lazy as _
from django.utils import timezone


class Wallet(models.Model):
    """User wallet for storing ETB balance."""
    user = models.OneToOneField(
        'accounts.User',
        on_delete=models.CASCADE,
        related_name='wallet',
        verbose_name=_('User')
    )
    balance = models.DecimalField(
        max_digits=12,
        decimal_places=2,
        default=0.00,
        verbose_name=_('Balance (ETB)')
    )
    currency = models.CharField(max_length=5, default='ETB', verbose_name=_('Currency'))
    is_active = models.BooleanField(default=True, verbose_name=_('Is Active'))
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Wallet')
        verbose_name_plural = _('Wallets')

    def __str__(self):
        return f"Wallet: {self.user.full_name} - {self.balance} ETB"

    def credit(self, amount, purpose, reference='', order=None):
        """Add funds to wallet and create transaction record."""
        from decimal import Decimal
        amount = Decimal(str(amount))
        self.balance += amount
        self.save(update_fields=['balance', 'updated_at'])

        WalletTransaction.objects.create(
            wallet=self,
            transaction_type=WalletTransaction.TransactionType.CREDIT,
            amount=amount,
            purpose=purpose,
            reference=reference,
            order=order,
            balance_after=self.balance,
        )
        return self.balance

    def debit(self, amount, purpose, reference='', order=None):
        """Remove funds from wallet. Raises ValueError if insufficient balance."""
        from decimal import Decimal
        amount = Decimal(str(amount))
        if self.balance < amount:
            raise ValueError(f"Insufficient wallet balance. Available: {self.balance} ETB")

        self.balance -= amount
        self.save(update_fields=['balance', 'updated_at'])

        WalletTransaction.objects.create(
            wallet=self,
            transaction_type=WalletTransaction.TransactionType.DEBIT,
            amount=amount,
            purpose=purpose,
            reference=reference,
            order=order,
            balance_after=self.balance,
        )
        return self.balance


class WalletTransaction(models.Model):
    """Record of every wallet credit or debit."""

    class TransactionType(models.TextChoices):
        CREDIT = 'CREDIT', _('Credit')
        DEBIT = 'DEBIT', _('Debit')

    class Purpose(models.TextChoices):
        ORDER_PAYMENT = 'ORDER_PAYMENT', _('Order Payment')
        DRIVER_EARNINGS = 'DRIVER_EARNINGS', _("Driver's Earnings")
        WITHDRAWAL = 'WITHDRAWAL', _('Withdrawal')
        REFUND = 'REFUND', _('Refund')
        COMMISSION = 'COMMISSION', _('Platform Commission')
        TOP_UP = 'TOP_UP', _('Wallet Top-Up')

    wallet = models.ForeignKey(
        Wallet,
        on_delete=models.CASCADE,
        related_name='transactions',
        verbose_name=_('Wallet')
    )
    transaction_type = models.CharField(
        max_length=10,
        choices=TransactionType.choices,
        verbose_name=_('Transaction Type')
    )
    amount = models.DecimalField(max_digits=12, decimal_places=2, verbose_name=_('Amount (ETB)'))
    purpose = models.CharField(
        max_length=20,
        choices=Purpose.choices,
        verbose_name=_('Purpose')
    )
    reference = models.CharField(
        max_length=255, blank=True,
        verbose_name=_('Payment Reference'),
        help_text=_('Reference from payment gateway')
    )
    order = models.ForeignKey(
        'orders.Order',
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='wallet_transactions',
        verbose_name=_('Order')
    )
    timestamp = models.DateTimeField(auto_now_add=True, verbose_name=_('Timestamp'))
    balance_after = models.DecimalField(
        max_digits=12, decimal_places=2,
        verbose_name=_('Balance After Transaction')
    )
    note = models.TextField(blank=True, verbose_name=_('Note'))

    class Meta:
        verbose_name = _('Wallet Transaction')
        verbose_name_plural = _('Wallet Transactions')
        ordering = ['-timestamp']
        indexes = [
            models.Index(fields=['wallet', 'timestamp']),
            models.Index(fields=['purpose']),
        ]

    def __str__(self):
        return f"{self.transaction_type} {self.amount} ETB ({self.purpose})"


class Payment(models.Model):
    """Record of payment for an order via external gateway or cash."""

    class Status(models.TextChoices):
        PENDING = 'PENDING', _('Pending')
        PROCESSING = 'PROCESSING', _('Processing')
        COMPLETED = 'COMPLETED', _('Completed')
        FAILED = 'FAILED', _('Failed')
        REFUNDED = 'REFUNDED', _('Refunded')

    order = models.ForeignKey(
        'orders.Order',
        on_delete=models.PROTECT,
        related_name='payments',
        verbose_name=_('Order')
    )
    amount = models.DecimalField(max_digits=10, decimal_places=2, verbose_name=_('Amount (ETB)'))
    method = models.CharField(
        max_length=15,
        choices=[
            ('TELEBIRR', 'Telebirr'),
            ('CBE_BIRR', 'CBE Birr'),
            ('CHAPA', 'Chapa'),
            ('CASH', 'Cash'),
            ('WALLET', 'Wallet'),
        ],
        verbose_name=_('Payment Method')
    )
    status = models.CharField(
        max_length=15,
        choices=Status.choices,
        default=Status.PENDING,
        verbose_name=_('Payment Status')
    )
    gateway_reference = models.CharField(
        max_length=255, blank=True,
        verbose_name=_('Gateway Reference'),
        help_text=_('Transaction reference from payment gateway (e.g. Chapa tx_ref)')
    )
    gateway_response = models.JSONField(
        default=dict, blank=True,
        verbose_name=_('Gateway Response'),
        help_text=_('Full response from payment gateway')
    )
    checkout_url = models.URLField(
        blank=True,
        verbose_name=_('Checkout URL'),
        help_text=_('Payment page URL for redirect-based gateways')
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = _('Payment')
        verbose_name_plural = _('Payments')
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['gateway_reference']),
            models.Index(fields=['order', 'status']),
        ]

    def __str__(self):
        return f"Payment {self.gateway_reference or self.id} - {self.status}"


class WithdrawalRequest(models.Model):
    """Driver requests to withdraw their earnings to a bank account."""

    class Status(models.TextChoices):
        PENDING = 'PENDING', _('Pending')
        APPROVED = 'APPROVED', _('Approved')
        REJECTED = 'REJECTED', _('Rejected')
        PROCESSED = 'PROCESSED', _('Processed')

    driver = models.ForeignKey(
        'accounts.User',
        on_delete=models.PROTECT,
        related_name='withdrawal_requests',
        limit_choices_to={'user_type': 'DRIVER'},
        verbose_name=_('Driver')
    )
    amount = models.DecimalField(max_digits=12, decimal_places=2, verbose_name=_('Amount (ETB)'))
    bank_name = models.CharField(max_length=100, verbose_name=_('Bank Name'))
    account_number = models.CharField(max_length=50, verbose_name=_('Account Number'))
    account_name = models.CharField(max_length=255, verbose_name=_('Account Holder Name'))
    status = models.CharField(
        max_length=15,
        choices=Status.choices,
        default=Status.PENDING,
        verbose_name=_('Status')
    )
    processed_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Processed At'))
    admin_note = models.TextField(blank=True, verbose_name=_('Admin Note'))
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = _('Withdrawal Request')
        verbose_name_plural = _('Withdrawal Requests')
        ordering = ['-created_at']

    def __str__(self):
        return f"Withdrawal {self.amount} ETB by {self.driver.full_name} ({self.status})"
