"""
Serializers for the orders app.
"""
from decimal import Decimal
from django.contrib.gis.geos import Point
from rest_framework import serializers
from drf_spectacular.utils import extend_schema_field

from .models import Order, OrderStatusHistory, DeliveryFeeConfig


class OrderCreateSerializer(serializers.ModelSerializer):
    """Serializer for creating a new delivery order."""
    pickup_latitude = serializers.FloatField(write_only=True)
    pickup_longitude = serializers.FloatField(write_only=True)
    destination_latitude = serializers.FloatField(write_only=True)
    destination_longitude = serializers.FloatField(write_only=True)

    class Meta:
        model = Order
        fields = [
            'pickup_address', 'pickup_latitude', 'pickup_longitude',
            'destination_address', 'destination_latitude', 'destination_longitude',
            'description', 'parcel_size', 'is_urgent', 'payment_method',
        ]

    def validate(self, attrs):
        pickup_lat = attrs.get('pickup_latitude')
        pickup_lng = attrs.get('pickup_longitude')
        dest_lat = attrs.get('destination_latitude')
        dest_lng = attrs.get('destination_longitude')

        if not all([pickup_lat, pickup_lng, dest_lat, dest_lng]):
            raise serializers.ValidationError('All location coordinates are required.')

        if pickup_lat == dest_lat and pickup_lng == dest_lng:
            raise serializers.ValidationError('Pickup and destination cannot be the same location.')

        return attrs

    def create(self, validated_data):
        from apps.orders.services import calculate_delivery_fee, generate_delivery_otp, assign_driver_to_order

        pickup_lat = validated_data.pop('pickup_latitude')
        pickup_lng = validated_data.pop('pickup_longitude')
        dest_lat = validated_data.pop('destination_latitude')
        dest_lng = validated_data.pop('destination_longitude')

        pickup_point = Point(pickup_lng, pickup_lat, srid=4326)
        destination_point = Point(dest_lng, dest_lat, srid=4326)

        parcel_size = validated_data.get('parcel_size', Order.ParcelSize.SMALL)
        is_urgent = validated_data.get('is_urgent', False)

        # Calculate fee
        fee_data = calculate_delivery_fee(pickup_point, destination_point, parcel_size, is_urgent)

        # Generate delivery OTP
        delivery_otp = generate_delivery_otp(4)

        customer = self.context['request'].user

        order = Order.objects.create(
            customer=customer,
            pickup_location=pickup_point,
            destination_location=destination_point,
            delivery_otp=delivery_otp,
            **fee_data,
            **validated_data,
        )

        # Find and assign nearest driver
        assign_driver_to_order(order)

        return order


class OrderStatusHistorySerializer(serializers.ModelSerializer):
    """Serializer for order status history."""
    class Meta:
        model = OrderStatusHistory
        fields = ['status', 'timestamp', 'note']


class OrderListSerializer(serializers.ModelSerializer):
    """Summary serializer for order lists."""
    customer_name = serializers.CharField(source='customer.full_name', read_only=True)
    driver_name = serializers.CharField(source='driver.full_name', read_only=True, allow_null=True)
    merchant_name = serializers.CharField(source='merchant.business_name', read_only=True, allow_null=True)

    class Meta:
        model = Order
        fields = [
            'id', 'order_number', 'customer_name', 'driver_name', 'merchant_name',
            'pickup_address', 'destination_address',
            'parcel_size', 'is_urgent', 'status',
            'total_fee', 'payment_method', 'payment_status',
            'created_at',
        ]


class OrderDetailSerializer(serializers.ModelSerializer):
    """Full order detail serializer."""
    customer_name = serializers.CharField(source='customer.full_name', read_only=True)
    customer_phone = serializers.SerializerMethodField()
    driver_name = serializers.CharField(source='driver.full_name', read_only=True, allow_null=True)
    driver_phone = serializers.SerializerMethodField()
    driver_vehicle = serializers.SerializerMethodField()
    driver_rating = serializers.SerializerMethodField()
    merchant_name = serializers.CharField(source='merchant.business_name', read_only=True, allow_null=True)
    pickup_latitude = serializers.SerializerMethodField()
    pickup_longitude = serializers.SerializerMethodField()
    destination_latitude = serializers.SerializerMethodField()
    destination_longitude = serializers.SerializerMethodField()
    status_history = OrderStatusHistorySerializer(many=True, read_only=True)

    class Meta:
        model = Order
        fields = [
            'id', 'order_number',
            'customer_name', 'customer_phone',
            'driver_name', 'driver_phone', 'driver_vehicle', 'driver_rating',
            'merchant_name',
            'pickup_address', 'pickup_latitude', 'pickup_longitude',
            'destination_address', 'destination_latitude', 'destination_longitude',
            'description', 'parcel_size', 'is_urgent',
            'status', 'otp_verified',
            'base_fee', 'distance_fee', 'urgent_fee', 'parcel_size_fee', 'total_fee',
            'distance_km', 'driver_earnings', 'platform_commission',
            'payment_method', 'payment_status',
            'customer_rating', 'customer_feedback',
            'created_at', 'accepted_at', 'picked_up_at', 'delivered_at', 'cancelled_at',
            'status_history',
        ]

    @extend_schema_field(serializers.CharField())
    def get_customer_phone(self, obj):
        return str(obj.customer.phone)

    @extend_schema_field(serializers.CharField(allow_null=True))
    def get_driver_phone(self, obj):
        if obj.driver:
            return str(obj.driver.phone)
        return None

    @extend_schema_field(serializers.DictField(allow_null=True))
    def get_driver_vehicle(self, obj):
        if obj.driver:
            try:
                dp = obj.driver.driver_profile
                return {
                    'vehicle_type': dp.vehicle_type,
                    'vehicle_model': dp.vehicle_model,
                    'vehicle_plate': dp.vehicle_plate,
                }
            except Exception:
                pass
        return None

    @extend_schema_field(serializers.FloatField(allow_null=True))
    def get_driver_rating(self, obj):
        if obj.driver:
            try:
                return float(obj.driver.driver_profile.rating)
            except Exception:
                pass
        return None

    def get_pickup_latitude(self, obj):
        return obj.pickup_location.y if obj.pickup_location else None

    def get_pickup_longitude(self, obj):
        return obj.pickup_location.x if obj.pickup_location else None

    def get_destination_latitude(self, obj):
        return obj.destination_location.y if obj.destination_location else None

    def get_destination_longitude(self, obj):
        return obj.destination_location.x if obj.destination_location else None


class OrderStatusUpdateSerializer(serializers.Serializer):
    """Serializer for updating order status."""
    status = serializers.ChoiceField(choices=Order.Status.choices)
    note = serializers.CharField(required=False, allow_blank=True)
    latitude = serializers.FloatField(required=False, allow_null=True)
    longitude = serializers.FloatField(required=False, allow_null=True)


class VerifyDeliveryOTPSerializer(serializers.Serializer):
    """Serializer for verifying delivery OTP."""
    otp = serializers.CharField(min_length=4, max_length=4)


class RatingSerializer(serializers.Serializer):
    """Serializer for rating after delivery."""
    rating = serializers.IntegerField(min_value=1, max_value=5)
    feedback = serializers.CharField(required=False, allow_blank=True)


class DeliveryFeeEstimateSerializer(serializers.Serializer):
    """Serializer for fee estimation request."""
    pickup_latitude = serializers.FloatField()
    pickup_longitude = serializers.FloatField()
    destination_latitude = serializers.FloatField()
    destination_longitude = serializers.FloatField()
    parcel_size = serializers.ChoiceField(choices=Order.ParcelSize.choices)
    is_urgent = serializers.BooleanField(default=False)


class DeliveryFeeConfigSerializer(serializers.ModelSerializer):
    """Serializer for delivery fee configuration."""
    class Meta:
        model = DeliveryFeeConfig
        fields = [
            'id', 'base_fee', 'per_km_fee', 'urgent_multiplier',
            'small_parcel_fee', 'medium_parcel_fee', 'large_parcel_fee', 'extra_large_fee',
            'platform_commission_percent', 'is_active',
        ]
