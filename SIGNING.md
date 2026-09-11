# Permanent signing and in-place upgrades

Future versions must install over the existing app without uninstalling.

## Permanent identity

- Application ID: `com.gsbtechnologies.lotto642modeltracker`
- Release key alias: `lotto642_release`
- Release certificate SHA-256 fingerprint:
  `02:69:90:89:80:31:25:79:48:67:A5:3D:5C:63:F9:F9:E5:4C:5A:1F:12:BE:13:3F:2F:0C:8A:32:07:DB:8E:D5`

Any future APK intended as an in-place update must be signed by this same certificate.

## GitHub Actions repository secrets

The signed release workflow reads these four repository secrets:

- `ANDROID_KEYSTORE_B64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The private keystore itself must never be committed to Git. Keep at least two secure offline/cloud backups of the `.p12` file and the credentials.

## Release rules

1. Keep application ID `com.gsbtechnologies.lotto642modeltracker` forever.
2. Increment `versionCode` for every release.
3. Sign every permanent release APK with the same permanent release key.
4. Do not distribute CI debug APKs as permanent installations; GitHub-hosted runners can use different debug signing keys.
5. Add a tested Room migration for every schema version increase; never enable destructive migration for production data.
6. Use `.github/workflows/release.yml` for signed APKs.

## First permanent installation

The existing debug installation cannot be updated by the new release key. Make an `.l642` backup from the currently installed debug build first. Then uninstall the debug build once, install the first permanently signed release, and restore the backup. After that transition, future permanently signed releases can update in place as long as the application ID and signing certificate stay unchanged and `versionCode` increases.
