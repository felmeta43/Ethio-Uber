"""
Utility functions for the accounts app.
"""
import logging
from rest_framework.views import exception_handler
from rest_framework.response import Response
from rest_framework import status

logger = logging.getLogger(__name__)


def custom_exception_handler(exc, context):
    """Custom exception handler for DRF that returns consistent error format."""
    response = exception_handler(exc, context)

    if response is not None:
        error_data = {
            'success': False,
            'errors': {},
        }

        if isinstance(response.data, dict):
            if 'detail' in response.data:
                error_data['message'] = str(response.data['detail'])
            else:
                error_data['errors'] = response.data
                error_data['message'] = 'Validation error'
        elif isinstance(response.data, list):
            error_data['message'] = str(response.data[0]) if response.data else 'Error occurred'
        else:
            error_data['message'] = str(response.data)

        response.data = error_data

    return response
