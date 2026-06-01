from django.urls import path
from . import views

urlpatterns = [
    path('', views.MerchantListView.as_view(), name='merchant-list'),
    path('categories/', views.MerchantCategoryListView.as_view(), name='merchant-category-list'),
    path('orders/', views.CreateMerchantOrderView.as_view(), name='merchant-order-create'),
    path('my/products/', views.MyProductListCreateView.as_view(), name='my-product-list-create'),
    path('my/products/<int:pk>/', views.MyProductDetailView.as_view(), name='my-product-detail'),
    path('my/orders/', views.MyMerchantOrderListView.as_view(), name='my-merchant-order-list'),
    path('my/orders/<int:pk>/status/', views.MerchantOrderStatusUpdateView.as_view(), name='my-merchant-order-status'),
    path('my/toggle-open/', views.MerchantOpenCloseView.as_view(), name='my-merchant-toggle-open'),
    path('<int:pk>/', views.MerchantDetailView.as_view(), name='merchant-detail'),
    path('<int:merchant_id>/products/', views.MerchantProductListView.as_view(), name='merchant-product-list'),
]
