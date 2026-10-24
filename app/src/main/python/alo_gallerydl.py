"""Bridge between ALO (Kotlin, via Chaquopy) and gallery-dl.

gallery-dl keeps global state (config, logging, sys.argv), so runs are serialised with a
lock. Output is forwarded line by line to a Java callback object with two methods:
``onThread(int)`` and ``onLine(str stream, str line)``.
"""

import ctypes
import io
import logging
import os
import sys
import threading

_lock = threading.Lock()


def _use_updated_wheel(wheel_dir):
    """Puts an in-app updated gallery-dl wheel in front of the bundled copy (or removes it)."""
    if not wheel_dir:
        return
    wheels = []
    if os.path.isdir(wheel_dir):
        wheels = sorted(f for f in os.listdir(wheel_dir) if f.startswith("gallery_dl-") and f.endswith(".whl"))
    wanted = [os.path.join(wheel_dir, wheels[-1])] if wheels else []
    current = [p for p in sys.path if p.startswith(wheel_dir)]
    if current == wanted:
        return
    sys.path[:] = wanted + [p for p in sys.path if not p.startswith(wheel_dir)]
    # Forget the old modules so the next import picks up the new location.
    for name in [m for m in sys.modules if m == "gallery_dl" or m.startswith("gallery_dl.")]:
        del sys.modules[name]


def version(wheel_dir=None):
    _use_updated_wheel(wheel_dir)
    from gallery_dl import version as v
    return v.__version__


class _LineWriter(io.TextIOBase):
    def __init__(self, callback, stream):
        self._cb = callback
        self._stream = stream
        self._buf = ""

    def writable(self):
        return True

    def write(self, text):
        self._buf += text
        while True:
            idx = min((i for i in (self._buf.find("\n"), self._buf.find("\r")) if i >= 0), default=-1)
            if idx < 0:
                break
            line, self._buf = self._buf[:idx], self._buf[idx + 1:]
            if line.strip():
                self._cb.onLine(self._stream, line)
        return len(text)

    def flush(self):
        if self._buf.strip():
            self._cb.onLine(self._stream, self._buf)
        self._buf = ""


def run(args, callback, wheel_dir=None):
    """Runs gallery-dl with ``args`` (list of str). Returns gallery-dl's exit code."""
    with _lock:
        _use_updated_wheel(wheel_dir)
        import gallery_dl
        from gallery_dl import config

        callback.onThread(threading.get_ident())
        saved = sys.argv, sys.stdout, sys.stderr
        out = _LineWriter(callback, "out")
        err = _LineWriter(callback, "err")
        sys.argv = ["gallery-dl"] + [str(a) for a in args]
        sys.stdout, sys.stderr = out, err
        root = logging.getLogger()
        for handler in list(root.handlers):
            root.removeHandler(handler)
        try:
            config.clear()
            code = gallery_dl.main()
        except KeyboardInterrupt:
            code = 130
        except SystemExit as exc:
            code = exc.code if isinstance(exc.code, int) else 1
            if not isinstance(exc.code, int) and exc.code:
                err.write(str(exc.code) + "\n")
        except Exception as exc:  # report instead of crashing the JVM thread
            err.write("[gallery-dl][error] %s: %s\n" % (type(exc).__name__, exc))
            code = 1
        finally:
            out.flush()
            err.flush()
            sys.argv, sys.stdout, sys.stderr = saved
        return int(code or 0)


def cancel(thread_id):
    """Interrupts a running ``run`` call by raising KeyboardInterrupt in its thread."""
    ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(thread_id), ctypes.py_object(KeyboardInterrupt))
