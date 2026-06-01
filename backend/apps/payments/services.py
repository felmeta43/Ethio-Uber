"""
Payment service functions for Ethio-Uber.
"""
import logging
from django.utils import timezone

logger = logging.getLogger(__name__)


def process_refund(order):
    """
    Refund payment for a cancelled order.
    If paid via wallet, credits back to customer wallet.
    For gateway payments, marks for manual refund (gateway refunds vary by provider).
    """
    from .models import Payment, Wallet, WalletTransaction

    payments = Payment.objects.filter(order=order, status='COMPLETED')
    if not payments.exists():
        logger.info(f"No completed payments to refund for order {order.order_number}")
        return

    for payment in payments:
        if payment.method == 'WALLET':
            # Direct wallet refund
            try:
                wallet, _ = Wallet.objects.get_or_create(user=order.customer)
                wallet.credit(
                    payment.amount,
                    purpose=WalletTransaction.Purpose.REFUND,
                    reference=f'REFUND-{payment.gateway_reference}',
                    order=order,
                )
                payment.status = 'REFUNDED'
                payment.gateway_response['refunded_at'] = timezone.now().isoformat()
                payment.save()
                logger.info(f"Wallet refund processed for order {order.order_number}")
            except Exception as e:
                logger.error(f"Wallet refund failed for order {order.order_number}: {e}")
        else:
            # For gateway payments, flag for admin manual processing
            payment.gateway_response['refund_requested_at'] = timezone.now().isoformat()
            payment.gateway_response['refund_status'] = 'PENDING_MANUAL'
            payment.status = 'REFUNDED'
            payment.save()
            logger.info(
                f"Payment {payment.gateway_reference} flagged for manual refund "
                f"(method: {payment.method}, amount: {payment.amount} ETB)"
            )
