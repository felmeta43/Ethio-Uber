"""
Payment views for Ethio-Uber.
Handles payment initiation, callbacks, wallet operations, and withdrawals.
"""
import logging
import uuid
from django.conf import settings
from django.utils import timezone
from django.db import transaction
from rest_framework import generics, status, permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.permissions import IsCustomer, IsDriver, IsAdmin
from apps.orders.models import Order
from .models import Wallet, WalletTransaction, Payment, WithdrawalRequest
from .serializers import (
    WalletSerializer, WalletTransactionSerializer, PaymentSerializer,
    InitiatePaymentSerializer, WalletTopUpSerializer,
    WithdrawalRequestSerializer, WithdrawalStatusUpdateSerializer,
    PaymentVerifySerializer,
)
from .gateways.chapa import ChapaGateway
from .gateways.telebirr import TelebirrGateway
from .gateways.cbe_birr import CBEBirrGateway

logger = logging.getLogger(__name__)

GATEWAY_MAP = {
    'CHAPA': ChapaGateway,
    'TELEBIRR': TelebirrGateway,
    'CBE_BIRR': CBEBirrGateway,
}


def _get_or_create_wallet(user):
    wallet, _ = Wallet.objects.get_or_create(user=user)
    return wallet


def _build_callback_url(request, path):
    return request.build_absolute_uri(path)


class WalletBalanceView(APIView):
    """GET /payments/wallet/ — current user's wallet balance."""

    def get(self, request):
        wallet = _get_or_create_wallet(request.user)
        serializer = WalletSerializer(wallet)
        return Response(serializer.data)


class WalletTransactionHistoryView(generics.ListAPIView):
    """GET /payments/wallet/transactions/ — paginated transaction history."""
    serializer_class = WalletTransactionSerializer

    def get_queryset(self):
        wallet = _get_or_create_wallet(self.request.user)
        return WalletTransaction.objects.filter(wallet=wallet).order_by('-timestamp')


class InitiatePaymentView(APIView):
    """
    POST /payments/initiate/
    Customer initiates payment for an order via chosen gateway.
    """
    permission_classes = [permissions.IsAuthenticated, IsCustomer]

    def post(self, request):
        serializer = InitiatePaymentSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)

        order = Order.objects.get(id=serializer.validated_data['order_id'])
        method = serializer.validated_data['method']
        return_url = serializer.validated_data.get('return_url', '')

        # Handle wallet payment directly
        if method == 'WALLET':
            return self._pay_with_wallet(request, order)

        # Handle cash (mark as COD)
        if method == 'CASH':
            order.payment_method = 'CASH'
            order.payment_status = 'PENDING'
            order.save(update_fields=['payment_method', 'payment_status'])
            return Response({'message': 'Cash on delivery selected.', 'order_number': order.order_number})

        # External gateway
        gateway_cls = GATEWAY_MAP.get(method)
        if not gateway_cls:
            return Response({'error': 'Invalid payment method'}, status=status.HTTP_400_BAD_REQUEST)

        tx_ref = f"EU-{uuid.uuid4().hex[:12].upper()}"
        callback_url = _build_callback_url(request, f'/api/v1/payments/callback/{method.lower()}/')

        gateway = gateway_cls()
        result = gateway.initiate_payment(
            amount=float(order.total_fee),
            tx_ref=tx_ref,
            customer_phone=str(request.user.phone),
            customer_name=request.user.full_name,
            customer_email=request.user.email,
            description=f'Ethio-Uber delivery #{order.order_number}',
            callback_url=callback_url,
            return_url=return_url,
        )

        if result.success:
            Payment.objects.create(
                order=order,
                amount=order.total_fee,
                method=method,
                status='PROCESSING',
                gateway_reference=tx_ref,
                gateway_response=result.raw_response or {},
                checkout_url=result.checkout_url or '',
            )
            order.payment_method = method
            order.save(update_fields=['payment_method'])

            return Response({
                'checkout_url': result.checkout_url,
                'tx_ref': tx_ref,
                'order_number': order.order_number,
                'amount': float(order.total_fee),
            })
        else:
            return Response({'error': result.message}, status=status.HTTP_502_BAD_GATEWAY)

    def _pay_with_wallet(self, request, order):
        try:
            wallet = _get_or_create_wallet(request.user)
            with transaction.atomic():
                wallet.debit(
                    order.total_fee,
                    purpose=WalletTransaction.Purpose.ORDER_PAYMENT,
                    order=order,
                )
                order.payment_method = 'WALLET'
                order.payment_status = 'PAID'
                order.save(update_fields=['payment_method', 'payment_status'])

                Payment.objects.create(
                    order=order,
                    amount=order.total_fee,
                    method='WALLET',
                    status='COMPLETED',
                    gateway_reference=f'WALLET-{uuid.uuid4().hex[:8].upper()}',
                    completed_at=timezone.now(),
                )
                self._credit_driver_earnings(order)

            return Response({'message': 'Payment successful via wallet.', 'order_number': order.order_number})
        except ValueError as e:
            return Response({'error': str(e)}, status=status.HTTP_400_BAD_REQUEST)

    def _credit_driver_earnings(self, order):
        if not order.driver:
            return
        try:
            config = order.delivery_fee_config if hasattr(order, 'delivery_fee_config') else None
            from apps.orders.models import DeliveryFeeConfig
            fee_config = DeliveryFeeConfig.get_active()
            commission = float(order.total_fee) * float(fee_config.platform_commission_percent) / 100
            driver_earnings = float(order.total_fee) - commission

            driver_wallet = _get_or_create_wallet(order.driver)
            driver_wallet.credit(
                driver_earnings,
                purpose=WalletTransaction.Purpose.DRIVER_EARNINGS,
                order=order,
            )
            order.driver.driver_profile.total_earnings += driver_earnings
            order.driver.driver_profile.save(update_fields=['total_earnings'])
        except Exception as e:
            logger.error(f"Error crediting driver earnings for order {order.id}: {e}")


class ChapaCallbackView(APIView):
    """
    POST /payments/callback/chapa/
    Webhook from Chapa after payment is completed.
    """
    permission_classes = []
    authentication_classes = []

    def post(self, request):
        import hmac
        import hashlib

        secret = settings.CHAPA_WEBHOOK_SECRET
        signature = request.headers.get('Chapa-Signature', '')
        payload = request.body

        if secret:
            expected = hmac.new(secret.encode(), payload, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, signature):
                return Response({'error': 'Invalid signature'}, status=status.HTTP_400_BAD_REQUEST)

        tx_ref = request.data.get('trx_ref') or request.data.get('tx_ref', '')
        event_type = request.data.get('event', '')

        if event_type == 'charge.success':
            self._handle_success(tx_ref, request.data)

        return Response({'status': 'received'})

    def _handle_success(self, tx_ref, data):
        try:
            payment = Payment.objects.get(gateway_reference=tx_ref)
            if payment.status == 'COMPLETED':
                return

            with transaction.atomic():
                payment.status = 'COMPLETED'
                payment.gateway_response = data
                payment.completed_at = timezone.now()
                payment.save()

                order = payment.order
                order.payment_status = 'PAID'
                order.save(update_fields=['payment_status'])

                InitiatePaymentView()._credit_driver_earnings(order)
        except Payment.DoesNotExist:
            logger.warning(f"Chapa callback for unknown tx_ref: {tx_ref}")
        except Exception as e:
            logger.error(f"Chapa callback error: {e}")


class TelebirrCallbackView(APIView):
    """POST /payments/callback/telebirr/ — Telebirr payment notification."""
    permission_classes = []
    authentication_classes = []

    def post(self, request):
        trade_status = request.data.get('tradeStatus', '')
        tx_ref = request.data.get('outTradeNo', '')

        if trade_status == 'TRADE_SUCCESS' and tx_ref:
            try:
                payment = Payment.objects.get(gateway_reference=tx_ref)
                if payment.status != 'COMPLETED':
                    payment.status = 'COMPLETED'
                    payment.gateway_response = request.data
                    payment.completed_at = timezone.now()
                    payment.save()
                    payment.order.payment_status = 'PAID'
                    payment.order.save(update_fields=['payment_status'])
                    InitiatePaymentView()._credit_driver_earnings(payment.order)
            except Payment.DoesNotExist:
                logger.warning(f"Telebirr callback for unknown tx_ref: {tx_ref}")

        return Response({'code': '0', 'msg': 'success'})


class CBEBirrCallbackView(APIView):
    """POST /payments/callback/cbe_birr/ — CBE Birr payment notification."""
    permission_classes = []
    authentication_classes = []

    def post(self, request):
        tx_ref = request.data.get('reference', '')
        pay_status = request.data.get('status', '').upper()

        if pay_status == 'SUCCESS' and tx_ref:
            try:
                payment = Payment.objects.get(gateway_reference=tx_ref)
                if payment.status != 'COMPLETED':
                    payment.status = 'COMPLETED'
                    payment.gateway_response = request.data
                    payment.completed_at = timezone.now()
                    payment.save()
                    payment.order.payment_status = 'PAID'
                    payment.order.save(update_fields=['payment_status'])
                    InitiatePaymentView()._credit_driver_earnings(payment.order)
            except Payment.DoesNotExist:
                logger.warning(f"CBE Birr callback for unknown tx_ref: {tx_ref}")

        return Response({'status': 'received'})


class VerifyPaymentView(APIView):
    """POST /payments/verify/ — manually verify a payment status from gateway."""

    def post(self, request):
        serializer = PaymentVerifySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        tx_ref = serializer.validated_data['tx_ref']

        try:
            payment = Payment.objects.get(gateway_reference=tx_ref)
            if payment.order.customer != request.user:
                return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)
        except Payment.DoesNotExist:
            return Response({'error': 'Payment not found'}, status=status.HTTP_404_NOT_FOUND)

        gateway_cls = GATEWAY_MAP.get(payment.method)
        if not gateway_cls:
            return Response({'status': payment.status})

        result = gateway_cls().verify_payment(tx_ref)
        if result.success and payment.status != 'COMPLETED':
            payment.status = 'COMPLETED'
            payment.completed_at = timezone.now()
            payment.save()
            payment.order.payment_status = 'PAID'
            payment.order.save(update_fields=['payment_status'])
            InitiatePaymentView()._credit_driver_earnings(payment.order)

        return Response({'status': payment.status, 'verified': result.success})


class WalletTopUpView(APIView):
    """POST /payments/wallet/topup/ — customer tops up wallet via gateway."""
    permission_classes = [permissions.IsAuthenticated, IsCustomer]

    def post(self, request):
        serializer = WalletTopUpSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        amount = serializer.validated_data['amount']
        method = serializer.validated_data['method']
        return_url = serializer.validated_data.get('return_url', '')

        gateway_cls = GATEWAY_MAP.get(method)
        if not gateway_cls:
            return Response({'error': 'Invalid method'}, status=status.HTTP_400_BAD_REQUEST)

        tx_ref = f"TOPUP-{uuid.uuid4().hex[:12].upper()}"
        callback_url = request.build_absolute_uri('/api/v1/payments/wallet/topup/callback/')

        gateway = gateway_cls()
        result = gateway.initiate_payment(
            amount=float(amount),
            tx_ref=tx_ref,
            customer_phone=str(request.user.phone),
            customer_name=request.user.full_name,
            description='Ethio-Uber Wallet Top-Up',
            callback_url=callback_url,
            return_url=return_url,
        )

        if result.success:
            # Store top-up intent in cache
            from django.core.cache import cache
            cache.set(f'topup_{tx_ref}', {
                'user_id': request.user.id,
                'amount': str(amount),
                'method': method,
            }, timeout=3600)

            return Response({
                'checkout_url': result.checkout_url,
                'tx_ref': tx_ref,
                'amount': float(amount),
            })
        else:
            return Response({'error': result.message}, status=status.HTTP_502_BAD_GATEWAY)


class WalletTopUpCallbackView(APIView):
    """POST /payments/wallet/topup/callback/ — gateway callback for wallet top-up."""
    permission_classes = []
    authentication_classes = []

    def post(self, request):
        tx_ref = request.data.get('tx_ref') or request.data.get('trx_ref', '')
        from django.core.cache import cache
        topup_data = cache.get(f'topup_{tx_ref}')

        if not topup_data:
            return Response({'error': 'Unknown top-up reference'}, status=status.HTTP_400_BAD_REQUEST)

        from apps.accounts.models import User
        try:
            user = User.objects.get(id=topup_data['user_id'])
            wallet = _get_or_create_wallet(user)
            wallet.credit(
                topup_data['amount'],
                purpose=WalletTransaction.Purpose.TOP_UP,
                reference=tx_ref,
            )
            cache.delete(f'topup_{tx_ref}')
        except Exception as e:
            logger.error(f"Wallet top-up error: {e}")

        return Response({'status': 'received'})


class WithdrawalRequestListCreateView(generics.ListCreateAPIView):
    """
    GET  /payments/withdrawals/        — driver lists their withdrawal requests
    POST /payments/withdrawals/        — driver creates a withdrawal request
    """
    serializer_class = WithdrawalRequestSerializer
    permission_classes = [permissions.IsAuthenticated, IsDriver]

    def get_queryset(self):
        return WithdrawalRequest.objects.filter(driver=self.request.user)

    def perform_create(self, serializer):
        with transaction.atomic():
            amount = serializer.validated_data['amount']
            wallet = _get_or_create_wallet(self.request.user)
            wallet.debit(
                amount,
                purpose=WalletTransaction.Purpose.WITHDRAWAL,
            )
            serializer.save(driver=self.request.user)


class WithdrawalAdminListView(generics.ListAPIView):
    """GET /payments/withdrawals/all/ — admin views all withdrawal requests."""
    serializer_class = WithdrawalRequestSerializer
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get_queryset(self):
        status_filter = self.request.query_params.get('status', '')
        qs = WithdrawalRequest.objects.all().select_related('driver')
        if status_filter:
            qs = qs.filter(status=status_filter)
        return qs


class WithdrawalStatusUpdateView(APIView):
    """PATCH /payments/withdrawals/{id}/status/ — admin approves/rejects withdrawal."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def patch(self, request, pk):
        try:
            withdrawal = WithdrawalRequest.objects.get(pk=pk)
        except WithdrawalRequest.DoesNotExist:
            return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)

        serializer = WithdrawalStatusUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        new_status = serializer.validated_data['status']
        withdrawal.status = new_status
        withdrawal.admin_note = serializer.validated_data.get('admin_note', '')

        if new_status == 'PROCESSED':
            withdrawal.processed_at = timezone.now()
        elif new_status == 'REJECTED':
            # Refund to wallet
            wallet = _get_or_create_wallet(withdrawal.driver)
            wallet.credit(
                withdrawal.amount,
                purpose=WalletTransaction.Purpose.REFUND,
                reference=f'WITHDRAWAL-REJECT-{withdrawal.id}',
            )

        withdrawal.save()
        return Response(WithdrawalRequestSerializer(withdrawal).data)


class PaymentHistoryView(generics.ListAPIView):
    """GET /payments/history/ — user's payment history."""
    serializer_class = PaymentSerializer

    def get_queryset(self):
        user = self.request.user
        return Payment.objects.filter(order__customer=user).select_related('order').order_by('-created_at')
