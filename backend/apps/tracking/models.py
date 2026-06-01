"""
Tracking models for real-time driver location history.
"""
from django.contrib.gis.db import models as gis_models
from django.db import models
from django.utils.translation import gettext_lazy as _


class DriverLocationHistory(models.Model):
    """
    Stores historical GPS location data for drivers during deliveries.
    Used for analytics, route replay, and dispute resolution.
    """
    driver = models.ForeignKey(
        'accounts.User',
        on_delete=models.CASCADE,
        related_name='location_history',
        limit_choices_to={'user_type': 'DRIVER'},
        verbose_name=_('Driver')
    )
    location = gis_models.PointField(
        srid=4326,
        verbose_name=_('Location'),
        help_text=_('GPS coordinates (longitude, latitude)')
    )
    timestamp = models.DateTimeField(auto_now_add=True, verbose_name=_('Timestamp'))
    order = models.ForeignKey(
        'orders.Order',
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='location_tracking',
        verbose_name=_('Order')
    )
    speed = models.FloatField(null=True, blank=True, verbose_name=_('Speed (km/h)'))
    heading = models.FloatField(null=True, blank=True, verbose_name=_('Heading (degrees)'))
    accuracy = models.FloatField(null=True, blank=True, verbose_name=_('GPS Accuracy (meters)'))

    class Meta:
        verbose_name = _('Driver Location History')
        verbose_name_plural = _('Driver Location Histories')
        ordering = ['-timestamp']
        indexes = [
            models.Index(fields=['driver', 'timestamp']),
            models.Index(fields=['order', 'timestamp']),
        ]

    def __str__(self):
        return f"{self.driver.full_name} at {self.timestamp}"

    @property
    def latitude(self):
        return self.location.y if self.location else None

    @property
    def longitude(self):
        return self.location.x if self.location else None
