from celery import shared_task
import logging

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=3)
def assign_driver_task(self, order_id):
    from apps.orders.models import Order
    from apps.orders.services import assign_driver_to_order
    try:
        order = Order.objects.get(id=order_id)
        if order.status not in [Order.Status.FINDING_DRIVER, Order.Status.DRIVER_ASSIGNED]:
            return
        assign_driver_to_order(order)
    except Order.DoesNotExist:
        logger.error(f"Order {order_id} not found for driver assignment")
    except Exception as exc:
        logger.error(f"Driver assignment task failed for order {order_id}: {exc}")
        raise self.retry(exc=exc, countdown=30)
