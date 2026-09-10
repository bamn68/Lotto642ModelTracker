# Permanent signing and in-place upgrades

For the user's requirement that future versions install **over** the existing app without uninstalling:

1. Keep application ID `com.gsbtechnologies.lotto642modeltracker` forever.
2. Increment `versionCode` for every release.
3. Sign the first distributed APK and every later APK with the **same permanent release key**.
4. Store the keystore outside Git. Keep at least two secure backups of it.
5. Do not distribute CI debug APKs as the permanent install; ephemeral CI debug keys can differ between runners.
6. Add a tested Room migration for every schema version increase; never enable destructive migration for production data.

The repository intentionally contains no private signing key.
