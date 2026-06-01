"""WebSocket URL routing for tracking consumers."""
from django.urls import re_path
from . import consumers

websocket_urlpatterns = [
    re_path(r'^ws/tracking/(?P<order_id>\d+)/$', consumers.OrderTrackingConsumer.as_asgi()),
    re_path(r'^ws/driver/availability/$', consumers.DriverAvailabilityConsumer.as_asgi()),
]
