from django.urls import path
from . import views

urlpatterns = [
    path('', views.NotificationListView.as_view(), name='notification-list'),
    path('unread-count/', views.UnreadCountView.as_view(), name='notification-unread-count'),
    path('mark-all-read/', views.MarkAllReadView.as_view(), name='notification-mark-all-read'),
    path('<int:pk>/', views.NotificationDetailView.as_view(), name='notification-detail'),
    path('device/', views.RegisterDeviceTokenView.as_view(), name='device-token-register'),
    path('device/<int:pk>/', views.DeactivateDeviceTokenView.as_view(), name='device-token-deactivate'),
]
