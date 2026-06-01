"""
Views for the orders app.
"""
import logging
from django.contrib.gis.geos import Point
from django.shortcuts import get_object_or_404
from rest_framework import status, generics, filters
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from django_filters.rest_framework import DjangoFilterBackend
from drf_spectacular.utils import extend_schema

from .models import Order, DeliveryFeeConfig
from .serializers import (
    OrderCreateSerializer, OrderDetailSerializer, OrderListSerializer,
    OrderStatusUpdateSerializer, VerifyDeliveryOTPSerializer,
    RatingSerializer, DeliveryFeeEstimateSerializer, DeliveryFeeConfigSerializer,
)
from .services import calculate_delivery_fee, assign_driver_to_order, cancel_order
from apps.accounts.permissions import IsCustomer, IsDriver, IsAdmin, IsDriverApproved

logger = logging.getLogger(__name__)


class EstimateDeliveryFeeView(APIView):
    """Estimate delivery fee before creating an order."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Orders'], request=DeliveryFeeEstimateSerializer)
    def post(self, request):
        serializer = DeliveryFeeEstimateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        pickup_point = Point(data['pickup_longitude'], data['pickup_latitude'], srid=4326)
        destination_point = Point(data['destination_longitude'], data['destination_latitude'], srid=4326)

        fee_data = calculate_delivery_fee(
            pickup_point, destination_point,
            data['parcel_size'], data.get('is_urgent', False)
        )

        return Response({
            'success': True,
            'data': {
                'base_fee': str(fee_data['base_fee']),
                'distance_fee': str(fee_data['distance_fee']),
                'parcel_size_fee': str(fee_data['parcel_size_fee']),
                'urgent_fee': str(fee_data['urgent_fee']),
                'total_fee': str(fee_data['total_fee']),
                'distance_km': str(fee_data['distance_km']),
                'currency': 'ETB',
            }
        })


class OrderCreateView(generics.CreateAPIView):
    """Create a new delivery order."""
    permission_classes = [IsAuthenticated, IsCustomer]
    serializer_class = OrderCreateSerializer

    @extend_schema(tags=['Orders'])
    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        order = serializer.save()
        return Response({
            'success': True,
            'message': 'Order created successfully.',
            'data': OrderDetailSerializer(order).data,
        }, status=status.HTTP_201_CREATED)


class OrderListView(generics.ListAPIView):
    """List orders for the current user."""
    permission_classes = [IsAuthenticated]
    serializer_class = OrderListSerializer
    filter_backends = [DjangoFilterBackend, filters.OrderingFilter]
    filterset_fields = ['status', 'payment_status', 'parcel_size']
    ordering_fields = ['created_at', 'total_fee']
    ordering = ['-created_at']

    @extend_schema(tags=['Orders'])
    def get_queryset(self):
        user = self.request.user
        if user.user_type == 'CUSTOMER':
            return Order.objects.filter(customer=user).select_related('driver', 'merchant')
        elif user.user_type == 'DRIVER':
            return Order.objects.filter(driver=user).select_related('customer', 'merchant')
        elif user.user_type == 'MERCHANT':
            return Order.objects.filter(merchant__user=user).select_related('customer', 'driver')
        elif user.user_type == 'ADMIN' or user.is_staff:
            return Order.objects.all().select_related('customer', 'driver', 'merchant')
        return Order.objects.none()


class OrderDetailView(generics.RetrieveAPIView):
    """Get detailed information about a specific order."""
    permission_classes = [IsAuthenticated]
    serializer_class = OrderDetailSerializer

    @extend_schema(tags=['Orders'])
    def get_queryset(self):
        user = self.request.user
        if user.user_type == 'CUSTOMER':
            return Order.objects.filter(customer=user)
        elif user.user_type == 'DRIVER':
            return Order.objects.filter(driver=user)
        elif user.user_type == 'MERCHANT':
            return Order.objects.filter(merchant__user=user)
        elif user.user_type == 'ADMIN' or user.is_staff:
            return Order.objects.all()
        return Order.objects.none()


class OrderCancelView(APIView):
    """Cancel an order."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Orders'])
    def post(self, request, pk):
        user = request.user
        try:
            if user.user_type == 'CUSTOMER':
                order = Order.objects.get(pk=pk, customer=user)
            elif user.user_type == 'ADMIN' or user.is_staff:
                order = Order.objects.get(pk=pk)
            else:
                return Response(
                    {'success': False, 'message': 'Permission denied.'},
                    status=status.HTTP_403_FORBIDDEN
                )
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found.'},
                status=status.HTTP_404_NOT_FOUND
            )

        reason = request.data.get('reason', '')
        try:
            cancel_order(order, user, reason)
        except ValueError as e:
            return Response({'success': False, 'message': str(e)}, status=status.HTTP_400_BAD_REQUEST)

        return Response({
            'success': True,
            'message': 'Order cancelled successfully.',
            'data': OrderDetailSerializer(order).data,
        })


class AcceptOrderView(APIView):
    """Driver accepts an assigned order."""
    permission_classes = [IsAuthenticated, IsDriver, IsDriverApproved]

    @extend_schema(tags=['Orders'])
    def post(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, driver=request.user, status=Order.Status.DRIVER_ASSIGNED)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found or not assigned to you.'},
                status=status.HTTP_404_NOT_FOUND
            )

        order.update_status(Order.Status.DRIVER_ON_WAY, note="Driver accepted the order")

        try:
            from apps.notifications.services import notify_order_accepted
            notify_order_accepted(order)
        except Exception as e:
            logger.error(f"Failed to send order accepted notification: {e}")

        return Response({
            'success': True,
            'message': 'Order accepted. Head to pickup location.',
            'data': OrderDetailSerializer(order).data,
        })


class RejectOrderView(APIView):
    """Driver rejects an assigned order — system finds next driver."""
    permission_classes = [IsAuthenticated, IsDriver]

    @extend_schema(tags=['Orders'])
    def post(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, driver=request.user, status=Order.Status.DRIVER_ASSIGNED)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found or not assigned to you.'},
                status=status.HTTP_404_NOT_FOUND
            )

        # Add this driver to rejected list
        if order.rejected_driver_ids is None:
            order.rejected_driver_ids = []
        order.rejected_driver_ids.append(request.user.id)
        order.driver = None
        order.save()

        # Find next available driver
        order.update_status(Order.Status.FINDING_DRIVER, note="Driver rejected - finding new driver")
        next_driver = assign_driver_to_order(order)

        if next_driver:
            return Response({
                'success': True,
                'message': 'Order rejected. Another driver will be assigned.',
            })
        else:
            return Response({
                'success': True,
                'message': 'Order rejected. No other drivers available nearby.',
            })


class UpdateOrderStatusView(APIView):
    """Driver updates order status during delivery."""
    permission_classes = [IsAuthenticated, IsDriver]

    # Valid status transitions for drivers
    DRIVER_VALID_TRANSITIONS = {
        Order.Status.DRIVER_ON_WAY: [Order.Status.ARRIVED_AT_PICKUP],
        Order.Status.ARRIVED_AT_PICKUP: [Order.Status.PICKED_UP],
        Order.Status.PICKED_UP: [Order.Status.IN_TRANSIT],
        Order.Status.IN_TRANSIT: [Order.Status.ARRIVED_AT_DESTINATION],
        Order.Status.ARRIVED_AT_DESTINATION: [Order.Status.DELIVERED],
    }

    @extend_schema(tags=['Orders'], request=OrderStatusUpdateSerializer)
    def patch(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, driver=request.user)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found.'},
                status=status.HTTP_404_NOT_FOUND
            )

        serializer = OrderStatusUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        new_status = serializer.validated_data['status']
        note = serializer.validated_data.get('note', '')
        latitude = serializer.validated_data.get('latitude')
        longitude = serializer.validated_data.get('longitude')

        # Validate transition
        valid_next = self.DRIVER_VALID_TRANSITIONS.get(order.status, [])
        if new_status not in valid_next:
            return Response({
                'success': False,
                'message': f"Cannot change status from '{order.status}' to '{new_status}'.",
            }, status=status.HTTP_400_BAD_REQUEST)

        location = None
        if latitude and longitude:
            location = Point(float(longitude), float(latitude), srid=4326)

        order.update_status(new_status, note=note, location=location)

        # Send notifications
        try:
            from apps.notifications.services import (
                notify_driver_arrived, notify_order_picked_up, notify_order_delivered
            )
            if new_status == Order.Status.ARRIVED_AT_PICKUP:
                notify_driver_arrived(order)
            elif new_status == Order.Status.PICKED_UP:
                notify_order_picked_up(order)
            elif new_status == Order.Status.DELIVERED:
                notify_order_delivered(order)
                # Update driver stats
                dp = request.user.driver_profile
                dp.total_deliveries += 1
                dp.total_earnings += order.driver_earnings
                dp.save()
                # Update customer profile
                cp = order.customer.customer_profile
                cp.total_orders += 1
                cp.save()
        except Exception as e:
            logger.error(f"Notification error: {e}")

        return Response({
            'success': True,
            'message': f'Order status updated to {new_status}.',
            'data': OrderDetailSerializer(order).data,
        })


class VerifyDeliveryOTPView(APIView):
    """Driver verifies delivery OTP to complete delivery."""
    permission_classes = [IsAuthenticated, IsDriver]

    @extend_schema(tags=['Orders'], request=VerifyDeliveryOTPSerializer)
    def post(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, driver=request.user)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found.'},
                status=status.HTTP_404_NOT_FOUND
            )

        if order.status != Order.Status.ARRIVED_AT_DESTINATION:
            return Response(
                {'success': False, 'message': 'Order must be at destination to verify OTP.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = VerifyDeliveryOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        entered_otp = serializer.validated_data['otp']
        if entered_otp != order.delivery_otp:
            return Response(
                {'success': False, 'message': 'Incorrect OTP. Please ask the recipient for the correct code.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        order.otp_verified = True
        order.save(update_fields=['otp_verified'])
        order.update_status(Order.Status.DELIVERED, note="OTP verified - delivery confirmed")

        # Update earnings and stats
        try:
            dp = request.user.driver_profile
            dp.total_deliveries += 1
            dp.total_earnings += order.driver_earnings
            dp.save()
            cp = order.customer.customer_profile
            cp.total_orders += 1
            cp.save()

            # Credit driver's wallet
            from apps.payments.services import credit_driver_earnings
            credit_driver_earnings(order)
        except Exception as e:
            logger.error(f"Error crediting driver earnings: {e}")

        try:
            from apps.notifications.services import notify_order_delivered
            notify_order_delivered(order)
        except Exception as e:
            logger.error(f"Notification error: {e}")

        return Response({
            'success': True,
            'message': 'Delivery confirmed! OTP verified successfully.',
            'data': OrderDetailSerializer(order).data,
        })


class RateDriverView(APIView):
    """Customer rates the driver after delivery."""
    permission_classes = [IsAuthenticated, IsCustomer]

    @extend_schema(tags=['Orders'], request=RatingSerializer)
    def post(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, customer=request.user, status=Order.Status.DELIVERED)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found or not yet delivered.'},
                status=status.HTTP_404_NOT_FOUND
            )

        if order.customer_rating:
            return Response(
                {'success': False, 'message': 'You have already rated this delivery.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = RatingSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        order.customer_rating = serializer.validated_data['rating']
        order.customer_feedback = serializer.validated_data.get('feedback', '')
        order.save(update_fields=['customer_rating', 'customer_feedback'])

        # Update driver's average rating
        if order.driver:
            try:
                driver_profile = order.driver.driver_profile
                driver_profile.update_rating(serializer.validated_data['rating'])
            except Exception as e:
                logger.error(f"Error updating driver rating: {e}")

        return Response({'success': True, 'message': 'Thank you for rating!'})


class RateCustomerView(APIView):
    """Driver rates the customer after delivery."""
    permission_classes = [IsAuthenticated, IsDriver]

    @extend_schema(tags=['Orders'], request=RatingSerializer)
    def post(self, request, pk):
        try:
            order = Order.objects.get(pk=pk, driver=request.user, status=Order.Status.DELIVERED)
        except Order.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Order not found or not yet delivered.'},
                status=status.HTTP_404_NOT_FOUND
            )

        if order.driver_rating:
            return Response(
                {'success': False, 'message': 'You have already rated this customer.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = RatingSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        order.driver_rating = serializer.validated_data['rating']
        order.save(update_fields=['driver_rating'])

        return Response({'success': True, 'message': 'Customer rated successfully.'})


class DeliveryFeeConfigView(generics.RetrieveUpdateAPIView):
    """Admin view to manage delivery fee configuration."""
    permission_classes = [IsAuthenticated, IsAdmin]
    serializer_class = DeliveryFeeConfigSerializer

    @extend_schema(tags=['Orders'])
    def get_object(self):
        return DeliveryFeeConfig.get_active()
