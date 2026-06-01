from django.contrib import admin
from .models import MerchantCategory, MerchantProduct, MerchantOrder, MerchantOrderItem


@admin.register(MerchantCategory)
class MerchantCategoryAdmin(admin.ModelAdmin):
    list_display = ['name', 'is_active', 'sort_order']
    list_editable = ['is_active', 'sort_order']


class MerchantOrderItemInline(admin.TabularInline):
    model = MerchantOrderItem
    extra = 0
    readonly_fields = ['subtotal']


@admin.register(MerchantProduct)
class MerchantProductAdmin(admin.ModelAdmin):
    list_display = ['name', 'merchant', 'category', 'price', 'is_available']
    list_filter = ['is_available', 'category']
    search_fields = ['name', 'merchant__business_name']
    list_editable = ['is_available', 'price']


@admin.register(MerchantOrder)
class MerchantOrderAdmin(admin.ModelAdmin):
    list_display = ['order', 'merchant', 'status', 'subtotal', 'created_at']
    list_filter = ['status']
    search_fields = ['order__order_number', 'merchant__business_name']
    inlines = [MerchantOrderItemInline]
    readonly_fields = ['created_at', 'updated_at']
