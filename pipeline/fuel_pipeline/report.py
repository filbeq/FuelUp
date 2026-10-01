"""Count what the parsers drop or fix, so every run explains itself in the logs."""

import logging
from collections import Counter

SAMPLE_SIZE = 5


class DropReport:
    """Per-file tally of dropped rows and notable-but-kept rows, by reason."""

    def __init__(self, name: str):
        self.name = name
        self.rows_read = 0
        self.dropped: Counter[str] = Counter()
        self.notes: Counter[str] = Counter()
        self._samples: dict[str, list[str]] = {}

    def drop(self, reason: str, ident: object) -> None:
        self.dropped[reason] += 1
        self._sample(reason, ident)

    def note(self, reason: str, ident: object) -> None:
        """Record something unusual about a row that was kept."""
        self.notes[reason] += 1
        self._sample(reason, ident)

    @property
    def dropped_total(self) -> int:
        return self.dropped.total()

    def samples(self, reason: str) -> list[str]:
        return self._samples.get(reason, [])

    def _sample(self, reason: str, ident: object) -> None:
        samples = self._samples.setdefault(reason, [])
        if len(samples) < SAMPLE_SIZE:
            samples.append(str(ident))

    def log(self, logger: logging.Logger) -> None:
        logger.info(
            "%s: %d rows read, %d dropped", self.name, self.rows_read, self.dropped_total
        )
        for reason, count in self.dropped.most_common():
            logger.info("  dropped %6d  %s  e.g. %s", count, reason, ", ".join(self.samples(reason)))
        for reason, count in self.notes.most_common():
            logger.info("  noted   %6d  %s  e.g. %s", count, reason, ", ".join(self.samples(reason)))
