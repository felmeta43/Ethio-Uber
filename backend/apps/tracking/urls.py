"""URL configuration for the tracking app."""
from django.urls import path
from .views import DriverLocationHistoryView, LiveDriverLocationView

urlpatterns = [
    path('orders/<int:order_id>/history/', DriverLocationHistoryView.as_view(), name='location-history'),
    path('orders/<int:order_id>/live/', LiveDriverLocationView.as_view(), name='live-location'),
]
