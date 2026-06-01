"""
Account models for Ethio-Uber.
Handles User, OTP, CustomerProfile, DriverProfile, MerchantProfile.
"""
import random
import string
from django.contrib.auth.models import AbstractBaseUser, PermissionsMixin, BaseUserManager
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils import timezone
from django.utils.translation import gettext_lazy as _
from phonenumber_field.modelfields import PhoneNumberField
from datetime import timedelta


class UserManager(BaseUserManager):
    """Custom manager for User with phone as username field."""

    def create_user(self, phone, password=None, **extra_fields):
        if not phone:
            raise ValueError(_('Phone number is required'))
        extra_fields.setdefault('is_active', True)
        user = self.model(phone=phone, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password=None, **extra_fields):
        extra_fields.setdefault('is_staff', True)
        extra_fields.setdefault('is_superuser', True)
        extra_fields.setdefault('is_active', True)
        extra_fields.setdefault('user_type', User.UserType.ADMIN)
        extra_fields.setdefault('is_verified', True)

        if extra_fields.get('is_staff') is not True:
            raise ValueError(_('Superuser must have is_staff=True.'))
        if extra_fields.get('is_superuser') is not True:
            raise ValueError(_('Superuser must have is_superuser=True.'))

        return self.create_user(phone, password, **extra_fields)


class User(AbstractBaseUser, PermissionsMixin):
    """
    Custom user model using phone number as the unique identifier.
    Supports four user types: Customer, Driver, Merchant, Admin.
    """

    class UserType(models.TextChoices):
        CUSTOMER = 'CUSTOMER', _('Customer')
        DRIVER = 'DRIVER', _('Driver')
        MERCHANT = 'MERCHANT', _('Merchant')
        ADMIN = 'ADMIN', _('Admin')

    phone = PhoneNumberField(unique=True, verbose_name=_('Phone Number'))
    email = models.EmailField(blank=True, verbose_name=_('Email Address'))
    full_name = models.CharField(max_length=255, verbose_name=_('Full Name'))
    user_type = models.CharField(
        max_length=10,
        choices=UserType.choices,
        default=UserType.CUSTOMER,
        verbose_name=_('User Type')
    )
    is_verified = models.BooleanField(
        default=False,
        verbose_name=_('Phone Verified'),
        help_text=_('Whether phone number has been verified via OTP')
    )
    is_active = models.BooleanField(default=True, verbose_name=_('Active'))
    is_staff = models.BooleanField(default=False, verbose_name=_('Staff Status'))
    date_joined = models.DateTimeField(default=timezone.now, verbose_name=_('Date Joined'))
    profile_photo = models.ImageField(
        upload_to='profiles/',
        null=True,
        blank=True,
        verbose_name=_('Profile Photo')
    )

    objects = UserManager()

    USERNAME_FIELD = 'phone'
    REQUIRED_FIELDS = ['full_name']

    class Meta:
        verbose_name = _('User')
        verbose_name_plural = _('Users')
        ordering = ['-date_joined']
        indexes = [
            models.Index(fields=['phone']),
            models.Index(fields=['user_type']),
            models.Index(fields=['is_active']),
        ]

    def __str__(self):
        return f"{self.full_name} ({self.phone})"

    @property
    def is_customer(self):
        return self.user_type == self.UserType.CUSTOMER

    @property
    def is_driver(self):
        return self.user_type == self.UserType.DRIVER

    @property
    def is_merchant(self):
        return self.user_type == self.UserType.MERCHANT

    @property
    def is_admin_user(self):
        return self.user_type == self.UserType.ADMIN


class OTPVerification(models.Model):
    """Stores OTP codes for phone verification, login, and delivery confirmation."""

    class Purpose(models.TextChoices):
        REGISTRATION = 'REGISTRATION', _('Registration')
        LOGIN = 'LOGIN', _('Login')
        DELIVERY = 'DELIVERY', _('Delivery Confirmation')
        PASSWORD_RESET = 'PASSWORD_RESET', _('Password Reset')

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='otp_verifications',
        verbose_name=_('User')
    )
    otp = models.CharField(max_length=6, verbose_name=_('OTP Code'))
    purpose = models.CharField(
        max_length=20,
        choices=Purpose.choices,
        verbose_name=_('Purpose')
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name=_('Created At'))
    expires_at = models.DateTimeField(verbose_name=_('Expires At'))
    is_used = models.BooleanField(default=False, verbose_name=_('Is Used'))

    class Meta:
        verbose_name = _('OTP Verification')
        verbose_name_plural = _('OTP Verifications')
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', 'purpose', 'is_used']),
            models.Index(fields=['expires_at']),
        ]

    def save(self, *args, **kwargs):
        if not self.expires_at:
            from django.conf import settings
            expiry_minutes = getattr(settings, 'OTP_EXPIRY_MINUTES', 10)
            self.expires_at = timezone.now() + timedelta(minutes=expiry_minutes)
        super().save(*args, **kwargs)

    def is_valid(self):
        """Check if OTP is still valid (not used and not expired)."""
        return not self.is_used and self.expires_at > timezone.now()

    def mark_used(self):
        """Mark this OTP as used."""
        self.is_used = True
        self.save(update_fields=['is_used'])

    @classmethod
    def generate_otp(cls, length=6):
        """Generate a random numeric OTP."""
        return ''.join(random.choices(string.digits, k=length))

    def __str__(self):
        return f"OTP for {self.user.phone} ({self.purpose})"


class CustomerProfile(models.Model):
    """Extended profile for customer users."""

    user = models.OneToOneField(
        User,
        on_delete=models.CASCADE,
        related_name='customer_profile',
        verbose_name=_('User')
    )
    emergency_contact = PhoneNumberField(
        blank=True,
        null=True,
        verbose_name=_('Emergency Contact')
    )
    saved_addresses = models.JSONField(
        default=list,
        blank=True,
        verbose_name=_('Saved Addresses'),
        help_text=_('List of saved addresses: [{label, address, lat, lng}]')
    )
    total_orders = models.PositiveIntegerField(default=0, verbose_name=_('Total Orders'))
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = _('Customer Profile')
        verbose_name_plural = _('Customer Profiles')

    def __str__(self):
        return f"Customer: {self.user.full_name}"

    def add_saved_address(self, label, address, lat, lng):
        """Add a new saved address."""
        if self.saved_addresses is None:
            self.saved_addresses = []
        self.saved_addresses.append({
            'label': label,
            'address': address,
            'lat': lat,
            'lng': lng
        })
        self.save(update_fields=['saved_addresses'])

    def remove_saved_address(self, index):
        """Remove a saved address by index."""
        if self.saved_addresses and 0 <= index < len(self.saved_addresses):
            self.saved_addresses.pop(index)
            self.save(update_fields=['saved_addresses'])


class DriverProfile(models.Model):
    """Extended profile for driver users, including vehicle and approval details."""

    class VehicleType(models.TextChoices):
        MOTORCYCLE = 'MOTORCYCLE', _('Motorcycle')
        BICYCLE = 'BICYCLE', _('Bicycle')
        CAR = 'CAR', _('Car')
        TRUCK = 'TRUCK', _('Truck')

    user = models.OneToOneField(
        User,
        on_delete=models.CASCADE,
        related_name='driver_profile',
        verbose_name=_('User')
    )

    # Identity documents
    national_id_number = models.CharField(max_length=100, verbose_name=_('National ID Number'))
    national_id_photo = models.ImageField(
        upload_to='drivers/national_ids/',
        verbose_name=_('National ID Photo')
    )
    driver_license_number = models.CharField(max_length=100, verbose_name=_("Driver's License Number"))
    driver_license_photo = models.ImageField(
        upload_to='drivers/licenses/',
        verbose_name=_("Driver's License Photo")
    )

    # Vehicle information
    vehicle_type = models.CharField(
        max_length=15,
        choices=VehicleType.choices,
        verbose_name=_('Vehicle Type')
    )
    vehicle_plate = models.CharField(max_length=20, verbose_name=_('Vehicle Plate Number'))
    vehicle_model = models.CharField(max_length=100, verbose_name=_('Vehicle Model'))
    vehicle_photo = models.ImageField(
        upload_to='drivers/vehicles/',
        verbose_name=_('Vehicle Photo')
    )

    # Status
    is_approved = models.BooleanField(
        default=False,
        verbose_name=_('Is Approved'),
        help_text=_('Admin must approve driver before they can take orders')
    )
    is_online = models.BooleanField(
        default=False,
        verbose_name=_('Is Online'),
        help_text=_('Driver is available for orders')
    )
    current_location = gis_models.PointField(
        srid=4326,
        null=True,
        blank=True,
        verbose_name=_('Current Location'),
        help_text=_('Driver current GPS location (longitude, latitude)')
    )

    # Stats
    rating = models.DecimalField(
        max_digits=3,
        decimal_places=2,
        default=5.00,
        verbose_name=_('Rating')
    )
    total_deliveries = models.PositiveIntegerField(default=0, verbose_name=_('Total Deliveries'))
    total_earnings = models.DecimalField(
        max_digits=12,
        decimal_places=2,
        default=0.00,
        verbose_name=_('Total Earnings (ETB)')
    )

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    approved_at = models.DateTimeField(null=True, blank=True, verbose_name=_('Approved At'))

    class Meta:
        verbose_name = _('Driver Profile')
        verbose_name_plural = _('Driver Profiles')
        indexes = [
            models.Index(fields=['is_online', 'is_approved']),
        ]

    def __str__(self):
        return f"Driver: {self.user.full_name} ({self.vehicle_plate})"

    def update_rating(self, new_rating):
        """Update driver's average rating."""
        if self.total_deliveries > 0:
            total = float(self.rating) * self.total_deliveries + new_rating
            self.rating = total / (self.total_deliveries + 1)
        else:
            self.rating = new_rating
        self.save(update_fields=['rating'])


class MerchantProfile(models.Model):
    """Extended profile for merchant users."""

    class BusinessType(models.TextChoices):
        RESTAURANT = 'RESTAURANT', _('Restaurant')
        PHARMACY = 'PHARMACY', _('Pharmacy')
        GROCERY = 'GROCERY', _('Grocery Store')
        ELECTRONICS = 'ELECTRONICS', _('Electronics')
        CLOTHING = 'CLOTHING', _('Clothing')
        BAKERY = 'BAKERY', _('Bakery')
        OTHER = 'OTHER', _('Other')

    user = models.OneToOneField(
        User,
        on_delete=models.CASCADE,
        related_name='merchant_profile',
        verbose_name=_('User')
    )
    business_name = models.CharField(max_length=255, verbose_name=_('Business Name'))
    business_type = models.CharField(
        max_length=20,
        choices=BusinessType.choices,
        verbose_name=_('Business Type')
    )
    business_license = models.ImageField(
        upload_to='merchants/licenses/',
        null=True,
        blank=True,
        verbose_name=_('Business License')
    )
    business_address = models.CharField(max_length=500, verbose_name=_('Business Address'))
    business_location = gis_models.PointField(
        srid=4326,
        verbose_name=_('Business Location'),
        help_text=_('GPS coordinates of business location')
    )
    is_approved = models.BooleanField(
        default=False,
        verbose_name=_('Is Approved'),
        help_text=_('Admin must approve merchant before they can list products')
    )
    is_open = models.BooleanField(default=True, verbose_name=_('Is Open'))
    rating = models.DecimalField(
        max_digits=3,
        decimal_places=2,
        default=5.00,
        verbose_name=_('Rating')
    )
    total_orders = models.PositiveIntegerField(default=0, verbose_name=_('Total Orders'))
    phone = PhoneNumberField(blank=True, verbose_name=_('Business Phone'))
    description = models.TextField(blank=True, verbose_name=_('Business Description'))

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    approved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = _('Merchant Profile')
        verbose_name_plural = _('Merchant Profiles')
        indexes = [
            models.Index(fields=['business_type', 'is_approved', 'is_open']),
        ]

    def __str__(self):
        return f"Merchant: {self.business_name} ({self.user.full_name})"
