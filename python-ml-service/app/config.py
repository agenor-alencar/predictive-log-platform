"""Configuration module for the ML service."""
import os


class Settings:
    """Application settings loaded from environment variables."""

    def __init__(self):
        self.DATABASE_URL: str = os.getenv(
            "DATABASE_URL",
            "postgresql://logadmin:logadmin123@localhost:5432/logplatform"
        )
        self.MLFLOW_TRACKING_URI: str = self._resolve_mlflow_uri()
        self.MODELS_DIR: str = os.getenv("MODELS_DIR", "./models")
        self.DATA_DIR: str = os.getenv("DATA_DIR", "./data")
        self.EXPERIMENT_NAME: str = "predictive-log-intelligence"
        self.RANDOM_STATE: int = 42
        self.TEST_SIZE: float = 0.2

    def _resolve_mlflow_uri(self) -> str:
        """Resolve hostname to IP to bypass DNS rebinding security in MLflow."""
        uri = os.getenv("MLFLOW_TRACKING_URI", "http://localhost:5000")
        if "localhost" in uri or "127.0.0.1" in uri:
            return uri
            
        try:
            from urllib.parse import urlparse, urlunparse
            import socket
            parsed = urlparse(uri)
            if parsed.hostname:
                ip = socket.gethostbyname(parsed.hostname)
                # Reconstruct URI with IP
                new_netloc = f"{ip}:{parsed.port}" if parsed.port else ip
                return urlunparse(parsed._replace(netloc=new_netloc))
        except Exception:
            pass
        return uri


settings = Settings()
