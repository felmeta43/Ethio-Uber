"""URL configuration for the orders app."""
from django.urls import path
from .views import (
    EstimateDeliveryFeeView, OrderCreateView, OrderListView, OrderDetailView,
    OrderCancelView, AcceptOrderView, RejectOrderView, UpdateOrderStatusView,
    VerifyDeliveryOTPView, RateDriverView, RateCustomerView, DeliveryFeeConfigView,
)

urlpatterns = [
    path('', OrderListView.as_view(), name='order-list'),
    path('create/', OrderCreateView.as_view(), name='order-create'),
    path('estimate-fee/', EstimateDeliveryFeeView.as_view(), name='estimate-fee'),
    path('fee-config/', DeliveryFeeConfigView.as_view(), name='fee-config'),
    path('<int:pk>/', OrderDetailView.as_view(), name='order-detail'),
    path('<int:pk>/cancel/', OrderCancelView.as_view(), name='order-cancel'),
    path('<int:pk>/accept/', AcceptOrderView.as_view(), name='order-accept'),
    path('<int:pk>/reject/', RejectOrderView.as_view(), name='order-reject'),
    path('<int:pk>/status/', UpdateOrderStatusView.as_view(), name='order-update-status'),
    path('<int:pk>/verify-otp/', VerifyDeliveryOTPView.as_view(), name='order-verify-otp'),
    path('<int:pk>/rate-driver/', RateDriverView.as_view(), name='rate-driver'),
    path('<int:pk>/rate-customer/', RateCustomerView.as_view(), name='rate-customer'),
]
