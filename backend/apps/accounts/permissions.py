"""
Custom permissions for Ethio-Uber user types.
"""
from rest_framework.permissions import BasePermission
from .models import User


class IsCustomer(BasePermission):
    """Allow access only to Customer users."""
    message = 'Only customers can perform this action.'

    def has_permission(self, request, view):
        return (
            request.user and
            request.user.is_authenticated and
            request.user.user_type == User.UserType.CUSTOMER
        )


class IsDriver(BasePermission):
    """Allow access only to Driver users."""
    message = 'Only drivers can perform this action.'

    def has_permission(self, request, view):
        return (
            request.user and
            request.user.is_authenticated and
            request.user.user_type == User.UserType.DRIVER
        )


class IsDriverApproved(BasePermission):
    """Allow access only to approved Driver users."""
    message = 'Only approved drivers can perform this action.'

    def has_permission(self, request, view):
        if not (request.user and request.user.is_authenticated):
            return False
        if request.user.user_type != User.UserType.DRIVER:
            return False
        try:
            return request.user.driver_profile.is_approved
        except AttributeError:
            return False


class IsMerchant(BasePermission):
    """Allow access only to Merchant users."""
    message = 'Only merchants can perform this action.'

    def has_permission(self, request, view):
        return (
            request.user and
            request.user.is_authenticated and
            request.user.user_type == User.UserType.MERCHANT
        )


class IsMerchantApproved(BasePermission):
    """Allow access only to approved Merchant users."""
    message = 'Only approved merchants can perform this action.'

    def has_permission(self, request, view):
        if not (request.user and request.user.is_authenticated):
            return False
        if request.user.user_type != User.UserType.MERCHANT:
            return False
        try:
            return request.user.merchant_profile.is_approved
        except AttributeError:
            return False


class IsAdmin(BasePermission):
    """Allow access only to Admin users."""
    message = 'Only administrators can perform this action.'

    def has_permission(self, request, view):
        return (
            request.user and
            request.user.is_authenticated and
            (request.user.user_type == User.UserType.ADMIN or request.user.is_staff)
        )


class IsOwnerOrAdmin(BasePermission):
    """Allow access to the owner of the object or admin."""
    message = 'You do not have permission to access this resource.'

    def has_object_permission(self, request, view, obj):
        if request.user.user_type == User.UserType.ADMIN or request.user.is_staff:
            return True
        if hasattr(obj, 'user'):
            return obj.user == request.user
        if hasattr(obj, 'customer'):
            return obj.customer == request.user
        if hasattr(obj, 'driver'):
            return obj.driver == request.user
        return obj == request.user
