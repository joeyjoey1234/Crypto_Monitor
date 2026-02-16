# F-Droid Crypto Monitor (Android MVP)

Android app MVP for monitoring crypto prices, charting recent history, reading wallet balances, and generating buy/sell alerts from five popular technical-analysis algorithms.

## What this build includes

- Jetpack Compose UI with per-asset cards and price chart
- Single wallet input field with auto-chain detection (BTC/ETH/SOL/DOGE/ADA)
- Dynamic Base token discovery from wallet balances (non-zero holdings)
- Portfolio summary header (total value + 24h change)
- Market data from CoinGecko public API
- Wallet address balance lookup via Blockchair public API
- 5 signal algorithms per asset:
  - SMA crossover
  - RSI
  - MACD crossover
  - Bollinger Bands
  - Rate of Change (momentum)
- Weighted vote to produce final `BUY`, `SELL`, or `HOLD`
- Background checks every 15 minutes using WorkManager
- Local notifications when an asset changes to BUY or SELL
- In-app updater:
  - Checks GitHub latest release from `joeyjoey1234/Crypto_Monitor`
  - Compares release tag against current app version
  - Downloads newest APK asset and launches installer when newer
- Settings screen includes updater actions and donation link to
  `https://buymeacoffee.com/joejoe1234`

## Project structure

- `app/src/main/java/com/fdroid/cryptomonitor/data`: models, API clients, repository
- `app/src/main/java/com/fdroid/cryptomonitor/domain`: signal engine
- `app/src/main/java/com/fdroid/cryptomonitor/ui`: Compose UI + ViewModel
- `app/src/main/java/com/fdroid/cryptomonitor/scheduling`: worker + notifications
- `app/src/main/java/com/fdroid/cryptomonitor/storage`: DataStore preferences

## Build

1. Install Android Studio Ladybug+ and Android SDK 35.
2. Open this folder as a project.
3. Sync Gradle and run `app` on a device/emulator (Android 8+).

## Notes and limitations

- Wallet lookup endpoint support depends on chain/address compatibility in Blockchair.
- App stores per-chain addresses internally but entry is done through one auto-detect input bar.
- Graph cards are shown only for assets where detected wallet balance is greater than zero.
- Notification delivery on Android 13+ requires notification permission.
- APK auto-update install requires allowing install from unknown sources for this app on Android 8+.
- API rate limits can affect refresh cadence.
- Signals are informational only and can be wrong.

## Safety

This app does not provide financial advice. Always validate strategy/risk independently before trading.
