from django.urls import path
from . import views

urlpatterns = [
    path('dashboard/', views.DashboardStatsView.as_view(), name='analytics-dashboard'),
    path('orders/', views.OrderAnalyticsView.as_view(), name='analytics-orders'),
    path('revenue/', views.RevenueAnalyticsView.as_view(), name='analytics-revenue'),
    path('drivers/', views.DriverAnalyticsView.as_view(), name='analytics-drivers'),
    path('heatmap/', views.DeliveryHeatmapView.as_view(), name='analytics-heatmap'),
    path('live-orders/', views.LiveOrderMapView.as_view(), name='analytics-live-orders'),
]
