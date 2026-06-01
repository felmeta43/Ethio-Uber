"""
Views for the tracking app.
"""
import logging
from rest_framework import generics
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from drf_spectacular.utils import extend_schema

from .models import DriverLocationHistory
from .serializers import DriverLocationHistorySerializer
from apps.accounts.permissions import IsAdmin

logger = logging.getLogger(__name__)


class DriverLocationHistoryView(generics.ListAPIView):
    """Get location history for a specific order (admin/driver)."""
    permission_classes = [IsAuthenticated]
    serializer_class = DriverLocationHistorySerializer

    @extend_schema(tags=['Tracking'])
    def get_queryset(self):
        user = self.request.user
        order_id = self.kwargs.get('order_id')
        qs = DriverLocationHistory.objects.filter(order_id=order_id).order_by('timestamp')
        if user.user_type == 'ADMIN' or user.is_staff:
            return qs
        elif user.user_type == 'DRIVER':
            return qs.filter(driver=user)
        return DriverLocationHistory.objects.none()


class LiveDriverLocationView(APIView):
    """Get current location of driver assigned to an order."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Tracking'])
    def get(self, request, order_id):
        from apps.orders.models import Order
        try:
            if request.user.user_type == 'CUSTOMER':
                order = Order.objects.get(id=order_id, customer=request.user)
            elif request.user.user_type == 'DRIVER':
                order = Order.objects.get(id=order_id, driver=request.user)
            elif request.user.user_type in ['ADMIN', 'MERCHANT']:
                order = Order.objects.get(id=order_id)
            else:
                return Response({'success': False, 'message': 'Permission denied.'}, status=403)
        except Order.DoesNotExist:
            return Response({'success': False, 'message': 'Order not found.'}, status=404)

        if not order.driver:
            return Response({'success': False, 'message': 'No driver assigned to this order.'})

        try:
            dp = order.driver.driver_profile
            if dp.current_location:
                return Response({
                    'success': True,
                    'data': {
                        'driver_name': order.driver.full_name,
                        'lat': dp.current_location.y,
                        'lng': dp.current_location.x,
                        'vehicle_type': dp.vehicle_type,
                        'vehicle_plate': dp.vehicle_plate,
                    }
                })
        except Exception:
            pass

        return Response({'success': False, 'message': 'Driver location not available.'})
