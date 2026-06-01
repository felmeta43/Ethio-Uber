"""URL configuration for the accounts app."""
from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView

from .views import (
    CustomerRegisterView, DriverRegisterView, MerchantRegisterView,
    SendOTPView, VerifyOTPView, LoginView, LogoutView,
    ProfileView, ChangePasswordView, SavedAddressView,
    DriverStatusView, NearbyDriversView,
)

urlpatterns = [
    # Registration
    path('register/customer/', CustomerRegisterView.as_view(), name='register-customer'),
    path('register/driver/', DriverRegisterView.as_view(), name='register-driver'),
    path('register/merchant/', MerchantRegisterView.as_view(), name='register-merchant'),

    # OTP
    path('otp/send/', SendOTPView.as_view(), name='otp-send'),
    path('otp/verify/', VerifyOTPView.as_view(), name='otp-verify'),

    # Auth
    path('login/', LoginView.as_view(), name='login'),
    path('logout/', LogoutView.as_view(), name='logout'),
    path('token/refresh/', TokenRefreshView.as_view(), name='token-refresh'),

    # Profile
    path('profile/', ProfileView.as_view(), name='profile'),
    path('change-password/', ChangePasswordView.as_view(), name='change-password'),

    # Customer
    path('saved-addresses/', SavedAddressView.as_view(), name='saved-addresses'),
    path('saved-addresses/<int:index>/', SavedAddressView.as_view(), name='saved-address-delete'),

    # Driver
    path('driver/status/', DriverStatusView.as_view(), name='driver-status'),
    path('drivers/nearby/', NearbyDriversView.as_view(), name='nearby-drivers'),
]
