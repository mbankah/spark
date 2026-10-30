# Phone to PC protocol, version 1

How the phone tells Spark Guard (the Windows service) when to block (ledger A2, A4, A6, K4, K5, K8, K9). The phone is the source of truth. The PC only enforces.

## Pairing

Spark Guard makes a random 24-byte pairing code the first time it starts, stores it in `C:\ProgramData\SparkGuard\pairing.code` (administrators and SYSTEM only), and the installer shows it with the PC address. The person types both into the phone once (Setup, Link a Windows PC). The code is the HMAC key. It never travels over the network.

## Sending a state

`POST http://<pc address>:47831/state` with the body below and the header `X-Spark-Signature: <lower-case hex of HMAC-SHA256(key = pairing code as UTF-8, message = the exact body bytes)>`.

```json
{"v":1,"issuedAtUtc":"2026-11-02T10:00:00Z","domains":["youtube.com","youtu.be"],"windows":[{"start":"2026-11-02T09:00:00Z","end":"2026-11-02T12:00:00Z","why":"Work block"}]}
```

- `domains`: plain domain names only. The service writes the root, `www.` and `m.` forms to the hosts file (a hosts file has no wildcards).
- `windows`: when those domains are blocked, in UTC. The phone computes them from its own plan for the next 36 hours, once a minute, using the same decision as its apps. A wall allowance on the phone does not appear here (K9).
- The signature is over the raw bytes, so no one has to agree how JSON is laid out.

Answers: `200` accepted, `400` bad signature or malformed (nothing changes), `409` the same or an older state (a replay), `413` body over 64 KB.

## What the service does

- Keeps the last accepted state on disk. After a restart or reboot it enforces it at once (K6).
- Every second, makes the hosts file match: Spark's lines (between `# >>> Spark Guard` and `# <<< Spark Guard <<<`) are present only inside a window, and everything else in the file is left as it was (K4, K7).
- If the file differs from what it must be, rewrites it and, when someone else changed it, logs an `attempt` (K5). The log keeps 90 days.
- Gives only SYSTEM write permission on the hosts file. An administrator can still change permissions, which is why the put-back also runs every second.
- Logs `started` and `stopped` (K6). The installer sets Windows to restart the service if it dies.
- `GET http://127.0.0.1:47831/state` (this PC only) tells the browser extension `{blocked, domains, until}` and never the code (K8).

## Shared test vector

Key `spark-test-key`, body `{"v":1,"issuedAtUtc":"2026-11-02T10:00:00Z","domains":["youtube.com"],"windows":[]}` gives signature `9718e6f8a4c623d0f0b007ca4b1015f3e9d133bc76f8054245a826b4f4d55052`. Both the phone's tests (`PcSyncTest`) and the service's tests (`GuardTests`) check it.

## Proof that both sides agree

`tools/pc-link-e2e.sh` starts the real service against a temporary hosts file, runs the phone's tests from the emulator against it (the emulator reaches this computer as 10.0.2.2), and checks that the service wrote the block. It never touches the real hosts file.

## Limits, plainly

An administrator can stop or uninstall the service (Windows has no way to ask for a typed phrase first); it is logged when it starts again. A VPN, a proxy or encrypted browser DNS can go around a hosts file, which is what the extension is for. A work block cannot be started from the PC: the plan decides, on both devices (A4 read as "the same plan applies to both"). The traffic is plain HTTP on the home network, signed but not encrypted, so a person on that network could read the plan.
