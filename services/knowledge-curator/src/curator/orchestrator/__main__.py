"""``python -m curator.orchestrator`` entrypoint."""

from __future__ import annotations

import sys

from curator.orchestrator.runner import main

if __name__ == "__main__":
    sys.exit(main())
