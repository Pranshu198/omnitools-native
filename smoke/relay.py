#!/usr/bin/env python3
"""Local CONNECT relay for the JVM smoke harness ONLY.

The sandbox egress proxy wants preemptive Basic auth on CONNECT; the
JVM's HttpURLConnection only answers 407 challenges and chokes on this
proxy's response (NoSuchElementException in doTunneling). This relay
listens on 127.0.0.1:18080, CONNECTs upstream with the credentials from
the https_proxy env var (read in-process, never printed), then pipes
bytes both ways. TLS stays end-to-end between the JVM and the real
server — the relay never sees plaintext.
"""
import base64
import os
import socket
import threading
import urllib.parse

UP = urllib.parse.urlparse(os.environ["https_proxy"])
UP_HOST, UP_PORT = UP.hostname, UP.port or 3128
AUTH = base64.b64encode(f"{UP.username}:{UP.password}".encode()).decode()
LISTEN = ("127.0.0.1", 18080)


def pipe(a, b):
    try:
        while True:
            data = a.recv(65536)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        try:
            b.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def handle(client):
    try:
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = client.recv(4096)
            if not chunk:
                client.close()
                return
            buf += chunk
        first = buf.split(b"\r\n", 1)[0].decode("latin1")
        parts = first.split(" ")
        if len(parts) < 2 or parts[0].upper() != "CONNECT":
            client.sendall(b"HTTP/1.1 405 Only CONNECT\r\n\r\n")
            client.close()
            return
        target = parts[1]
        print("CONNECT", target, flush=True)
        up = socket.create_connection((UP_HOST, UP_PORT), timeout=20)
        req = (f"CONNECT {target} HTTP/1.1\r\nHost: {target}\r\n"
               f"Proxy-Authorization: Basic {AUTH}\r\n"
               "Proxy-Connection: Keep-Alive\r\n\r\n").encode()
        up.sendall(req)
        resp = b""
        while b"\r\n\r\n" not in resp:
            chunk = up.recv(4096)
            if not chunk:
                raise OSError("upstream closed during CONNECT")
            resp += chunk
        status = resp.split(b"\r\n", 1)[0].decode("latin1")
        print("upstream:", status, "resp-headers:", resp.split(b"\r\n\r\n", 1)[0].decode("latin1").replace("\r\n", " | "), flush=True)
        if " 200" not in status:
            client.sendall(b"HTTP/1.1 502 Upstream CONNECT failed\r\n\r\n")
            up.close()
            client.close()
            return
        client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        # Any bytes already read past the headers (rare) belong to the tunnel.
        extra = resp.split(b"\r\n\r\n", 1)[1]
        if extra:
            client.sendall(extra)
        t = threading.Thread(target=pipe, args=(client, up), daemon=True)
        t.start()
        pipe(up, client)
        up.close()
        client.close()
    except Exception:
        try:
            client.close()
        except OSError:
            pass


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(LISTEN)
    srv.listen(64)
    print(f"relay listening on {LISTEN[0]}:{LISTEN[1]}", flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


if __name__ == "__main__":
    main()
