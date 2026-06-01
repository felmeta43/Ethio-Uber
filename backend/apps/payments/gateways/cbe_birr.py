"""
CBE Birr payment gateway integration.
CBE Birr is Commercial Bank of Ethiopia's mobile payment service.
"""
import logging
import requests
from django.conf import settings

from .base import PaymentGateway, PaymentInitResponse, PaymentVerifyResponse

logger = logging.getLogger(__name__)


class CBEBirrGateway(PaymentGateway):
    """
    CBE Birr payment gateway implementation.
    Note: CBE Birr's official merchant API documentation is limited.
    This implementation follows the common integration pattern.
    """

    def __init__(self):
        self.merchant_id = getattr(settings, 'CBE_BIRR_MERCHANT_ID', '')
        self.api_key = getattr(settings, 'CBE_BIRR_API_KEY', '')
        self.base_url = getattr(settings, 'CBE_BIRR_BASE_URL', '')

        if not self.merchant_id:
            logger.warning("CBE_BIRR_MERCHANT_ID not configured")

    def initiate_payment(
        self,
        amount: float,
        currency: str = 'ETB',
        tx_ref: str = '',
        customer_phone: str = '',
        customer_name: str = '',
        customer_email: str = '',
        description: str = '',
        callback_url: str = '',
        return_url: str = '',
        **kwargs
    ) -> PaymentInitResponse:
        """Initialize a CBE Birr payment request."""
        payload = {
            'merchantId': self.merchant_id,
            'amount': amount,
            'currency': currency,
            'reference': tx_ref,
            'description': description,
            'customerPhone': customer_phone,
            'customerName': customer_name,
            'notifyUrl': callback_url,
            'returnUrl': return_url,
        }

        headers = {
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json',
        }

        try:
            response = requests.post(
                f'{self.base_url}/payment/initialize',
                json=payload,
                headers=headers,
                timeout=30
            )
            response_data = response.json()

            if response.status_code == 200 and response_data.get('success'):
                return PaymentInitResponse(
                    success=True,
                    gateway_reference=tx_ref,
                    checkout_url=response_data.get('checkoutUrl', ''),
                    message='CBE Birr payment initialized',
                    raw_response=response_data,
                )
            else:
                return PaymentInitResponse(
                    success=False,
                    message=response_data.get('message', 'CBE Birr initialization failed'),
                    raw_response=response_data,
                )

        except Exception as e:
            logger.error(f"CBE Birr payment error: {e}")
            return PaymentInitResponse(success=False, message=str(e))

    def verify_payment(self, tx_ref: str) -> PaymentVerifyResponse:
        """Verify CBE Birr payment status."""
        headers = {
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json',
        }

        try:
            response = requests.get(
                f'{self.base_url}/payment/verify/{tx_ref}',
                headers=headers,
                timeout=30
            )
            response_data = response.json()

            if response.status_code == 200:
                pay_status = response_data.get('status', '').upper()
                if pay_status == 'SUCCESS':
                    return PaymentVerifyResponse(
                        success=True,
                        status='COMPLETED',
                        amount=float(response_data.get('amount', 0)),
                        message='Payment verified',
                        raw_response=response_data,
                    )
                elif pay_status == 'FAILED':
                    return PaymentVerifyResponse(
                        success=False,
                        status='FAILED',
                        message='Payment failed',
                        raw_response=response_data,
                    )
                else:
                    return PaymentVerifyResponse(
                        success=False,
                        status='PENDING',
                        message=f'Status: {pay_status}',
                        raw_response=response_data,
                    )
            else:
                return PaymentVerifyResponse(
                    success=False,
                    status='PENDING',
                    message='Verification failed',
                    raw_response=response_data,
                )

        except Exception as e:
            logger.error(f"CBE Birr verify error: {e}")
            return PaymentVerifyResponse(success=False, status='PENDING', message=str(e))
