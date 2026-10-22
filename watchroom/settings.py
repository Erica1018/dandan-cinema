"""Instance paths shared by editor, TV service, preparation and adapters."""
import os
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
DATA = Path(os.environ.get('DANDAN_DATA_DIR', str(PROJECT / 'runtime' / 'watchroom'))).expanduser().resolve()
EDITOR_PORT = int(os.environ.get('DANDAN_PORT', '18776'))
VERSION = '0.1.0-preview.1'
