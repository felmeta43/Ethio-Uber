from django.contrib import admin
from django.utils.html import format_html
from .models import Wallet, WalletTransaction, Payment, WithdrawalRequest


@admin.register(Wallet)
class WalletAdmin(admin.ModelAdmin):
    list_display = ['user', 'balance', 'currency', 'is_active', 'updated_at']
    list_filter = ['currency', 'is_active']
    search_fields = ['user__full_name', 'user__phone']
    readonly_fields = ['balance', 'updated_at']


@admin.register(WalletTransaction)
class WalletTransactionAdmin(admin.ModelAdmin):
    list_display = ['wallet', 'transaction_type', 'amount', 'purpose', 'balance_after', 'timestamp']
    list_filter = ['transaction_type', 'purpose']
    search_fields = ['wallet__user__full_name', 'reference']
    readonly_fields = ['timestamp']
    ordering = ['-timestamp']


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ['order', 'amount', 'method', 'colored_status', 'gateway_reference', 'created_at']
    list_filter = ['method', 'status']
    search_fields = ['order__order_number', 'gateway_reference']
    readonly_fields = ['created_at', 'updated_at', 'gateway_response']
    ordering = ['-created_at']

    def colored_status(self, obj):
        colors = {
            'PENDING': 'orange',
            'PROCESSING': 'blue',
            'COMPLETED': 'green',
            'FAILED': 'red',
            'REFUNDED': 'purple',
        }
        color = colors.get(obj.status, 'black')
        return format_html('<span style="color: {}; font-weight: bold;">{}</span>', color, obj.status)
    colored_status.short_description = 'Status'


@admin.register(WithdrawalRequest)
class WithdrawalRequestAdmin(admin.ModelAdmin):
    list_display = ['driver', 'amount', 'bank_name', 'account_number', 'status', 'created_at']
    list_filter = ['status', 'bank_name']
    search_fields = ['driver__full_name', 'driver__phone', 'account_number']
    readonly_fields = ['created_at']
    actions = ['approve_withdrawals', 'reject_withdrawals']

    def approve_withdrawals(self, request, queryset):
        from django.utils import timezone
        updated = queryset.filter(status='PENDING').update(
            status='APPROVED',
            processed_at=timezone.now(),
        )
        self.message_user(request, f'{updated} withdrawal(s) approved.')
    approve_withdrawals.short_description = 'Approve selected withdrawal requests'

    def reject_withdrawals(self, request, queryset):
        updated = queryset.filter(status='PENDING').update(status='REJECTED')
        self.message_user(request, f'{updated} withdrawal(s) rejected.')
    reject_withdrawals.short_description = 'Reject selected withdrawal requests'
