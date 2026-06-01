"""
Serializers for the accounts app.
"""
import random
import string
from django.contrib.auth import authenticate
from django.contrib.auth.password_validation import validate_password
from django.utils.translation import gettext_lazy as _
from rest_framework import serializers
from rest_framework_simplejwt.tokens import RefreshToken
from phonenumber_field.serializerfields import PhoneNumberField

from .models import User, OTPVerification, CustomerProfile, DriverProfile, MerchantProfile


class CustomerRegistrationSerializer(serializers.ModelSerializer):
    """Serializer for customer registration."""
    password = serializers.CharField(write_only=True, min_length=8)
    confirm_password = serializers.CharField(write_only=True)

    class Meta:
        model = User
        fields = ['phone', 'full_name', 'email', 'password', 'confirm_password']

    def validate(self, attrs):
        if attrs['password'] != attrs['confirm_password']:
            raise serializers.ValidationError({'confirm_password': _('Passwords do not match.')})
        validate_password(attrs['password'])
        return attrs

    def create(self, validated_data):
        validated_data.pop('confirm_password')
        user = User.objects.create_user(
            phone=validated_data['phone'],
            password=validated_data['password'],
            full_name=validated_data['full_name'],
            email=validated_data.get('email', ''),
            user_type=User.UserType.CUSTOMER,
        )
        CustomerProfile.objects.create(user=user)
        return user


class DriverRegistrationSerializer(serializers.ModelSerializer):
    """Serializer for driver registration with document uploads."""
    password = serializers.CharField(write_only=True, min_length=8)
    confirm_password = serializers.CharField(write_only=True)
    national_id_number = serializers.CharField()
    national_id_photo = serializers.ImageField()
    driver_license_number = serializers.CharField()
    driver_license_photo = serializers.ImageField()
    vehicle_type = serializers.ChoiceField(choices=DriverProfile.VehicleType.choices)
    vehicle_plate = serializers.CharField()
    vehicle_model = serializers.CharField()
    vehicle_photo = serializers.ImageField()

    class Meta:
        model = User
        fields = [
            'phone', 'full_name', 'email', 'password', 'confirm_password',
            'national_id_number', 'national_id_photo',
            'driver_license_number', 'driver_license_photo',
            'vehicle_type', 'vehicle_plate', 'vehicle_model', 'vehicle_photo',
        ]

    def validate(self, attrs):
        if attrs['password'] != attrs['confirm_password']:
            raise serializers.ValidationError({'confirm_password': _('Passwords do not match.')})
        validate_password(attrs['password'])
        return attrs

    def create(self, validated_data):
        validated_data.pop('confirm_password')
        driver_fields = [
            'national_id_number', 'national_id_photo',
            'driver_license_number', 'driver_license_photo',
            'vehicle_type', 'vehicle_plate', 'vehicle_model', 'vehicle_photo',
        ]
        driver_data = {k: validated_data.pop(k) for k in driver_fields if k in validated_data}

        user = User.objects.create_user(
            phone=validated_data['phone'],
            password=validated_data['password'],
            full_name=validated_data['full_name'],
            email=validated_data.get('email', ''),
            user_type=User.UserType.DRIVER,
        )
        DriverProfile.objects.create(user=user, **driver_data)
        return user


class MerchantRegistrationSerializer(serializers.ModelSerializer):
    """Serializer for merchant registration."""
    password = serializers.CharField(write_only=True, min_length=8)
    confirm_password = serializers.CharField(write_only=True)
    business_name = serializers.CharField()
    business_type = serializers.ChoiceField(choices=MerchantProfile.BusinessType.choices)
    business_address = serializers.CharField()
    business_latitude = serializers.FloatField(write_only=True)
    business_longitude = serializers.FloatField(write_only=True)
    business_phone = PhoneNumberField(required=False)
    business_description = serializers.CharField(required=False, allow_blank=True)
    business_license = serializers.ImageField(required=False)

    class Meta:
        model = User
        fields = [
            'phone', 'full_name', 'email', 'password', 'confirm_password',
            'business_name', 'business_type', 'business_address',
            'business_latitude', 'business_longitude',
            'business_phone', 'business_description', 'business_license',
        ]

    def validate(self, attrs):
        if attrs['password'] != attrs['confirm_password']:
            raise serializers.ValidationError({'confirm_password': _('Passwords do not match.')})
        validate_password(attrs['password'])
        return attrs

    def create(self, validated_data):
        from django.contrib.gis.geos import Point
        validated_data.pop('confirm_password')

        lat = validated_data.pop('business_latitude')
        lng = validated_data.pop('business_longitude')
        business_location = Point(lng, lat, srid=4326)

        merchant_fields = [
            'business_name', 'business_type', 'business_address',
            'business_phone', 'business_description', 'business_license',
        ]
        merchant_data = {k: validated_data.pop(k) for k in merchant_fields if k in validated_data}
        merchant_data['business_location'] = business_location

        user = User.objects.create_user(
            phone=validated_data['phone'],
            password=validated_data['password'],
            full_name=validated_data['full_name'],
            email=validated_data.get('email', ''),
            user_type=User.UserType.MERCHANT,
        )
        MerchantProfile.objects.create(user=user, **merchant_data)
        return user


class OTPVerifySerializer(serializers.Serializer):
    """Serializer for OTP verification."""
    phone = PhoneNumberField()
    otp = serializers.CharField(min_length=4, max_length=6)
    purpose = serializers.ChoiceField(choices=OTPVerification.Purpose.choices)


class SendOTPSerializer(serializers.Serializer):
    """Serializer for sending OTP."""
    phone = PhoneNumberField()
    purpose = serializers.ChoiceField(choices=OTPVerification.Purpose.choices)


class LoginSerializer(serializers.Serializer):
    """Serializer for phone + password login."""
    phone = PhoneNumberField()
    password = serializers.CharField(write_only=True)

    def validate(self, attrs):
        phone = attrs.get('phone')
        password = attrs.get('password')

        if phone and password:
            user = authenticate(request=self.context.get('request'), username=str(phone), password=password)
            if not user:
                raise serializers.ValidationError(_('Invalid phone number or password.'))
            if not user.is_active:
                raise serializers.ValidationError(_('This account has been deactivated.'))
            if not user.is_verified:
                raise serializers.ValidationError(_('Phone number not verified. Please verify your phone first.'))
        else:
            raise serializers.ValidationError(_('Must include phone and password.'))

        attrs['user'] = user
        return attrs


class UserBasicSerializer(serializers.ModelSerializer):
    """Basic user info serializer."""
    phone = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = ['id', 'phone', 'full_name', 'email', 'user_type', 'is_verified', 'profile_photo']

    def get_phone(self, obj):
        return str(obj.phone)


class CustomerProfileSerializer(serializers.ModelSerializer):
    """Serializer for customer profile."""
    user = UserBasicSerializer(read_only=True)

    class Meta:
        model = CustomerProfile
        fields = ['id', 'user', 'emergency_contact', 'saved_addresses', 'total_orders']

    def update(self, instance, validated_data):
        instance.emergency_contact = validated_data.get('emergency_contact', instance.emergency_contact)
        instance.saved_addresses = validated_data.get('saved_addresses', instance.saved_addresses)
        instance.save()
        return instance


class DriverProfileSerializer(serializers.ModelSerializer):
    """Serializer for driver profile."""
    user = UserBasicSerializer(read_only=True)
    current_latitude = serializers.SerializerMethodField()
    current_longitude = serializers.SerializerMethodField()

    class Meta:
        model = DriverProfile
        fields = [
            'id', 'user', 'national_id_number', 'national_id_photo',
            'driver_license_number', 'driver_license_photo',
            'vehicle_type', 'vehicle_plate', 'vehicle_model', 'vehicle_photo',
            'is_approved', 'is_online', 'current_latitude', 'current_longitude',
            'rating', 'total_deliveries', 'total_earnings',
        ]
        read_only_fields = ['is_approved', 'rating', 'total_deliveries', 'total_earnings']

    def get_current_latitude(self, obj):
        if obj.current_location:
            return obj.current_location.y
        return None

    def get_current_longitude(self, obj):
        if obj.current_location:
            return obj.current_location.x
        return None


class MerchantProfileSerializer(serializers.ModelSerializer):
    """Serializer for merchant profile."""
    user = UserBasicSerializer(read_only=True)
    business_latitude = serializers.SerializerMethodField()
    business_longitude = serializers.SerializerMethodField()

    class Meta:
        model = MerchantProfile
        fields = [
            'id', 'user', 'business_name', 'business_type', 'business_license',
            'business_address', 'business_latitude', 'business_longitude',
            'is_approved', 'is_open', 'rating', 'total_orders', 'phone', 'description',
        ]
        read_only_fields = ['is_approved', 'rating', 'total_orders']

    def get_business_latitude(self, obj):
        if obj.business_location:
            return obj.business_location.y
        return None

    def get_business_longitude(self, obj):
        if obj.business_location:
            return obj.business_location.x
        return None


class UserProfileSerializer(serializers.ModelSerializer):
    """Full user profile with nested sub-profile."""
    customer_profile = CustomerProfileSerializer(read_only=True)
    driver_profile = DriverProfileSerializer(read_only=True)
    merchant_profile = MerchantProfileSerializer(read_only=True)
    phone = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = [
            'id', 'phone', 'full_name', 'email', 'user_type',
            'is_verified', 'profile_photo', 'date_joined',
            'customer_profile', 'driver_profile', 'merchant_profile',
        ]
        read_only_fields = ['user_type', 'is_verified', 'date_joined']

    def get_phone(self, obj):
        return str(obj.phone)


class ChangePasswordSerializer(serializers.Serializer):
    """Serializer for changing password."""
    old_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(write_only=True, min_length=8)
    confirm_new_password = serializers.CharField(write_only=True)

    def validate(self, attrs):
        if attrs['new_password'] != attrs['confirm_new_password']:
            raise serializers.ValidationError({'confirm_new_password': _('New passwords do not match.')})
        validate_password(attrs['new_password'])
        return attrs


class SavedAddressSerializer(serializers.Serializer):
    """Serializer for saved address operations."""
    label = serializers.CharField(max_length=100)
    address = serializers.CharField(max_length=500)
    lat = serializers.FloatField()
    lng = serializers.FloatField()


class NearbyDriverSerializer(serializers.ModelSerializer):
    """Serializer for displaying nearby drivers to customers."""
    full_name = serializers.CharField(source='user.full_name')
    profile_photo = serializers.ImageField(source='user.profile_photo')
    distance_km = serializers.SerializerMethodField()
    latitude = serializers.SerializerMethodField()
    longitude = serializers.SerializerMethodField()

    class Meta:
        model = DriverProfile
        fields = [
            'id', 'full_name', 'profile_photo', 'vehicle_type',
            'vehicle_model', 'vehicle_plate', 'rating', 'total_deliveries',
            'latitude', 'longitude', 'distance_km',
        ]

    def get_distance_km(self, obj):
        if hasattr(obj, 'distance') and obj.distance:
            return round(obj.distance.km, 2)
        return None

    def get_latitude(self, obj):
        if obj.current_location:
            return obj.current_location.y
        return None

    def get_longitude(self, obj):
        if obj.current_location:
            return obj.current_location.x
        return None
