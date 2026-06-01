"""
Views for the accounts app.
"""
import logging
from django.contrib.gis.geos import Point
from django.contrib.gis.db.models.functions import Distance
from django.contrib.gis.measure import D
from django.utils import timezone
from rest_framework import status, generics, filters
from rest_framework.permissions import IsAuthenticated, AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView
from drf_spectacular.utils import extend_schema, OpenApiExample

from .models import User, OTPVerification, CustomerProfile, DriverProfile, MerchantProfile
from .serializers import (
    CustomerRegistrationSerializer, DriverRegistrationSerializer,
    MerchantRegistrationSerializer, OTPVerifySerializer, SendOTPSerializer,
    LoginSerializer, UserProfileSerializer, DriverProfileSerializer,
    MerchantProfileSerializer, ChangePasswordSerializer,
    SavedAddressSerializer, NearbyDriverSerializer
)
from .permissions import IsCustomer, IsDriver, IsAdmin

logger = logging.getLogger(__name__)


def get_tokens_for_user(user):
    """Generate JWT tokens for a user."""
    refresh = RefreshToken.for_user(user)
    return {
        'refresh': str(refresh),
        'access': str(refresh.access_token),
    }


class CustomerRegisterView(generics.CreateAPIView):
    """Register a new customer."""
    permission_classes = [AllowAny]
    serializer_class = CustomerRegistrationSerializer

    @extend_schema(tags=['Authentication'])
    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        # Create and send OTP
        otp_code = OTPVerification.generate_otp(6)
        OTPVerification.objects.create(
            user=user,
            otp=otp_code,
            purpose=OTPVerification.Purpose.REGISTRATION,
        )

        # Send OTP via SMS (async)
        try:
            from apps.notifications.tasks import send_otp_sms_task
            send_otp_sms_task.delay(str(user.phone), otp_code, OTPVerification.Purpose.REGISTRATION)
        except Exception as e:
            logger.error(f"Failed to send OTP SMS: {e}")

        return Response({
            'success': True,
            'message': 'Registration successful. Please verify your phone number.',
            'data': {
                'user_id': user.id,
                'phone': str(user.phone),
                'full_name': user.full_name,
            }
        }, status=status.HTTP_201_CREATED)


class DriverRegisterView(generics.CreateAPIView):
    """Register a new driver with documents."""
    permission_classes = [AllowAny]
    serializer_class = DriverRegistrationSerializer

    @extend_schema(tags=['Authentication'])
    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        # Create OTP for phone verification
        otp_code = OTPVerification.generate_otp(6)
        OTPVerification.objects.create(
            user=user,
            otp=otp_code,
            purpose=OTPVerification.Purpose.REGISTRATION,
        )

        try:
            from apps.notifications.tasks import send_otp_sms_task
            send_otp_sms_task.delay(str(user.phone), otp_code, OTPVerification.Purpose.REGISTRATION)
        except Exception as e:
            logger.error(f"Failed to send OTP SMS: {e}")

        return Response({
            'success': True,
            'message': 'Driver registration submitted. Verify your phone, then await admin approval.',
            'data': {
                'user_id': user.id,
                'phone': str(user.phone),
                'full_name': user.full_name,
            }
        }, status=status.HTTP_201_CREATED)


class MerchantRegisterView(generics.CreateAPIView):
    """Register a new merchant."""
    permission_classes = [AllowAny]
    serializer_class = MerchantRegistrationSerializer

    @extend_schema(tags=['Authentication'])
    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        otp_code = OTPVerification.generate_otp(6)
        OTPVerification.objects.create(
            user=user,
            otp=otp_code,
            purpose=OTPVerification.Purpose.REGISTRATION,
        )

        try:
            from apps.notifications.tasks import send_otp_sms_task
            send_otp_sms_task.delay(str(user.phone), otp_code, OTPVerification.Purpose.REGISTRATION)
        except Exception as e:
            logger.error(f"Failed to send OTP SMS: {e}")

        return Response({
            'success': True,
            'message': 'Merchant registration submitted. Verify your phone, then await admin approval.',
            'data': {
                'user_id': user.id,
                'phone': str(user.phone),
                'full_name': user.full_name,
            }
        }, status=status.HTTP_201_CREATED)


class SendOTPView(APIView):
    """Send an OTP to a phone number."""
    permission_classes = [AllowAny]

    @extend_schema(tags=['Authentication'], request=SendOTPSerializer)
    def post(self, request):
        serializer = SendOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        phone = serializer.validated_data['phone']
        purpose = serializer.validated_data['purpose']

        try:
            user = User.objects.get(phone=phone)
        except User.DoesNotExist:
            return Response(
                {'success': False, 'message': 'No account found with this phone number.'},
                status=status.HTTP_404_NOT_FOUND
            )

        # Invalidate existing unused OTPs for same purpose
        OTPVerification.objects.filter(
            user=user,
            purpose=purpose,
            is_used=False
        ).update(is_used=True)

        otp_code = OTPVerification.generate_otp(6)
        OTPVerification.objects.create(user=user, otp=otp_code, purpose=purpose)

        try:
            from apps.notifications.tasks import send_otp_sms_task
            send_otp_sms_task.delay(str(phone), otp_code, purpose)
        except Exception as e:
            logger.error(f"Failed to send OTP SMS: {e}")

        return Response({
            'success': True,
            'message': 'OTP sent successfully.',
        })


class VerifyOTPView(APIView):
    """Verify an OTP code."""
    permission_classes = [AllowAny]

    @extend_schema(tags=['Authentication'], request=OTPVerifySerializer)
    def post(self, request):
        serializer = OTPVerifySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        phone = serializer.validated_data['phone']
        otp_code = serializer.validated_data['otp']
        purpose = serializer.validated_data['purpose']

        try:
            user = User.objects.get(phone=phone)
        except User.DoesNotExist:
            return Response(
                {'success': False, 'message': 'No account found with this phone number.'},
                status=status.HTTP_404_NOT_FOUND
            )

        otp_obj = OTPVerification.objects.filter(
            user=user,
            otp=otp_code,
            purpose=purpose,
            is_used=False,
        ).order_by('-created_at').first()

        if not otp_obj or not otp_obj.is_valid():
            return Response(
                {'success': False, 'message': 'Invalid or expired OTP.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        otp_obj.mark_used()

        response_data = {'success': True, 'message': 'OTP verified successfully.'}

        if purpose == OTPVerification.Purpose.REGISTRATION:
            user.is_verified = True
            user.save(update_fields=['is_verified'])
            tokens = get_tokens_for_user(user)
            response_data['data'] = {
                'tokens': tokens,
                'user': UserProfileSerializer(user).data,
            }

        elif purpose == OTPVerification.Purpose.LOGIN:
            tokens = get_tokens_for_user(user)
            response_data['data'] = {
                'tokens': tokens,
                'user': UserProfileSerializer(user).data,
            }

        return Response(response_data)


class LoginView(APIView):
    """Login with phone number and password."""
    permission_classes = [AllowAny]

    @extend_schema(tags=['Authentication'], request=LoginSerializer)
    def post(self, request):
        serializer = LoginSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)

        user = serializer.validated_data['user']
        tokens = get_tokens_for_user(user)

        return Response({
            'success': True,
            'message': 'Login successful.',
            'data': {
                'tokens': tokens,
                'user': UserProfileSerializer(user).data,
            }
        })


class LogoutView(APIView):
    """Logout by blacklisting the refresh token."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Authentication'])
    def post(self, request):
        try:
            refresh_token = request.data.get('refresh')
            if refresh_token:
                token = RefreshToken(refresh_token)
                token.blacklist()
            return Response({'success': True, 'message': 'Logged out successfully.'})
        except Exception as e:
            return Response(
                {'success': False, 'message': 'Invalid token.'},
                status=status.HTTP_400_BAD_REQUEST
            )


class ProfileView(generics.RetrieveUpdateAPIView):
    """Get or update current user profile."""
    permission_classes = [IsAuthenticated]
    serializer_class = UserProfileSerializer

    @extend_schema(tags=['Accounts'])
    def get_object(self):
        return self.request.user

    def patch(self, request, *args, **kwargs):
        user = self.get_object()
        serializer = UserProfileSerializer(user, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response({'success': True, 'data': serializer.data})


class ChangePasswordView(APIView):
    """Change user password."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Accounts'], request=ChangePasswordSerializer)
    def post(self, request):
        serializer = ChangePasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = request.user
        if not user.check_password(serializer.validated_data['old_password']):
            return Response(
                {'success': False, 'message': 'Old password is incorrect.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        user.set_password(serializer.validated_data['new_password'])
        user.save()

        return Response({'success': True, 'message': 'Password changed successfully.'})


class SavedAddressView(APIView):
    """CRUD for customer saved addresses."""
    permission_classes = [IsAuthenticated, IsCustomer]

    @extend_schema(tags=['Accounts'])
    def get(self, request):
        profile = request.user.customer_profile
        return Response({'success': True, 'data': profile.saved_addresses})

    @extend_schema(tags=['Accounts'], request=SavedAddressSerializer)
    def post(self, request):
        serializer = SavedAddressSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        profile = request.user.customer_profile
        profile.add_saved_address(**serializer.validated_data)
        return Response({'success': True, 'data': profile.saved_addresses}, status=status.HTTP_201_CREATED)

    @extend_schema(tags=['Accounts'])
    def delete(self, request, index):
        profile = request.user.customer_profile
        profile.remove_saved_address(index)
        return Response({'success': True, 'data': profile.saved_addresses})


class DriverStatusView(APIView):
    """Toggle driver online/offline status."""
    permission_classes = [IsAuthenticated, IsDriver]

    @extend_schema(tags=['Accounts'])
    def patch(self, request):
        try:
            driver_profile = request.user.driver_profile
        except DriverProfile.DoesNotExist:
            return Response(
                {'success': False, 'message': 'Driver profile not found.'},
                status=status.HTTP_404_NOT_FOUND
            )

        if not driver_profile.is_approved:
            return Response(
                {'success': False, 'message': 'Your account is not yet approved.'},
                status=status.HTTP_403_FORBIDDEN
            )

        is_online = request.data.get('is_online')
        latitude = request.data.get('latitude')
        longitude = request.data.get('longitude')

        if is_online is not None:
            driver_profile.is_online = is_online

        if latitude is not None and longitude is not None:
            driver_profile.current_location = Point(float(longitude), float(latitude), srid=4326)

        driver_profile.save()

        return Response({
            'success': True,
            'message': f"Status set to {'online' if driver_profile.is_online else 'offline'}.",
            'data': {'is_online': driver_profile.is_online}
        })


class NearbyDriversView(APIView):
    """Get available drivers near a location (for customer preview)."""
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=['Accounts'])
    def get(self, request):
        latitude = request.query_params.get('lat')
        longitude = request.query_params.get('lng')
        radius_km = float(request.query_params.get('radius_km', 5))

        if not latitude or not longitude:
            return Response(
                {'success': False, 'message': 'lat and lng query parameters are required.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        try:
            point = Point(float(longitude), float(latitude), srid=4326)
        except (ValueError, TypeError):
            return Response(
                {'success': False, 'message': 'Invalid latitude or longitude values.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        drivers = DriverProfile.objects.filter(
            is_online=True,
            is_approved=True,
            user__is_active=True,
            current_location__isnull=False,
        ).annotate(
            distance=Distance('current_location', point)
        ).filter(
            distance__lte=radius_km * 1000
        ).order_by('distance')[:20]

        serializer = NearbyDriverSerializer(drivers, many=True)
        return Response({
            'success': True,
            'data': serializer.data,
            'count': len(serializer.data),
        })


# ─── Admin management views ──────────────────────────────────────────────────

class PendingDriversView(generics.ListAPIView):
    """GET /accounts/admin/drivers/pending/ — list drivers awaiting approval."""
    permission_classes = [IsAuthenticated, IsAdmin]
    serializer_class = DriverProfileSerializer

    def get_queryset(self):
        return DriverProfile.objects.filter(is_approved=False).select_related('user').order_by('user__date_joined')


class DriverApprovalView(APIView):
    """
    PATCH /accounts/admin/drivers/{user_id}/approve/
    Admin approves or suspends a driver account.
    """
    permission_classes = [IsAuthenticated, IsAdmin]

    def patch(self, request, user_id):
        try:
            driver_profile = DriverProfile.objects.select_related('user').get(user_id=user_id)
        except DriverProfile.DoesNotExist:
            return Response({'success': False, 'message': 'Driver not found.'}, status=status.HTTP_404_NOT_FOUND)

        action = request.data.get('action')
        if action not in ('approve', 'suspend', 'reject'):
            return Response(
                {'success': False, 'message': 'action must be "approve", "suspend", or "reject".'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if action == 'approve':
            driver_profile.is_approved = True
            driver_profile.approved_at = timezone.now()
            driver_profile.user.is_active = True
            driver_profile.user.save(update_fields=['is_active'])
            driver_profile.save(update_fields=['is_approved', 'approved_at'])
            try:
                from apps.notifications.services import notify_driver_approved
                notify_driver_approved(driver_profile.user)
            except Exception as e:
                logger.error(f"Failed to notify driver of approval: {e}")
            message = 'Driver account approved.'

        elif action == 'suspend':
            driver_profile.is_approved = False
            driver_profile.is_online = False
            driver_profile.user.is_active = False
            driver_profile.user.save(update_fields=['is_active'])
            driver_profile.save(update_fields=['is_approved', 'is_online'])
            message = 'Driver account suspended.'

        else:  # reject
            driver_profile.user.is_active = False
            driver_profile.user.save(update_fields=['is_active'])
            message = 'Driver application rejected.'

        return Response({'success': True, 'message': message})


class MerchantApprovalView(APIView):
    """
    PATCH /accounts/admin/merchants/{user_id}/approve/
    Admin approves or suspends a merchant account.
    """
    permission_classes = [IsAuthenticated, IsAdmin]

    def patch(self, request, user_id):
        try:
            merchant_profile = MerchantProfile.objects.select_related('user').get(user_id=user_id)
        except MerchantProfile.DoesNotExist:
            return Response({'success': False, 'message': 'Merchant not found.'}, status=status.HTTP_404_NOT_FOUND)

        action = request.data.get('action')
        if action not in ('approve', 'suspend', 'reject'):
            return Response(
                {'success': False, 'message': 'action must be "approve", "suspend", or "reject".'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if action == 'approve':
            merchant_profile.is_approved = True
            merchant_profile.approved_at = timezone.now()
            merchant_profile.user.is_active = True
            merchant_profile.user.save(update_fields=['is_active'])
            merchant_profile.save(update_fields=['is_approved', 'approved_at'])
            try:
                from apps.notifications.services import notify_merchant_approved
                notify_merchant_approved(merchant_profile.user)
            except Exception as e:
                logger.error(f"Failed to notify merchant of approval: {e}")
            message = 'Merchant account approved.'

        elif action == 'suspend':
            merchant_profile.is_approved = False
            merchant_profile.is_open = False
            merchant_profile.user.is_active = False
            merchant_profile.user.save(update_fields=['is_active'])
            merchant_profile.save(update_fields=['is_approved', 'is_open'])
            message = 'Merchant account suspended.'

        else:
            merchant_profile.user.is_active = False
            merchant_profile.user.save(update_fields=['is_active'])
            message = 'Merchant application rejected.'

        return Response({'success': True, 'message': message})


class AdminUserListView(generics.ListAPIView):
    """GET /accounts/admin/users/?type=CUSTOMER|DRIVER|MERCHANT — admin lists all users."""
    permission_classes = [IsAuthenticated, IsAdmin]
    serializer_class = UserProfileSerializer
    filter_backends = [filters.SearchFilter, filters.OrderingFilter]
    search_fields = ['full_name', 'phone', 'email']
    ordering_fields = ['date_joined', 'full_name']
    ordering = ['-date_joined']

    def get_queryset(self):
        qs = User.objects.all()
        user_type = self.request.query_params.get('type')
        is_active = self.request.query_params.get('is_active')
        if user_type:
            qs = qs.filter(user_type=user_type)
        if is_active is not None:
            qs = qs.filter(is_active=is_active.lower() == 'true')
        return qs
