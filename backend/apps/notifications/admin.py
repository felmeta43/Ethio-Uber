from django.contrib import admin
from .models import DeviceToken, Notification


@admin.register(DeviceToken)
class DeviceTokenAdmin(admin.ModelAdmin):
    list_display = ['user', 'device_type', 'device_id', 'is_active', 'updated_at']
    list_filter = ['device_type', 'is_active']
    search_fields = ['user__full_name', 'user__phone', 'device_id']


@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ['user', 'notification_type', 'title', 'is_read', 'created_at']
    list_filter = ['notification_type', 'is_read']
    search_fields = ['user__full_name', 'title', 'body']
    readonly_fields = ['created_at']
    ordering = ['-created_at']
