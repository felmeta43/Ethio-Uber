"""Serializers for the tracking app."""
from rest_framework import serializers
from .models import DriverLocationHistory


class DriverLocationHistorySerializer(serializers.ModelSerializer):
    """Serializer for driver location history."""
    latitude = serializers.ReadOnlyField()
    longitude = serializers.ReadOnlyField()
    driver_name = serializers.CharField(source='driver.full_name', read_only=True)

    class Meta:
        model = DriverLocationHistory
        fields = ['id', 'driver_name', 'latitude', 'longitude', 'timestamp', 'speed', 'heading', 'accuracy', 'order']
