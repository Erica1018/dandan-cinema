# Install Dandan Cinema

[中文安装说明](INSTALL.zh-CN.md) · [Project](../README.md)

## 1. Start with the computer library

1. Install [Python 3.11 or newer](https://www.python.org/downloads/windows/). Enable its PATH option during installation. Close and reopen your terminal afterward.
2. Download the source ZIP from the [release page](https://github.com/Erica1018/dandan-cinema/releases). Extract it into a folder you can keep, such as `C:\dandan-cinema`. Do not run it inside the ZIP.
3. Open that folder in File Explorer. Right-click its empty space and choose **Open in Terminal**. Run `python --version` to verify Python is available.
4. Run `python run.py`, or double-click `Start.cmd`.
5. Open `http://127.0.0.1:18776` in your browser. Choose your language at the top right, then **Add to watchlist**. A title alone is enough.

Keep this computer and the service running while using the TV. Ctrl+C in the terminal stops the service. Your library is saved under `runtime/watchroom/`; restarting does not erase it. To back up, stop the service and copy this directory somewhere private. Exporting the library from the UI exports metadata, not media files; there is no UI import yet.

No Python packages are needed for this basic path. MCP and artwork preparation have optional dependencies.

## 2. Optional Android TV setup

This preview uses developer-mode installation, not an app-store installer. Android 5+ is the declared minimum; newer TVs may reject the legacy target SDK. Do not bypass your device's security policy. A successful installation does not establish playback compatibility.

1. Put the computer and TV on the same trusted private network. Find the computer's IPv4 address in Windows **Settings → Network & Internet → your connection → Properties**. Find the TV address in its network settings. Router address reservations help keep both addresses stable.
2. Stop `run.py` with Ctrl+C. In the project terminal, create a pairing configuration. Replace the sample addresses with your own:

   ```powershell
   python setup_tv.py --host 192.168.1.20 --tv-host 192.168.1.30
   ```

   These are examples, not addresses the app discovers. A random pairing token is stored locally in `runtime/watchroom/tv-access.json`; never share that file.
3. Run `python run.py` again. If Windows requests firewall access, allow it only for your private network. If a managed firewall still blocks the TV, ask its administrator to allow TCP **18777 from your TV address only**. Never forward these ports on your router.
4. Download the APK from the release page. Install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) on the computer. Enable developer/network debugging on the TV using its manufacturer's instructions. The app does not change these settings for you.
5. Note the TV's ADB address and port. Some TVs use `5555`; newer wireless-debugging systems require a separate `adb pair` step and show a different port. Complete that pairing as instructed on the TV.
6. Run the following, replacing all three example paths/addresses. Accept the computer authorization prompt on the TV if shown; rerun if the first attempt stops awaiting authorization.

   ```powershell
   python watchroom-tv/install.py --adb "C:\platform-tools\adb.exe" --apk "C:\Downloads\dandan-cinema-0.1.0-preview.1.apk" --device 192.168.1.30:5555
   ```

   This targets only the configured TV, installs the new public package and passes its pairing information without printing the token. It never removes another cinema app.
7. On the TV, confirm your library appears. Open **Library tools → Playback test**. Test play/pause, seek and Back. Report device model, Android version and the actual result, without IPs or tokens. Disable network debugging when installation is finished; normal playback uses the application port.

The TV follows the system language initially. **Library tools → 中文 / English** changes the app language. Changing the network addresses requires stopping the service and carefully replacing the pairing configuration. To pair a TV to a different computer, clear only this app's TV data in Android settings, then reinstall/pair; this clears its local resume positions and cache, not the computer library.

## 3. Prepare a local video

A platform/cloud page is a reference. A direct source must point to playable video, and can still fail due to login, expiry, codec or network restrictions. To serve your own local video:

1. Obtain [FFmpeg and ffprobe](https://ffmpeg.org/download.html) yourself; keep them in the same directory. The project does not bundle or download them.
2. Add the title in the browser. Get its stable ID with `python watchroom/agent.py list`.
3. Convert an authorized local video, optionally supplying UTF-8 SRT tracks:

   ```powershell
   python watchroom/prepare_media.py "C:\Videos\my-video.mp4" --ffmpeg "C:\ffmpeg\bin\ffmpeg.exe" --zh "C:\Videos\zh.srt" --en "C:\Videos\en.srt"
   ```

   Omit either subtitle argument when unavailable. Select the correct audio stream with `--audio 0` (the default). This copies or converts into the managed media folder and prints an `asset` ID. It requires sufficient disk space; it does not mark the film verified.
4. Check the actual film identity, beginning/end and completeness. Copy `examples/evidence.json` to `runtime/evidence.json` and open the copy in a text editor. Set `identity` and `complete` to `true` only after those checks, and replace `evidence` with what you actually verified. Leave other checks false unless verified. Register the asset using the item ID and this JSON file:

   ```powershell
   python watchroom/agent.py publish ITEM_ID --asset ASSET_ID --evidence runtime/evidence.json
   ```

   This command records your assertion of identity/completeness. Do not run it before those checks. It does not independently establish them or subtitle/audio quality. Optional audio/subtitle assertions are exposed by MCP; never invent them.
5. Refresh the TV and test this particular title, including later-position seeking. A passing test clip does not establish that every film works.

Deletion removes the card and its exclusively registered local files after a preview. Shared files remain. Originals outside the managed data directory are not enrolled automatically; platform/cloud originals are never deleted. Keep source backups before testing media workflows.

## Troubleshooting

- **Python not found:** reopen the terminal after installing Python; verify the PATH option.
- **Port already in use:** close an older copy. For another instance, set `$env:DANDAN_PORT='18876'` and `$env:DANDAN_DATA_DIR='C:\dandan-data-two'` before starting. Set that same data directory for preparation/setup commands, and point CLI/MCP `DANDAN_URL` to the new port. Do not run multiple services against one data directory.
- **TV offline:** keep the PC awake, check its current IP, the TV IP, private-network firewall, and that `run.py` is still running. An offline cached library is not a live connection.
- **App installs but video fails:** run the test clip first, then check codec/URL expiry and media preparation. This preview has no DRM or provider login adapter.
- **MCP cannot connect:** start `run.py`, use the virtual environment's absolute Python path, and read [agent setup](AGENTS.md).
