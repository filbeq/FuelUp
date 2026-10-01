"""Download the two MIMIT files."""

import logging
import time
import urllib.request
from pathlib import Path

# Verified on the official dataset page on 2026-10-01:
# https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
STATIONS_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"

USER_AGENT = "fuel-up-pipeline/0.1 (open data reuse; Python urllib)"
TIMEOUT_SECONDS = 60
ATTEMPTS = 3
RETRY_DELAY_SECONDS = 10

log = logging.getLogger(__name__)


def download(url: str, dest: Path, attempts: int = ATTEMPTS, retry_delay: float = RETRY_DELAY_SECONDS) -> Path:
    """Save ``url`` to ``dest``, retrying a few times on network errors."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                payload = response.read()
            break
        except OSError as error:  # URLError, HTTPError and timeouts are all OSErrors
            log.warning("download of %s failed (attempt %d/%d): %s", url, attempt, attempts, error)
            if attempt == attempts:
                raise
            time.sleep(retry_delay * attempt)

    tmp = dest.with_suffix(dest.suffix + ".tmp")
    tmp.write_bytes(payload)
    tmp.replace(dest)
    log.info("downloaded %s (%d bytes)", url, len(payload))
    return dest
