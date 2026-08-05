"""Agrupa middlewares HTTP propios de la aplicacion.

Pasos:
- Expone componentes reutilizables de transporte.
- Mantiene aislada la logica transversal del request pipeline.
"""

from app.middleware.correlation import CorrelationIdMiddleware
from app.middleware.request_size import RequestSizeLimitMiddleware

__all__ = ["CorrelationIdMiddleware", "RequestSizeLimitMiddleware"]
