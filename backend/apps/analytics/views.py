"""
Analytics views for Ethio-Uber admin dashboard.
Provides statistics, reports, and heatmap data.
"""
from datetime import timedelta
from django.db.models import Count, Sum, Avg, Q
from django.db.models.functions import TruncDate, TruncMonth
from django.utils import timezone
from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.models import User, DriverProfile
from apps.accounts.permissions import IsAdmin
from apps.orders.models import Order
from apps.payments.models import Payment, WalletTransaction


class DashboardStatsView(APIView):
    """GET /analytics/dashboard/ — high-level KPIs for admin dashboard."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        now = timezone.now()
        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        week_start = today_start - timedelta(days=today_start.weekday())
        month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)

        total_customers = User.objects.filter(user_type='CUSTOMER', is_active=True).count()
        total_drivers = User.objects.filter(user_type='DRIVER', is_active=True).count()
        total_merchants = User.objects.filter(user_type='MERCHANT', is_active=True).count()
        pending_driver_approvals = DriverProfile.objects.filter(is_approved=False).count()

        total_orders = Order.objects.count()
        orders_today = Order.objects.filter(created_at__gte=today_start).count()
        orders_this_week = Order.objects.filter(created_at__gte=week_start).count()
        orders_this_month = Order.objects.filter(created_at__gte=month_start).count()
        active_orders = Order.objects.filter(
            status__in=['FINDING_DRIVER', 'DRIVER_ASSIGNED', 'DRIVER_ON_WAY',
                        'ARRIVED_AT_PICKUP', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED_AT_DESTINATION']
        ).count()

        rev_today = Payment.objects.filter(
            status='COMPLETED', completed_at__gte=today_start
        ).aggregate(total=Sum('amount'))['total'] or 0

        rev_month = Payment.objects.filter(
            status='COMPLETED', completed_at__gte=month_start
        ).aggregate(total=Sum('amount'))['total'] or 0

        online_drivers = DriverProfile.objects.filter(is_online=True, is_approved=True).count()

        return Response({
            'users': {
                'total_customers': total_customers,
                'total_drivers': total_drivers,
                'total_merchants': total_merchants,
                'pending_driver_approvals': pending_driver_approvals,
                'online_drivers': online_drivers,
            },
            'orders': {
                'total': total_orders,
                'today': orders_today,
                'this_week': orders_this_week,
                'this_month': orders_this_month,
                'active_now': active_orders,
            },
            'revenue': {
                'today_etb': float(rev_today),
                'this_month_etb': float(rev_month),
            },
        })


class OrderAnalyticsView(APIView):
    """GET /analytics/orders/ — order trends and status breakdown."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        days = int(request.query_params.get('days', 30))
        since = timezone.now() - timedelta(days=days)

        # Orders per day
        daily_orders = (
            Order.objects.filter(created_at__gte=since)
            .annotate(date=TruncDate('created_at'))
            .values('date')
            .annotate(count=Count('id'))
            .order_by('date')
        )

        # Status breakdown
        status_breakdown = (
            Order.objects.values('status')
            .annotate(count=Count('id'))
            .order_by('-count')
        )

        # Average delivery time (delivered orders)
        delivered = Order.objects.filter(status='DELIVERED', delivered_at__isnull=False)
        avg_delivery_time = None
        if delivered.exists():
            total_seconds = sum(
                (o.delivered_at - o.accepted_at).total_seconds()
                for o in delivered.filter(accepted_at__isnull=False)
            )
            count = delivered.filter(accepted_at__isnull=False).count()
            avg_delivery_time = round(total_seconds / count / 60, 1) if count else None

        return Response({
            'daily_orders': list(daily_orders),
            'status_breakdown': list(status_breakdown),
            'avg_delivery_time_minutes': avg_delivery_time,
            'completion_rate': self._completion_rate(),
            'cancellation_rate': self._cancellation_rate(),
        })

    def _completion_rate(self):
        total = Order.objects.count()
        if not total:
            return 0
        delivered = Order.objects.filter(status='DELIVERED').count()
        return round(delivered / total * 100, 1)

    def _cancellation_rate(self):
        total = Order.objects.count()
        if not total:
            return 0
        cancelled = Order.objects.filter(status='CANCELLED').count()
        return round(cancelled / total * 100, 1)


class RevenueAnalyticsView(APIView):
    """GET /analytics/revenue/ — revenue, commission, and driver earnings."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        months = int(request.query_params.get('months', 6))
        since = timezone.now() - timedelta(days=months * 30)

        monthly_revenue = (
            Payment.objects.filter(status='COMPLETED', completed_at__gte=since)
            .annotate(month=TruncMonth('completed_at'))
            .values('month')
            .annotate(total=Sum('amount'))
            .order_by('month')
        )

        total_revenue = Payment.objects.filter(status='COMPLETED').aggregate(
            total=Sum('amount')
        )['total'] or 0

        commission_transactions = WalletTransaction.objects.filter(
            purpose='COMMISSION'
        ).aggregate(total=Sum('amount'))['total'] or 0

        driver_earnings = WalletTransaction.objects.filter(
            purpose='DRIVER_EARNINGS'
        ).aggregate(total=Sum('amount'))['total'] or 0

        return Response({
            'monthly_revenue': [
                {'month': item['month'].strftime('%Y-%m'), 'total_etb': float(item['total'])}
                for item in monthly_revenue
            ],
            'totals': {
                'total_revenue_etb': float(total_revenue),
                'total_commission_etb': float(commission_transactions),
                'total_driver_earnings_etb': float(driver_earnings),
            }
        })


class DriverAnalyticsView(APIView):
    """GET /analytics/drivers/ — top drivers and performance metrics."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        top_drivers = (
            DriverProfile.objects.filter(is_approved=True)
            .select_related('user')
            .order_by('-total_deliveries')[:10]
        )

        top_earners = (
            DriverProfile.objects.filter(is_approved=True)
            .select_related('user')
            .order_by('-total_earnings')[:10]
        )

        avg_rating = DriverProfile.objects.filter(is_approved=True).aggregate(
            avg=Avg('rating')
        )['avg'] or 0

        return Response({
            'top_drivers_by_deliveries': [
                {
                    'driver': dp.user.full_name,
                    'phone': str(dp.user.phone),
                    'total_deliveries': dp.total_deliveries,
                    'rating': float(dp.rating),
                    'vehicle_type': dp.vehicle_type,
                }
                for dp in top_drivers
            ],
            'top_earners': [
                {
                    'driver': dp.user.full_name,
                    'total_earnings_etb': float(dp.total_earnings),
                    'total_deliveries': dp.total_deliveries,
                }
                for dp in top_earners
            ],
            'average_driver_rating': round(float(avg_rating), 2),
            'total_approved_drivers': DriverProfile.objects.filter(is_approved=True).count(),
            'total_online_drivers': DriverProfile.objects.filter(is_online=True).count(),
        })


class DeliveryHeatmapView(APIView):
    """GET /analytics/heatmap/ — GeoJSON of pickup/delivery locations for map heatmap."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        days = int(request.query_params.get('days', 30))
        since = timezone.now() - timedelta(days=days)
        order_type = request.query_params.get('type', 'pickup')

        orders = Order.objects.filter(
            created_at__gte=since,
            status__in=['DELIVERED', 'IN_TRANSIT', 'PICKED_UP'],
        )

        features = []
        for order in orders:
            point = order.pickup_location if order_type == 'pickup' else order.destination_location
            if point:
                features.append({
                    'type': 'Feature',
                    'geometry': {
                        'type': 'Point',
                        'coordinates': [point.x, point.y],
                    },
                    'properties': {
                        'order_id': order.id,
                        'status': order.status,
                    }
                })

        return Response({
            'type': 'FeatureCollection',
            'features': features,
            'count': len(features),
        })


class LiveOrderMapView(APIView):
    """GET /analytics/live-orders/ — currently active orders with driver locations."""
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        active_orders = Order.objects.filter(
            status__in=['DRIVER_ASSIGNED', 'DRIVER_ON_WAY', 'ARRIVED_AT_PICKUP',
                        'PICKED_UP', 'IN_TRANSIT', 'ARRIVED_AT_DESTINATION'],
            driver__isnull=False,
        ).select_related('customer', 'driver', 'driver__driver_profile')

        data = []
        for order in active_orders:
            driver_location = None
            try:
                loc = order.driver.driver_profile.current_location
                if loc:
                    driver_location = {'lat': loc.y, 'lng': loc.x}
            except Exception:
                pass

            data.append({
                'order_id': order.id,
                'order_number': order.order_number,
                'status': order.status,
                'customer': order.customer.full_name,
                'driver': order.driver.full_name if order.driver else None,
                'driver_location': driver_location,
                'pickup': {
                    'address': order.pickup_address,
                    'lat': order.pickup_location.y if order.pickup_location else None,
                    'lng': order.pickup_location.x if order.pickup_location else None,
                },
                'destination': {
                    'address': order.destination_address,
                    'lat': order.destination_location.y if order.destination_location else None,
                    'lng': order.destination_location.x if order.destination_location else None,
                },
            })

        return Response({'active_orders': data, 'count': len(data)})
