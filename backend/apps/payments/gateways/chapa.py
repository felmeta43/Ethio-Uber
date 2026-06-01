"""
Chapa payment gateway integration.
Chapa is the most widely used Ethiopian payment gateway.
Docs: https://developer.chapa.co/
"""
import hashlib
import hmac
import json
import logging
import requests
from django.conf import settings

from .base import PaymentGateway, PaymentInitResponse, PaymentVerifyResponse

logger = logging.getLogger(__name__)


class ChapaGateway(PaymentGateway):
    """
    Chapa payment gateway implementation.

    Supports:
    - Payment initialization (redirects to Chapa checkout)
    - Payment verification
    - Webhook signature verification
    """

    BASE_URL = 'https://api.chapa.co/v1'

    def __init__(self):
        self.secret_key = getattr(settings, 'CHAPA_SECRET_KEY', '')
        self.public_key = getattr(settings, 'CHAPA_PUBLIC_KEY', '')
        self.webhook_secret = getattr(settings, 'CHAPA_WEBHOOK_SECRET', '')
        self.base_url = getattr(settings, 'CHAPA_BASE_URL', self.BASE_URL)

        if not self.secret_key:
            logger.warning("CHAPA_SECRET_KEY not configured")

    def _get_headers(self):
        return {
            'Authorization': f'Bearer {self.secret_key}',
            'Content-Type': 'application/json',
        }

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
        """
        Initialize payment with Chapa.
        Returns a checkout URL to redirect the customer.
        """
        # Split name for Chapa API
        name_parts = customer_name.strip().split(' ', 1)
        first_name = name_parts[0]
        last_name = name_parts[1] if len(name_parts) > 1 else ''

        payload = {
            'amount': str(amount),
            'currency': currency,
            'tx_ref': tx_ref,
            'first_name': first_name,
            'last_name': last_name,
            'phone_number': customer_phone.replace('+', ''),
            'email': customer_email or f'{tx_ref}@ethiouber.com',
            'description': description or 'Ethio-Uber Delivery Payment',
            'callback_url': callback_url,
            'return_url': return_url,
            'customization': {
                'title': 'Ethio-Uber',
                'description': description,
            },
        }

        try:
            response = requests.post(
                f'{self.base_url}/transaction/initialize',
                headers=self._get_headers(),
                json=payload,
                timeout=30
            )
            response_data = response.json()

            if response.status_code == 200 and response_data.get('status') == 'success':
                checkout_url = response_data.get('data', {}).get('checkout_url', '')
                return PaymentInitResponse(
                    success=True,
                    gateway_reference=tx_ref,
                    checkout_url=checkout_url,
                    message='Payment initialized successfully',
                    raw_response=response_data,
                )
            else:
                message = response_data.get('message', 'Payment initialization failed')
                logger.error(f"Chapa init failed: {response_data}")
                return PaymentInitResponse(
                    success=False,
                    message=message,
                    raw_response=response_data,
                )

        except requests.exceptions.Timeout:
            logger.error("Chapa payment initialization timed out")
            return PaymentInitResponse(success=False, message='Payment gateway timeout')
        except requests.exceptions.RequestException as e:
            logger.error(f"Chapa request error: {e}")
            return PaymentInitResponse(success=False, message=str(e))

    def verify_payment(self, tx_ref: str) -> PaymentVerifyResponse:
        """
        Verify payment status using transaction reference.
        GET /v1/transaction/verify/{tx_ref}
        """
        try:
            response = requests.get(
                f'{self.base_url}/transaction/verify/{tx_ref}',
                headers=self._get_headers(),
                timeout=30
            )
            response_data = response.json()

            if response.status_code == 200 and response_data.get('status') == 'success':
                data = response_data.get('data', {})
                chapa_status = data.get('status', '').lower()

                if chapa_status == 'success':
                    return PaymentVerifyResponse(
                        success=True,
                        status='COMPLETED',
                        amount=float(data.get('amount', 0)),
                        currency=data.get('currency', 'ETB'),
                        message='Payment verified successfully',
                        raw_response=response_data,
                    )
                elif chapa_status in ['failed', 'error']:
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
                        message=f'Payment status: {chapa_status}',
                        raw_response=response_data,
                    )
            else:
                return PaymentVerifyResponse(
                    success=False,
                    status='FAILED',
                    message=response_data.get('message', 'Verification failed'),
                    raw_response=response_data,
                )

        except requests.exceptions.Timeout:
            logger.error(f"Chapa verification timed out for {tx_ref}")
            return PaymentVerifyResponse(success=False, status='PENDING', message='Gateway timeout')
        except Exception as e:
            logger.error(f"Chapa verification error for {tx_ref}: {e}")
            return PaymentVerifyResponse(success=False, status='PENDING', message=str(e))

    def verify_webhook_signature(self, payload: bytes, signature: str) -> bool:
        """
        Verify Chapa webhook signature using HMAC-SHA256.

        Args:
            payload: Raw request body bytes
            signature: Chapa-Signature header value

        Returns:
            True if signature is valid
        """
        if not self.webhook_secret:
            logger.warning("CHAPA_WEBHOOK_SECRET not configured - skipping signature verification")
            return True

        expected = hmac.new(
            self.webhook_secret.encode(),
            payload,
            hashlib.sha256
        ).hexdigest()

        return hmac.compare_digest(expected, signature)
