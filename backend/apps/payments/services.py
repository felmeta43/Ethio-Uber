"""
Payment service functions for Ethio-Uber.
"""
import logging
from django.utils import timezone

logger = logging.getLogger(__name__)


def credit_driver_earnings(order):
    """
    Credit a driver's wallet with their earnings after a successful delivery.
    Commission is deducted before crediting. Idempotent — safe to call multiple times.
    """
    from .models import Wallet, WalletTransaction
    from apps.orders.models import DeliveryFeeConfig

    if not order.driver:
        logger.warning(f"Cannot credit earnings: no driver assigned to order {order.order_number}")
        return

    # Avoid double-crediting
    already_credited = WalletTransaction.objects.filter(
        purpose=WalletTransaction.Purpose.DRIVER_EARNINGS,
        order=order,
    ).exists()
    if already_credited:
        logger.info(f"Driver earnings already credited for order {order.order_number}")
        return

    try:
        driver_wallet, _ = Wallet.objects.get_or_create(user=order.driver)
        # Use pre-computed driver_earnings on the order (set at fee calculation time)
        earnings = order.driver_earnings
        driver_wallet.credit(
            earnings,
            purpose=WalletTransaction.Purpose.DRIVER_EARNINGS,
            reference=f'EARN-{order.order_number}',
            order=order,
        )
        logger.info(f"Credited {earnings} ETB to driver {order.driver.full_name} for order {order.order_number}")
    except Exception as e:
        logger.error(f"Failed to credit driver earnings for order {order.order_number}: {e}")


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
