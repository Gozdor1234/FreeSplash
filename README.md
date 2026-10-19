# FreeSplash

Personal Android browser for Unsplash that shows **only free photos**. Unsplash+ (paywalled, watermarked) photos are filtered out.

## How filtering works

A photo is shown only if it passes both checks:

1. Its `plus` / `premium` flags are not `true`.
2. None of its image URLs are served from `plus.unsplash.com`.

Check 2 keeps working even if Unsplash renames or drops the flags. The number of hidden photos is shown under the app title so you can see the filter working.

## Data sources

| Source | When used | Notes |
|---|---|---|
| Official API (`api.unsplash.com`) | You saved an access key in Settings | Documented and stable. Demo keys allow 50 requests/hour (30 photos per request). |
| Internal site endpoint (`unsplash.com/napi`) | No key saved, or the official API returns 401/403/429 | Undocumented. Can change or break with no notice. |

Get a free key at https://unsplash.com/oauth/applications (New Application, copy the **Access Key**, not the Secret Key).

## Features

- Editorial feed and search, 2-column staggered grid, infinite scroll, pull to refresh
- Full-screen viewer with pinch zoom
- Download to `Pictures/FreeSplash`, set as home/lock wallpaper, share, open on Unsplash
- Photographer attribution with links (required by Unsplash's API guidelines)
- Download tracking ping to Unsplash when a key is set (also required)
- Light/Dark/System theme and Material You colors

## Building

GitHub Actions builds the APK on every push to `main`. Download it from the **latest** release on the repo page.

The signing key (`app/signing.keystore`) is committed on purpose so each new build installs over the previous one. That is fine for a personal sideloaded app. Do not reuse it for anything published.

## Caveats

- Unsplash's API guidelines prohibit unofficial clients that replicate the core Unsplash experience. This app is for personal use only and must not be published.
- Requires Android 10 or newer.
