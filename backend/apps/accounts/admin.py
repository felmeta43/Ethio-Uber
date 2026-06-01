"""Admin configuration for accounts app."""
from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from django.contrib.gis.admin import GISModelAdmin
from django.utils.translation import gettext_lazy as _

from .models import User, OTPVerification, CustomerProfile, DriverProfile, MerchantProfile


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    list_display = ['phone', 'full_name', 'email', 'user_type', 'is_verified', 'is_active', 'date_joined']
    list_filter = ['user_type', 'is_verified', 'is_active', 'is_staff']
    search_fields = ['phone', 'full_name', 'email']
    ordering = ['-date_joined']

    fieldsets = (
        (None, {'fields': ('phone', 'password')}),
        (_('Personal Info'), {'fields': ('full_name', 'email', 'profile_photo')}),
        (_('Account Info'), {'fields': ('user_type', 'is_verified')}),
        (_('Permissions'), {'fields': ('is_active', 'is_staff', 'is_superuser', 'groups', 'user_permissions')}),
        (_('Important Dates'), {'fields': ('last_login', 'date_joined')}),
    )

    add_fieldsets = (
        (None, {
            'classes': ('wide',),
            'fields': ('phone', 'full_name', 'user_type', 'password1', 'password2'),
        }),
    )


@admin.register(OTPVerification)
class OTPVerificationAdmin(admin.ModelAdmin):
    list_display = ['user', 'otp', 'purpose', 'created_at', 'expires_at', 'is_used']
    list_filter = ['purpose', 'is_used']
    search_fields = ['user__phone', 'user__full_name', 'otp']
    ordering = ['-created_at']
    readonly_fields = ['created_at']


@admin.register(CustomerProfile)
class CustomerProfileAdmin(admin.ModelAdmin):
    list_display = ['user', 'emergency_contact', 'total_orders']
    search_fields = ['user__phone', 'user__full_name']
    readonly_fields = ['total_orders']


@admin.register(DriverProfile)
class DriverProfileAdmin(GISModelAdmin):
    list_display = [
        'user', 'vehicle_type', 'vehicle_plate', 'vehicle_model',
        'is_approved', 'is_online', 'rating', 'total_deliveries'
    ]
    list_filter = ['vehicle_type', 'is_approved', 'is_online']
    search_fields = ['user__phone', 'user__full_name', 'vehicle_plate', 'national_id_number']
    readonly_fields = ['rating', 'total_deliveries', 'total_earnings', 'approved_at']
    actions = ['approve_drivers', 'reject_drivers']

    def approve_drivers(self, request, queryset):
        from django.utils import timezone
        updated = queryset.update(is_approved=True, approved_at=timezone.now())
        self.message_user(request, f'{updated} driver(s) approved.')
    approve_drivers.short_description = 'Approve selected drivers'

    def reject_drivers(self, request, queryset):
        updated = queryset.update(is_approved=False)
        self.message_user(request, f'{updated} driver(s) rejected.')
    reject_drivers.short_description = 'Reject selected drivers'


@admin.register(MerchantProfile)
class MerchantProfileAdmin(GISModelAdmin):
    list_display = [
        'business_name', 'user', 'business_type', 'is_approved',
        'is_open', 'rating', 'total_orders'
    ]
    list_filter = ['business_type', 'is_approved', 'is_open']
    search_fields = ['business_name', 'user__phone', 'user__full_name', 'business_address']
    readonly_fields = ['rating', 'total_orders', 'approved_at']
    actions = ['approve_merchants']

    def approve_merchants(self, request, queryset):
        from django.utils import timezone
        updated = queryset.update(is_approved=True, approved_at=timezone.now())
        self.message_user(request, f'{updated} merchant(s) approved.')
    approve_merchants.short_description = 'Approve selected merchants'
