"""Admin configuration for orders app."""
from django.contrib import admin
from django.contrib.gis.admin import GISModelAdmin
from .models import Order, OrderStatusHistory, DeliveryFeeConfig


class OrderStatusHistoryInline(admin.TabularInline):
    model = OrderStatusHistory
    extra = 0
    readonly_fields = ['status', 'timestamp', 'note']
    can_delete = False


@admin.register(Order)
class OrderAdmin(GISModelAdmin):
    list_display = [
        'order_number', 'customer', 'driver', 'status',
        'total_fee', 'payment_method', 'payment_status', 'created_at'
    ]
    list_filter = ['status', 'payment_method', 'payment_status', 'parcel_size', 'is_urgent']
    search_fields = ['order_number', 'customer__full_name', 'driver__full_name', 'pickup_address']
    ordering = ['-created_at']
    readonly_fields = ['order_number', 'created_at', 'accepted_at', 'picked_up_at', 'delivered_at']
    inlines = [OrderStatusHistoryInline]

    fieldsets = (
        ('Order Info', {'fields': ('order_number', 'customer', 'driver', 'merchant', 'status')}),
        ('Locations', {'fields': ('pickup_address', 'pickup_location', 'destination_address', 'destination_location')}),
        ('Parcel', {'fields': ('description', 'parcel_size', 'is_urgent')}),
        ('Delivery', {'fields': ('delivery_otp', 'otp_verified')}),
        ('Pricing', {'fields': ('base_fee', 'distance_fee', 'urgent_fee', 'parcel_size_fee', 'total_fee', 'distance_km', 'driver_earnings', 'platform_commission')}),
        ('Payment', {'fields': ('payment_method', 'payment_status')}),
        ('Timestamps', {'fields': ('created_at', 'accepted_at', 'picked_up_at', 'delivered_at', 'cancelled_at')}),
        ('Ratings', {'fields': ('customer_rating', 'customer_feedback', 'driver_rating')}),
    )


@admin.register(OrderStatusHistory)
class OrderStatusHistoryAdmin(GISModelAdmin):
    list_display = ['order', 'status', 'timestamp', 'note']
    list_filter = ['status']
    search_fields = ['order__order_number']
    ordering = ['-timestamp']


@admin.register(DeliveryFeeConfig)
class DeliveryFeeConfigAdmin(admin.ModelAdmin):
    list_display = [
        'base_fee', 'per_km_fee', 'urgent_multiplier',
        'platform_commission_percent', 'is_active', 'updated_at'
    ]
    list_filter = ['is_active']
