"""
Abstract base class for payment gateways.
"""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional, Dict, Any


@dataclass
class PaymentInitResponse:
    """Response from initiating a payment."""
    success: bool
    gateway_reference: str = ''
    checkout_url: str = ''
    message: str = ''
    raw_response: Dict[str, Any] = field(default_factory=dict)


@dataclass
class PaymentVerifyResponse:
    """Response from verifying a payment."""
    success: bool
    status: str = 'PENDING'  # PENDING, COMPLETED, FAILED
    amount: Optional[float] = None
    currency: str = 'ETB'
    message: str = ''
    raw_response: Dict[str, Any] = field(default_factory=dict)


class PaymentGateway(ABC):
    """
    Abstract base class that all payment gateways must implement.
    Provides a consistent interface for initiating and verifying payments.
    """

    @abstractmethod
    def initiate_payment(
        self,
        amount: float,
        currency: str,
        tx_ref: str,
        customer_phone: str,
        customer_name: str,
        customer_email: str,
        description: str,
        callback_url: str,
        return_url: str,
        **kwargs
    ) -> PaymentInitResponse:
        """
        Initiate a payment with the gateway.

        Args:
            amount: Payment amount
            currency: Currency code (e.g., 'ETB')
            tx_ref: Unique transaction reference
            customer_phone: Customer's phone number
            customer_name: Customer's full name
            customer_email: Customer's email (may be optional for some gateways)
            description: Payment description
            callback_url: Webhook URL for payment status updates
            return_url: URL to redirect customer after payment
            **kwargs: Additional gateway-specific parameters

        Returns:
            PaymentInitResponse
        """
        pass

    @abstractmethod
    def verify_payment(self, tx_ref: str) -> PaymentVerifyResponse:
        """
        Verify a payment status with the gateway.

        Args:
            tx_ref: Transaction reference to verify

        Returns:
            PaymentVerifyResponse
        """
        pass

    def generate_tx_ref(self, order_number: str) -> str:
        """Generate a unique transaction reference for an order."""
        import uuid
        return f"EU-{order_number}-{uuid.uuid4().hex[:8].upper()}"
