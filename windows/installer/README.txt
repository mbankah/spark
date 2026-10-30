SPARK GUARD
===========

What it is
  The Windows part of Spark. During your work blocks it blocks the websites of the apps you blocked on your phone, on this PC, in every browser, by editing the
  hosts file. If anyone changes the hosts file it is put back within about a second, and each attempt is logged. The log is kept for 90 days.

Link your phone
  1. On your phone: Spark, Setup, Link a Windows PC.
  2. Type the PC address and the pairing code the installer showed. You can show the code again from the Start menu: Spark Guard, Show the pairing code.
  3. Phone and PC must be on the same home network. The phone sends the plan for the next 36 hours every minute. If the phone is away, the PC keeps blocking
     by the last plan it was given.

Second layer: the browser extension (Chrome, Edge, Brave)
  Some browsers use encrypted DNS and skip the hosts file. The extension blocks the same sites inside the browser.
  1. Open chrome://extensions (or edge://extensions) and switch on Developer mode.
  2. Choose "Load unpacked" and pick the folder:  <install folder>\extension
  3. It only asks Spark Guard on this PC what is blocked. It reads no pages and sends nothing anywhere.
  (A browser store listing would need a developer account and review, so it is loaded by hand.)

What it cannot do, plainly
  - An administrator can stop or uninstall the service. That is logged when it starts again, but Windows gives no way to ask for a typed phrase first.
  - A VPN, a proxy, or a browser on encrypted DNS can go around the hosts file. The extension is the second layer for this.
  - A different browser (Firefox, for example) is covered by the hosts file but not by the extension in this version.
  - Passing the typed wall on your phone does not open the PC.

Uninstall
  Settings, Apps, Spark Guard, Uninstall. It stops the service, removes Spark's lines from the hosts file and gives the file its normal permissions back.

Where things are
  Program:   <install folder>
  Data, log: C:\ProgramData\SparkGuard   (administrators only)
