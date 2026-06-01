from django.urls import path
from . import views

urlpatterns = [
    path('wallet/', views.WalletBalanceView.as_view(), name='wallet-balance'),
    path('wallet/transactions/', views.WalletTransactionHistoryView.as_view(), name='wallet-transactions'),
    path('wallet/topup/', views.WalletTopUpView.as_view(), name='wallet-topup'),
    path('wallet/topup/callback/', views.WalletTopUpCallbackView.as_view(), name='wallet-topup-callback'),
    path('initiate/', views.InitiatePaymentView.as_view(), name='payment-initiate'),
    path('verify/', views.VerifyPaymentView.as_view(), name='payment-verify'),
    path('history/', views.PaymentHistoryView.as_view(), name='payment-history'),
    path('callback/chapa/', views.ChapaCallbackView.as_view(), name='chapa-callback'),
    path('callback/telebirr/', views.TelebirrCallbackView.as_view(), name='telebirr-callback'),
    path('callback/cbe_birr/', views.CBEBirrCallbackView.as_view(), name='cbe-birr-callback'),
    path('withdrawals/', views.WithdrawalRequestListCreateView.as_view(), name='withdrawal-list-create'),
    path('withdrawals/all/', views.WithdrawalAdminListView.as_view(), name='withdrawal-admin-list'),
    path('withdrawals/<int:pk>/status/', views.WithdrawalStatusUpdateView.as_view(), name='withdrawal-status'),
]
