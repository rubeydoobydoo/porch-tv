# Install Porch on a Chromecast with Google TV

This is for a box you own. Porch is not in the Play Store. You put the app on the TV yourself ("sideloading").

You need:

- A Chromecast with Google TV, on the same Wi-Fi as your computer.
- The Porch release file, `app-release.apk`. Build it with `./gradlew assembleRelease`. It lands in `app/build/outputs/apk/release/`. There is no ready-made download yet.
- Your playlist and guide details. Porch ships with none.

## 1. Turn on developer options (once)

1. On the TV's home screen, go to your picture or the cog in the top right corner and open **Settings**. Then choose **System**, then **About**.
2. Highlight **Android TV OS build** and press OK seven times. A message says you are a developer.
3. Go back to **Settings → System → Developer options**.
4. Turn on **USB debugging**.

## 2. Put the app on the TV

**With a computer (adb).** You need a free Google tool called adb. Download "SDK Platform-Tools" from https://developer.android.com/tools/releases/platform-tools and unzip it.

1. On the TV, find its address in **Settings → Network & Internet**, then your Wi-Fi name.
2. On the computer, open Terminal (Mac) or Command Prompt (Windows) in the unzipped folder, and copy `app-release.apk` into the same folder. Type these two lines, pressing Enter after each. Swap in your TV's address from step 1 and keep `:5555` on the end. On a Mac, put `./` in front of `adb`.

```bash
adb connect 192.168.x.x:5555
adb install -r app-release.apk
```

3. The TV may ask you to allow the computer. Choose **Always allow**, then run the install line again.
4. Run this once after each install. It makes Porch faster to open:

```bash
adb shell cmd package compile -m speed -f tv.porch.app
```

**With the Downloader app instead (no computer).** On the TV, install **Downloader** (by AFTVnews) from the Play Store. Open it and type the web address of the Porch app file. The first time, the TV says it can't install unknown apps: choose **Settings**, turn on **Downloader**, press Back, then choose **Install**.

Open **Porch** from the Apps row on the home screen. If Porch is not in the Apps row, open **Settings → Apps → See all apps** and choose Porch, then **Open**. The first screen is **Add a source**.

## 3. Add the playlist

Porch starts on **Add a source**. Pick **Playlist URL** or **Xtream login**, fill in the boxes and choose **Save and import**. Typing on a remote is slow, so choose **Start phone setup** and fill the form in on a phone on the same Wi-Fi. The page closes when you leave the screen. Only do this on a network you trust.

## Setting it up for Dad

Do this at Dad's place, or over a call with his TV on.

1. Install Porch (steps 1 and 2). Do it while you are on his Wi-Fi.
2. Add his source with the phone setup, so nothing is typed on the remote. Use the login for their own service.
3. Wait for the channels to appear. The first fetch of a big playlist can take a few minutes.
4. Walk through **Pick your channels** so his favourites are already there.
5. If the guide is an hour or two out, fix it in **Settings → Guide offset** with Left and Right.
6. Turn on **Settings → Simple mode** and choose a 4-digit code. Home then shows only his favourites, and Porch opens straight into the last channel he watched. Write the code down somewhere safe: if it is forgotten, the only way out is clearing Porch's data, which also removes his login.
7. A welcome card shows him the three keys he needs: Up and Down change channel, OK shows what's on, Back goes to his list. Press **Start watching** together.
8. To leave simple mode later: press Back on his favourites list. On the **Leave Porch?** card choose **Simple mode off**, then type the code.
9. Turn USB debugging back off if you like. The app keeps working.

## Updating

Run the install line again with the new file. `-r` keeps his login and favourites. Never install an older build over a newer one: that wipes the app's data.

## If something goes wrong

- **adb says unauthorised.** Look at the TV for the allow prompt.
- **adb can't connect.** Check USB debugging is on and both devices share Wi-Fi.
- **No channels after saving.** Check the address and login. Porch says the HTTP code, not the address.
- **A stream stops.** Porch tries again on its own. Use **Retry** on the overlay for another go.
