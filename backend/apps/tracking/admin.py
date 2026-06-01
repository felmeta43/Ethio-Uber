"""Admin configuration for tracking app."""
from django.contrib import admin
from django.contrib.gis.admin import GISModelAdmin
from .models import DriverLocationHistory


@admin.register(DriverLocationHistory)
class DriverLocationHistoryAdmin(GISModelAdmin):
    list_display = ['driver', 'timestamp', 'speed', 'heading', 'order']
    list_filter = ['driver']
    search_fields = ['driver__full_name', 'driver__phone']
    ordering = ['-timestamp']
    readonly_fields = ['timestamp']
