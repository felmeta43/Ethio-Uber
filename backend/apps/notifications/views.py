"""
Notification views for Ethio-Uber.
"""
from rest_framework import generics, status
from rest_framework.response import Response
from rest_framework.views import APIView
from .models import DeviceToken, Notification
from .serializers import DeviceTokenSerializer, NotificationSerializer


class RegisterDeviceTokenView(generics.CreateAPIView):
    """POST /notifications/device/ — register or update FCM device token."""
    serializer_class = DeviceTokenSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        token_obj = serializer.save()
        return Response(DeviceTokenSerializer(token_obj).data, status=status.HTTP_200_OK)


class DeactivateDeviceTokenView(APIView):
    """DELETE /notifications/device/{id}/ — deactivate a device token on logout."""

    def delete(self, request, pk):
        try:
            token = DeviceToken.objects.get(pk=pk, user=request.user)
            token.is_active = False
            token.save(update_fields=['is_active'])
            return Response({'message': 'Device token deactivated.'}, status=status.HTTP_200_OK)
        except DeviceToken.DoesNotExist:
            return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)


class NotificationListView(generics.ListAPIView):
    """GET /notifications/ — paginated list of user's notifications."""
    serializer_class = NotificationSerializer

    def get_queryset(self):
        return Notification.objects.filter(user=self.request.user).order_by('-created_at')


class NotificationDetailView(generics.RetrieveAPIView):
    """GET /notifications/{id}/ — single notification, marks it as read."""
    serializer_class = NotificationSerializer

    def get_queryset(self):
        return Notification.objects.filter(user=self.request.user)

    def retrieve(self, request, *args, **kwargs):
        instance = self.get_object()
        if not instance.is_read:
            instance.mark_read()
        serializer = self.get_serializer(instance)
        return Response(serializer.data)


class MarkAllReadView(APIView):
    """POST /notifications/mark-all-read/ — mark all notifications as read."""

    def post(self, request):
        count = Notification.objects.filter(user=request.user, is_read=False).update(is_read=True)
        return Response({'marked_read': count})


class UnreadCountView(APIView):
    """GET /notifications/unread-count/ — count of unread notifications."""

    def get(self, request):
        count = Notification.objects.filter(user=request.user, is_read=False).count()
        return Response({'unread_count': count})
