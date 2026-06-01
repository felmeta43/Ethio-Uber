"""
Merchant views for Ethio-Uber.
Handles product management, merchant orders, and merchant browsing.
"""
from django.utils import timezone
from django.db import transaction
from rest_framework import generics, status, permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.models import MerchantProfile
from apps.accounts.permissions import IsMerchant, IsCustomer, IsAdmin
from apps.orders.models import Order
from apps.orders.services import calculate_delivery_fee
from .models import MerchantCategory, MerchantProduct, MerchantOrder, MerchantOrderItem
from .serializers import (
    MerchantCategorySerializer, MerchantProductSerializer,
    MerchantProductCreateSerializer, MerchantOrderSerializer,
    MerchantOrderCreateSerializer, MerchantProfilePublicSerializer,
    MerchantStatusUpdateSerializer,
)


class MerchantCategoryListView(generics.ListAPIView):
    """GET /merchants/categories/ — list all active product categories."""
    serializer_class = MerchantCategorySerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        return MerchantCategory.objects.filter(is_active=True)


class MerchantListView(generics.ListAPIView):
    """GET /merchants/ — list approved, open merchants for customers."""
    serializer_class = MerchantProfilePublicSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        qs = MerchantProfile.objects.filter(is_approved=True, is_open=True)
        business_type = self.request.query_params.get('type')
        if business_type:
            qs = qs.filter(business_type=business_type)
        return qs.order_by('-rating')


class MerchantDetailView(generics.RetrieveAPIView):
    """GET /merchants/{id}/ — single merchant with their products."""
    serializer_class = MerchantProfilePublicSerializer
    permission_classes = [permissions.IsAuthenticated]
    queryset = MerchantProfile.objects.filter(is_approved=True)


class MerchantProductListView(generics.ListAPIView):
    """GET /merchants/{merchant_id}/products/ — products for a specific merchant."""
    serializer_class = MerchantProductSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        merchant_id = self.kwargs['merchant_id']
        return MerchantProduct.objects.filter(
            merchant_id=merchant_id,
            merchant__is_approved=True,
            is_available=True,
        ).select_related('category')


class MyProductListCreateView(generics.ListCreateAPIView):
    """
    GET  /merchants/my/products/ — merchant lists their products
    POST /merchants/my/products/ — merchant adds a product
    """
    permission_classes = [permissions.IsAuthenticated, IsMerchant]

    def get_serializer_class(self):
        if self.request.method == 'POST':
            return MerchantProductCreateSerializer
        return MerchantProductSerializer

    def get_queryset(self):
        return MerchantProduct.objects.filter(
            merchant=self.request.user.merchant_profile
        ).select_related('category')


class MyProductDetailView(generics.RetrieveUpdateDestroyAPIView):
    """GET/PATCH/DELETE /merchants/my/products/{id}/ — manage a specific product."""
    serializer_class = MerchantProductSerializer
    permission_classes = [permissions.IsAuthenticated, IsMerchant]

    def get_queryset(self):
        return MerchantProduct.objects.filter(merchant=self.request.user.merchant_profile)

    def get_serializer_class(self):
        if self.request.method in ('PUT', 'PATCH'):
            return MerchantProductCreateSerializer
        return MerchantProductSerializer


class CreateMerchantOrderView(APIView):
    """POST /merchants/orders/ — customer places an order with a merchant."""
    permission_classes = [permissions.IsAuthenticated, IsCustomer]

    def post(self, request):
        serializer = MerchantOrderCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        try:
            merchant = MerchantProfile.objects.get(id=data['merchant_id'], is_approved=True, is_open=True)
        except MerchantProfile.DoesNotExist:
            return Response({'error': 'Merchant not available.'}, status=status.HTTP_400_BAD_REQUEST)

        from django.contrib.gis.geos import Point
        pickup_point = Point(data['pickup_lng'], data['pickup_lat'], srid=4326)
        dest_point = Point(data['destination_lng'], data['destination_lat'], srid=4326)

        # Calculate items subtotal
        items_data = []
        subtotal = 0
        for item_data in data['items']:
            product = MerchantProduct.objects.get(id=item_data['product_id'])
            qty = int(item_data['quantity'])
            item_subtotal = float(product.price) * qty
            subtotal += item_subtotal
            items_data.append({'product': product, 'quantity': qty, 'unit_price': product.price})

        fee_breakdown = calculate_delivery_fee(pickup_point, dest_point, 'MEDIUM', False)

        with transaction.atomic():
            order = Order.objects.create(
                customer=request.user,
                merchant=merchant,
                pickup_address=data['pickup_address'],
                pickup_location=pickup_point,
                destination_address=data['destination_address'],
                destination_location=dest_point,
                description=f"Merchant order from {merchant.business_name}",
                parcel_size='MEDIUM',
                is_urgent=False,
                payment_method=data['payment_method'],
                base_fee=fee_breakdown['base_fee'],
                distance_fee=fee_breakdown['distance_fee'],
                urgent_fee=0,
                total_fee=fee_breakdown['total_fee'] + subtotal,
                distance_km=fee_breakdown['distance_km'],
                status=Order.Status.PENDING,
            )

            merchant_order = MerchantOrder.objects.create(
                order=order,
                merchant=merchant,
                special_instructions=data.get('special_instructions', ''),
                subtotal=subtotal,
            )

            for item in items_data:
                MerchantOrderItem.objects.create(
                    merchant_order=merchant_order,
                    product=item['product'],
                    quantity=item['quantity'],
                    unit_price=item['unit_price'],
                )

        from apps.notifications.services import notify_order_placed
        notify_order_placed(order)

        return Response(
            MerchantOrderSerializer(merchant_order).data,
            status=status.HTTP_201_CREATED
        )


class MyMerchantOrderListView(generics.ListAPIView):
    """GET /merchants/my/orders/ — merchant views incoming orders."""
    serializer_class = MerchantOrderSerializer
    permission_classes = [permissions.IsAuthenticated, IsMerchant]

    def get_queryset(self):
        merchant = self.request.user.merchant_profile
        status_filter = self.request.query_params.get('status')
        qs = MerchantOrder.objects.filter(merchant=merchant).select_related(
            'order', 'order__customer'
        ).prefetch_related('items__product')
        if status_filter:
            qs = qs.filter(status=status_filter)
        return qs.order_by('-created_at')


class MerchantOrderStatusUpdateView(APIView):
    """PATCH /merchants/my/orders/{id}/status/ — merchant accepts/rejects/prepares order."""
    permission_classes = [permissions.IsAuthenticated, IsMerchant]

    def patch(self, request, pk):
        try:
            merchant_order = MerchantOrder.objects.get(
                pk=pk, merchant=request.user.merchant_profile
            )
        except MerchantOrder.DoesNotExist:
            return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)

        serializer = MerchantStatusUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        merchant_order.status = data['status']
        if data['status'] == 'REJECTED':
            merchant_order.rejection_reason = data.get('rejection_reason', '')
        if 'estimated_ready_minutes' in data:
            from datetime import timedelta
            merchant_order.estimated_ready_time = timezone.now() + timedelta(
                minutes=data['estimated_ready_minutes']
            )
        merchant_order.save()

        return Response(MerchantOrderSerializer(merchant_order).data)


class MerchantOpenCloseView(APIView):
    """PATCH /merchants/my/toggle-open/ — merchant toggles open/closed status."""
    permission_classes = [permissions.IsAuthenticated, IsMerchant]

    def patch(self, request):
        merchant = request.user.merchant_profile
        merchant.is_open = not merchant.is_open
        merchant.save(update_fields=['is_open'])
        return Response({'is_open': merchant.is_open})
