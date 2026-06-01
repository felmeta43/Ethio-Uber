"""
Serializers for the payments app.
"""
from decimal import Decimal
from rest_framework import serializers
from .models import Wallet, WalletTransaction, Payment, WithdrawalRequest


class WalletSerializer(serializers.ModelSerializer):
    user_name = serializers.CharField(source='user.full_name', read_only=True)
    user_phone = serializers.CharField(source='user.phone', read_only=True)

    class Meta:
        model = Wallet
        fields = ['id', 'user_name', 'user_phone', 'balance', 'currency', 'is_active', 'updated_at']
        read_only_fields = ['balance', 'currency', 'updated_at']


class WalletTransactionSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source='order.order_number', read_only=True, allow_null=True)

    class Meta:
        model = WalletTransaction
        fields = [
            'id', 'transaction_type', 'amount', 'purpose',
            'reference', 'order_number', 'timestamp', 'balance_after', 'note'
        ]
        read_only_fields = fields


class PaymentSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source='order.order_number', read_only=True)

    class Meta:
        model = Payment
        fields = [
            'id', 'order', 'order_number', 'amount', 'method',
            'status', 'gateway_reference', 'checkout_url',
            'created_at', 'updated_at', 'completed_at'
        ]
        read_only_fields = [
            'id', 'status', 'gateway_reference', 'gateway_response',
            'checkout_url', 'created_at', 'updated_at', 'completed_at'
        ]


class InitiatePaymentSerializer(serializers.Serializer):
    order_id = serializers.IntegerField()
    method = serializers.ChoiceField(choices=['TELEBIRR', 'CBE_BIRR', 'CHAPA', 'CASH', 'WALLET'])
    return_url = serializers.URLField(required=False, allow_blank=True, default='')

    def validate_order_id(self, value):
        from apps.orders.models import Order
        try:
            order = Order.objects.get(id=value)
            request = self.context.get('request')
            if request and order.customer != request.user:
                raise serializers.ValidationError("Order not found.")
            if order.payment_status == 'PAID':
                raise serializers.ValidationError("Order already paid.")
            self._order = order
            return value
        except Order.DoesNotExist:
            raise serializers.ValidationError("Order not found.")


class WalletTopUpSerializer(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=10, decimal_places=2, min_value=Decimal('10'))
    method = serializers.ChoiceField(choices=['TELEBIRR', 'CBE_BIRR', 'CHAPA'])
    return_url = serializers.URLField(required=False, allow_blank=True, default='')


class WithdrawalRequestSerializer(serializers.ModelSerializer):
    driver_name = serializers.CharField(source='driver.full_name', read_only=True)
    driver_phone = serializers.CharField(source='driver.phone', read_only=True)

    class Meta:
        model = WithdrawalRequest
        fields = [
            'id', 'driver', 'driver_name', 'driver_phone',
            'amount', 'bank_name', 'account_number', 'account_name',
            'status', 'processed_at', 'admin_note', 'created_at'
        ]
        read_only_fields = ['id', 'driver', 'status', 'processed_at', 'admin_note', 'created_at']

    def validate_amount(self, value):
        request = self.context.get('request')
        if request and request.user:
            try:
                wallet = request.user.wallet
                if wallet.balance < value:
                    raise serializers.ValidationError(
                        f"Insufficient balance. Available: {wallet.balance} ETB"
                    )
            except Wallet.DoesNotExist:
                raise serializers.ValidationError("No wallet found.")
        return value


class WithdrawalStatusUpdateSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=['APPROVED', 'REJECTED', 'PROCESSED'])
    admin_note = serializers.CharField(required=False, allow_blank=True, default='')


class PaymentVerifySerializer(serializers.Serializer):
    tx_ref = serializers.CharField(max_length=255)
