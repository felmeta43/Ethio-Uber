"""
WebSocket consumers for real-time order tracking and driver availability.
"""
import json
import logging
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser

logger = logging.getLogger(__name__)


class OrderTrackingConsumer(AsyncJsonWebsocketConsumer):
    """
    WebSocket consumer for real-time order tracking.

    Customers connect to: ws://host/ws/tracking/{order_id}/?token=<jwt>
    Drivers send location updates, customers receive them in real-time.

    Message format from driver:
        {
            "type": "location_update",
            "lat": 7.0621,
            "lng": 38.7468,
            "heading": 180.0,
            "speed": 30.5,
            "accuracy": 5.0
        }

    Message received by customer:
        {
            "type": "location_update",
            "lat": 7.0621,
            "lng": 38.7468,
            "heading": 180.0,
            "speed": 30.5,
            "driver_name": "Abebe Kebede",
            "order_status": "IN_TRANSIT"
        }
    """

    async def connect(self):
        """Authenticate and join order tracking group."""
        user = self.scope.get('user')

        if not user or isinstance(user, AnonymousUser) or not user.is_authenticated:
            await self.close(code=4001)
            return

        self.order_id = self.scope['url_route']['kwargs']['order_id']
        self.group_name = f'order_{self.order_id}'

        # Verify user has access to this order
        has_access = await self._check_order_access(user, self.order_id)
        if not has_access:
            await self.close(code=4003)
            return

        # Join the order's tracking group
        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()

        # Send current order status on connect
        order_data = await self._get_order_status(self.order_id)
        if order_data:
            await self.send_json({
                'type': 'order_status',
                'data': order_data,
            })

        logger.info(f"User {user.id} connected to order {self.order_id} tracking")

    async def disconnect(self, close_code):
        """Leave the order tracking group."""
        if hasattr(self, 'group_name'):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)

    async def receive_json(self, content):
        """Handle incoming messages from WebSocket."""
        user = self.scope.get('user')
        if not user or isinstance(user, AnonymousUser):
            return

        message_type = content.get('type')

        if message_type == 'location_update':
            await self._handle_location_update(user, content)
        elif message_type == 'ping':
            await self.send_json({'type': 'pong'})

    async def _handle_location_update(self, user, content):
        """Process a driver location update."""
        lat = content.get('lat')
        lng = content.get('lng')
        heading = content.get('heading')
        speed = content.get('speed')
        accuracy = content.get('accuracy')

        if lat is None or lng is None:
            await self.send_json({'type': 'error', 'message': 'lat and lng are required'})
            return

        # Only drivers can send location updates
        if user.user_type != 'DRIVER':
            await self.send_json({'type': 'error', 'message': 'Only drivers can send location updates'})
            return

        # Save location to DB and update driver profile
        order_status = await self._save_driver_location(user, lat, lng, heading, speed, accuracy, self.order_id)

        # Broadcast to all in the group
        await self.channel_layer.group_send(
            self.group_name,
            {
                'type': 'location_update',
                'lat': lat,
                'lng': lng,
                'heading': heading,
                'speed': speed,
                'driver_id': user.id,
                'driver_name': user.full_name,
                'order_status': order_status,
            }
        )

    async def location_update(self, event):
        """Receive location update from channel layer and send to WebSocket."""
        await self.send_json({
            'type': 'location_update',
            'lat': event.get('lat'),
            'lng': event.get('lng'),
            'heading': event.get('heading'),
            'speed': event.get('speed'),
            'driver_name': event.get('driver_name'),
            'order_status': event.get('order_status'),
        })

    async def order_status_update(self, event):
        """Receive order status update and forward to WebSocket."""
        await self.send_json({
            'type': 'order_status_update',
            'status': event.get('status'),
            'message': event.get('message', ''),
        })

    @database_sync_to_async
    def _check_order_access(self, user, order_id):
        """Check if user has permission to track this order."""
        from apps.orders.models import Order
        try:
            if user.user_type == 'CUSTOMER':
                return Order.objects.filter(id=order_id, customer=user).exists()
            elif user.user_type == 'DRIVER':
                return Order.objects.filter(id=order_id, driver=user).exists()
            elif user.user_type in ['ADMIN', 'MERCHANT']:
                return Order.objects.filter(id=order_id).exists()
            return False
        except Exception:
            return False

    @database_sync_to_async
    def _get_order_status(self, order_id):
        """Get current order status and driver location."""
        from apps.orders.models import Order
        try:
            order = Order.objects.select_related('driver').get(id=order_id)
            data = {
                'order_number': order.order_number,
                'status': order.status,
                'driver_name': order.driver.full_name if order.driver else None,
            }
            if order.driver:
                try:
                    dp = order.driver.driver_profile
                    if dp.current_location:
                        data['driver_lat'] = dp.current_location.y
                        data['driver_lng'] = dp.current_location.x
                except Exception:
                    pass
            return data
        except Exception:
            return None

    @database_sync_to_async
    def _save_driver_location(self, user, lat, lng, heading, speed, accuracy, order_id):
        """Save driver location to DB and update driver profile."""
        from django.contrib.gis.geos import Point
        from apps.tracking.models import DriverLocationHistory
        from apps.orders.models import Order

        point = Point(float(lng), float(lat), srid=4326)

        # Update driver's current location
        try:
            dp = user.driver_profile
            dp.current_location = point
            dp.save(update_fields=['current_location'])
        except Exception as e:
            logger.error(f"Error updating driver location: {e}")

        # Save to history
        order = None
        order_status = None
        try:
            order = Order.objects.get(id=order_id)
            order_status = order.status
        except Order.DoesNotExist:
            pass

        DriverLocationHistory.objects.create(
            driver=user,
            location=point,
            order=order,
            speed=speed,
            heading=heading,
            accuracy=accuracy,
        )

        return order_status


class DriverAvailabilityConsumer(AsyncJsonWebsocketConsumer):
    """
    WebSocket consumer for drivers to receive new order requests in real-time.

    Drivers connect to: ws://host/ws/driver/availability/?token=<jwt>
    System sends new order requests, driver accepts/rejects via WebSocket.

    Message from server (new order):
        {
            "type": "new_order",
            "order_id": 123,
            "order_number": "EU-20241215-0001",
            "pickup_address": "Shashemene Market",
            "destination_address": "Adama Road",
            "total_fee": "85.00",
            "distance_km": "3.2",
            "parcel_size": "MEDIUM",
            "is_urgent": false,
            "pickup_lat": 7.0621,
            "pickup_lng": 38.7468
        }

    Message from driver (accept/reject):
        {"type": "accept_order", "order_id": 123}
        {"type": "reject_order", "order_id": 123}
    """

    async def connect(self):
        user = self.scope.get('user')

        if not user or isinstance(user, AnonymousUser) or not user.is_authenticated:
            await self.close(code=4001)
            return

        if user.user_type != 'DRIVER':
            await self.close(code=4003)
            return

        self.driver_id = user.id
        self.group_name = f'driver_{self.driver_id}'

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()

        # Mark driver as online
        await self._set_driver_online(user, True)
        logger.info(f"Driver {user.id} connected to availability channel")

    async def disconnect(self, close_code):
        user = self.scope.get('user')
        if user and not isinstance(user, AnonymousUser):
            await self._set_driver_online(user, False)

        if hasattr(self, 'group_name'):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)

    async def receive_json(self, content):
        user = self.scope.get('user')
        if not user or isinstance(user, AnonymousUser):
            return

        message_type = content.get('type')

        if message_type == 'accept_order':
            await self._handle_accept_order(user, content.get('order_id'))
        elif message_type == 'reject_order':
            await self._handle_reject_order(user, content.get('order_id'))
        elif message_type == 'location_update':
            lat = content.get('lat')
            lng = content.get('lng')
            if lat and lng:
                await self._update_driver_location(user, lat, lng)
        elif message_type == 'ping':
            await self.send_json({'type': 'pong'})

    async def new_order(self, event):
        """Receive new order notification from channel layer."""
        await self.send_json({
            'type': 'new_order',
            'order_id': event.get('order_id'),
            'order_number': event.get('order_number'),
            'pickup_address': event.get('pickup_address'),
            'destination_address': event.get('destination_address'),
            'total_fee': event.get('total_fee'),
            'distance_km': event.get('distance_km'),
            'parcel_size': event.get('parcel_size'),
            'is_urgent': event.get('is_urgent'),
            'pickup_lat': event.get('pickup_lat'),
            'pickup_lng': event.get('pickup_lng'),
        })

    async def order_cancelled(self, event):
        """Notify driver that an order was cancelled."""
        await self.send_json({
            'type': 'order_cancelled',
            'order_id': event.get('order_id'),
            'order_number': event.get('order_number'),
            'message': 'The order has been cancelled.',
        })

    @database_sync_to_async
    def _handle_accept_order(self, user, order_id):
        """Handle driver accepting an order via WebSocket."""
        from apps.orders.models import Order
        try:
            order = Order.objects.get(
                id=order_id,
                driver=user,
                status=Order.Status.DRIVER_ASSIGNED
            )
            order.update_status(Order.Status.DRIVER_ON_WAY, note="Driver accepted via WebSocket")
            logger.info(f"Driver {user.id} accepted order {order_id}")
        except Order.DoesNotExist:
            logger.warning(f"Driver {user.id} tried to accept non-existent/invalid order {order_id}")

    @database_sync_to_async
    def _handle_reject_order(self, user, order_id):
        """Handle driver rejecting an order via WebSocket."""
        from apps.orders.models import Order
        from apps.orders.services import assign_driver_to_order
        try:
            order = Order.objects.get(
                id=order_id,
                driver=user,
                status=Order.Status.DRIVER_ASSIGNED
            )
            if order.rejected_driver_ids is None:
                order.rejected_driver_ids = []
            order.rejected_driver_ids.append(user.id)
            order.driver = None
            order.save()
            order.update_status(Order.Status.FINDING_DRIVER, note="Driver rejected via WebSocket")
            assign_driver_to_order(order)
        except Order.DoesNotExist:
            pass

    @database_sync_to_async
    def _set_driver_online(self, user, is_online):
        """Toggle driver online status."""
        try:
            dp = user.driver_profile
            dp.is_online = is_online
            dp.save(update_fields=['is_online'])
        except Exception as e:
            logger.error(f"Error setting driver online status: {e}")

    @database_sync_to_async
    def _update_driver_location(self, user, lat, lng):
        """Update driver's current location."""
        from django.contrib.gis.geos import Point
        try:
            dp = user.driver_profile
            dp.current_location = Point(float(lng), float(lat), srid=4326)
            dp.save(update_fields=['current_location'])
        except Exception as e:
            logger.error(f"Error updating driver location: {e}")
