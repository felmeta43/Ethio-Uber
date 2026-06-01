"""
Telebirr payment gateway integration.
Telebirr is Ethio Telecom's mobile payment service.
"""
import hashlib
import hmac
import json
import logging
import time
import requests
from django.conf import settings

from .base import PaymentGateway, PaymentInitResponse, PaymentVerifyResponse

logger = logging.getLogger(__name__)


class TelebirrGateway(PaymentGateway):
    """
    Telebirr payment gateway implementation.
    Uses Telebirr's H5Pay API for in-app payments.
    """

    def __init__(self):
        self.app_id = getattr(settings, 'TELEBIRR_APP_ID', '')
        self.app_key = getattr(settings, 'TELEBIRR_APP_KEY', '')
        self.merchant_code = getattr(settings, 'TELEBIRR_MERCHANT_CODE', '')
        self.base_url = getattr(settings, 'TELEBIRR_BASE_URL', '')

        if not self.app_id:
            logger.warning("TELEBIRR_APP_ID not configured")

    def _generate_sign(self, params: dict) -> str:
        """Generate signature for Telebirr API requests."""
        # Sort keys alphabetically and create query string
        sorted_params = sorted(params.items())
        query_string = '&'.join(f"{k}={v}" for k, v in sorted_params if v is not None and v != '')
        query_string += f"&key={self.app_key}"

        # SHA256 hash
        return hashlib.sha256(query_string.encode('utf-8')).hexdigest().upper()

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
        """Initialize a Telebirr H5Pay payment."""
        timestamp = str(int(time.time() * 1000))

        params = {
            'appId': self.app_id,
            'merchantCode': self.merchant_code,
            'nonce': tx_ref[:32],
            'notifyUrl': callback_url,
            'outTradeNo': tx_ref,
            'returnUrl': return_url,
            'shortCode': self.merchant_code,
            'subject': description or 'Ethio-Uber Delivery',
            'timeoutExpress': '30',
            'timestamp': timestamp,
            'totalAmount': f"{amount:.2f}",
            'tradeType': 'h5Pay',
        }

        params['sign'] = self._generate_sign(params)

        try:
            response = requests.post(
                f'{self.base_url}/payment/gateway',
                json=params,
                timeout=30,
                verify=False  # Telebirr may use self-signed cert in some envs
            )
            response_data = response.json()

            if response_data.get('code') == '0':
                raw_data = response_data.get('data', {})
                return PaymentInitResponse(
                    success=True,
                    gateway_reference=tx_ref,
                    checkout_url=raw_data.get('toPayUrl', ''),
                    message='Telebirr payment initialized',
                    raw_response=response_data,
                )
            else:
                return PaymentInitResponse(
                    success=False,
                    message=response_data.get('msg', 'Telebirr initialization failed'),
                    raw_response=response_data,
                )

        except Exception as e:
            logger.error(f"Telebirr payment error: {e}")
            return PaymentInitResponse(success=False, message=str(e))

    def verify_payment(self, tx_ref: str) -> PaymentVerifyResponse:
        """Query Telebirr for payment status."""
        timestamp = str(int(time.time() * 1000))
        params = {
            'appId': self.app_id,
            'merchantCode': self.merchant_code,
            'outTradeNo': tx_ref,
            'timestamp': timestamp,
        }
        params['sign'] = self._generate_sign(params)

        try:
            response = requests.post(
                f'{self.base_url}/payment/queryTrade',
                json=params,
                timeout=30,
                verify=False
            )
            response_data = response.json()

            if response_data.get('code') == '0':
                data = response_data.get('data', {})
                trade_status = data.get('tradeStatus', '')
                if trade_status == 'TRADE_SUCCESS':
                    return PaymentVerifyResponse(
                        success=True,
                        status='COMPLETED',
                        amount=float(data.get('totalAmount', 0)),
                        message='Payment completed',
                        raw_response=response_data,
                    )
                elif trade_status in ['TRADE_FAIL', 'TRADE_CLOSED']:
                    return PaymentVerifyResponse(
                        success=False,
                        status='FAILED',
                        message='Payment failed or closed',
                        raw_response=response_data,
                    )
                else:
                    return PaymentVerifyResponse(
                        success=False,
                        status='PENDING',
                        message=f'Trade status: {trade_status}',
                        raw_response=response_data,
                    )
            else:
                return PaymentVerifyResponse(
                    success=False,
                    status='PENDING',
                    message=response_data.get('msg', 'Query failed'),
                    raw_response=response_data,
                )

        except Exception as e:
            logger.error(f"Telebirr verify error: {e}")
            return PaymentVerifyResponse(success=False, status='PENDING', message=str(e))
