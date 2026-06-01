from rest_framework import serializers
from .models import DeviceToken, Notification


class DeviceTokenSerializer(serializers.ModelSerializer):
    class Meta:
        model = DeviceToken
        fields = ['id', 'token', 'device_type', 'device_id', 'is_active', 'created_at']
        read_only_fields = ['id', 'created_at']

    def create(self, validated_data):
        user = self.context['request'].user
        device_id = validated_data.get('device_id', '')

        if device_id:
            token_obj, _ = DeviceToken.objects.update_or_create(
                user=user,
                device_id=device_id,
                defaults={
                    'token': validated_data['token'],
                    'device_type': validated_data.get('device_type', 'ANDROID'),
                    'is_active': True,
                },
            )
            return token_obj

        return DeviceToken.objects.create(user=user, **validated_data)


class NotificationSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source='order.order_number', read_only=True, allow_null=True)

    class Meta:
        model = Notification
        fields = [
            'id', 'title', 'body', 'notification_type', 'data',
            'is_read', 'order', 'order_number', 'created_at',
        ]
        read_only_fields = fields
