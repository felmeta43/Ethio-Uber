"""
Serializers for the merchants app.
"""
from rest_framework import serializers
from apps.accounts.models import MerchantProfile
from .models import MerchantCategory, MerchantProduct, MerchantOrder, MerchantOrderItem


class MerchantCategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = MerchantCategory
        fields = ['id', 'name', 'name_am', 'icon', 'is_active', 'sort_order']


class MerchantProductSerializer(serializers.ModelSerializer):
    category_name = serializers.CharField(source='category.name', read_only=True)
    merchant_name = serializers.CharField(source='merchant.business_name', read_only=True)

    class Meta:
        model = MerchantProduct
        fields = [
            'id', 'merchant', 'merchant_name', 'category', 'category_name',
            'name', 'name_am', 'description', 'price', 'image',
            'is_available', 'preparation_time_minutes', 'sort_order',
            'created_at', 'updated_at',
        ]
        read_only_fields = ['id', 'merchant', 'created_at', 'updated_at']


class MerchantProductCreateSerializer(serializers.ModelSerializer):
    class Meta:
        model = MerchantProduct
        fields = [
            'category', 'name', 'name_am', 'description', 'price',
            'image', 'is_available', 'preparation_time_minutes', 'sort_order',
        ]

    def create(self, validated_data):
        merchant = self.context['request'].user.merchant_profile
        return MerchantProduct.objects.create(merchant=merchant, **validated_data)


class MerchantOrderItemSerializer(serializers.ModelSerializer):
    product_name = serializers.CharField(source='product.name', read_only=True)
    product_image = serializers.ImageField(source='product.image', read_only=True)

    class Meta:
        model = MerchantOrderItem
        fields = ['id', 'product', 'product_name', 'product_image', 'quantity', 'unit_price', 'subtotal', 'note']
        read_only_fields = ['subtotal']


class MerchantOrderSerializer(serializers.ModelSerializer):
    items = MerchantOrderItemSerializer(many=True, read_only=True)
    order_number = serializers.CharField(source='order.order_number', read_only=True)
    customer_name = serializers.CharField(source='order.customer.full_name', read_only=True)
    customer_phone = serializers.CharField(source='order.customer.phone', read_only=True)

    class Meta:
        model = MerchantOrder
        fields = [
            'id', 'order', 'order_number', 'customer_name', 'customer_phone',
            'merchant', 'status', 'special_instructions', 'estimated_ready_time',
            'subtotal', 'rejection_reason', 'items', 'created_at', 'updated_at',
        ]
        read_only_fields = ['id', 'order', 'merchant', 'subtotal', 'created_at', 'updated_at']


class MerchantOrderCreateSerializer(serializers.Serializer):
    """Customer creates a merchant order (items + delivery)."""
    merchant_id = serializers.IntegerField()
    items = serializers.ListField(
        child=serializers.DictField(),
        min_length=1,
    )
    special_instructions = serializers.CharField(required=False, allow_blank=True, default='')
    pickup_address = serializers.CharField()
    destination_address = serializers.CharField()
    pickup_lat = serializers.FloatField()
    pickup_lng = serializers.FloatField()
    destination_lat = serializers.FloatField()
    destination_lng = serializers.FloatField()
    payment_method = serializers.ChoiceField(choices=['TELEBIRR', 'CBE_BIRR', 'CHAPA', 'CASH', 'WALLET'])

    def validate_items(self, items):
        for item in items:
            if 'product_id' not in item or 'quantity' not in item:
                raise serializers.ValidationError("Each item must have product_id and quantity.")
            try:
                MerchantProduct.objects.get(id=item['product_id'], is_available=True)
            except MerchantProduct.DoesNotExist:
                raise serializers.ValidationError(f"Product {item['product_id']} not available.")
        return items


class MerchantProfilePublicSerializer(serializers.ModelSerializer):
    """Public-facing merchant info for customers browsing merchants."""
    products = MerchantProductSerializer(many=True, read_only=True)
    business_type_display = serializers.CharField(source='get_business_type_display', read_only=True)

    class Meta:
        model = MerchantProfile
        fields = [
            'id', 'business_name', 'business_type', 'business_type_display',
            'business_address', 'is_open', 'rating', 'total_orders',
            'phone', 'description', 'products',
        ]


class MerchantStatusUpdateSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=['ACCEPTED', 'REJECTED', 'PREPARING', 'READY'])
    rejection_reason = serializers.CharField(required=False, allow_blank=True, default='')
    estimated_ready_minutes = serializers.IntegerField(required=False, min_value=1)
