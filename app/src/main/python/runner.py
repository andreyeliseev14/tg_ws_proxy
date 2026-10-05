"""
Android entry point for the TG WS Proxy core (github.com/Flowseal/tg-ws-proxy).

The core lives in the `proxy` package, which the build pulls fresh from
Flowseal's repository. This file only adapts it to Android:
  * points the core's AES-CTR fallback at the OpenSSL bundled with Python,
    so no `cryptography` wheel is needed;
  * writes a log file the app can show;
  * passes settings from the app as command-line arguments.
"""
import ctypes
import ctypes.util
import glob
import logging
import logging.handlers
import os
import sys


def _libcrypto_candidates(native_dir):
    import ssl  # noqa: F401  -- loads Python's own libcrypto into the process
    found = []
    try:
        with open("/proc/self/maps") as f:
            for line in f:
                path = line.split()[-1]
                if "libcrypto" in os.path.basename(path) and path.startswith("/"):
                    if path not in found:
                        found.append(path)
    except OSError:
        pass
    found += sorted(glob.glob(os.path.join(native_dir, "libcrypto*.so")))
    found += ["libcrypto_chaquopy.so", "libcrypto.so.3", "libcrypto.so.1.1", "libcrypto.so"]
    return found


def _setup_crypto(native_dir):
    try:
        import cryptography.hazmat.primitives.ciphers  # noqa: F401
        return "cryptography"
    except ImportError:
        pass

    chosen = None
    for cand in _libcrypto_candidates(native_dir):
        try:
            lib = ctypes.CDLL(cand)
            lib.EVP_aes_256_ctr  # make sure it is real OpenSSL
            chosen = cand
            break
        except (OSError, AttributeError):
            continue

    original = ctypes.util.find_library

    def find_library(name):
        if name == "crypto" and chosen:
            return chosen
        return original(name)

    ctypes.util.find_library = find_library
    return chosen or "not found"


def run(port, secret, dc_ips, log_path, native_dir):
    handler = logging.handlers.RotatingFileHandler(
        str(log_path), maxBytes=512 * 1024, backupCount=1, encoding="utf-8")
    handler.setFormatter(logging.Formatter(
        "%(asctime)s  %(levelname)-5s  %(message)s", datefmt="%H:%M:%S"))
    root = logging.getLogger()
    root.addHandler(handler)
    root.setLevel(logging.INFO)
    log = logging.getLogger("android")

    log.info("Android: AES backend = %s", _setup_crypto(str(native_dir)))

    argv = ["tg-ws-proxy", "--host", "127.0.0.1",
            "--port", str(port), "--secret", str(secret)]
    ips = [x.strip() for x in str(dc_ips).split(",") if x.strip()]
    if ips:
        for ip in ips:
            argv += ["--dc-ip", ip]
    else:
        argv += ["--dc-ip"]  # empty list = no DC overrides
    sys.argv = argv

    try:
        from proxy.tg_ws_proxy import main
        main()
    except SystemExit as e:
        log.error("Proxy exited: %s", e)
    except BaseException:
        log.exception("Proxy crashed")
