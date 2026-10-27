# RepoPilot

RepoPilot is an Android agent for GitHub repository updates, with optional OpenRouter assistance.

## Main capabilities
- **Update a repository from ZIP:** validates archive paths, skips `.git` paths, limits ZIP extraction to 80 MB / 500 files / 24 MB per file, uploads blobs, and creates one Git commit for the full update.
- **Publish an APK or ZIP as a GitHub Release asset:** creates or reuses the release tag and uploads the selected file as an asset. Use this for large APKs rather than committing APK binaries to the Git tree.
- **OpenRouter helper:** drafts commit messages and release notes from the text you provide. It does not receive the GitHub token or the selected file contents.
- **Credential protection:** GitHub and OpenRouter keys are encrypted with AES-GCM using a key held by Android Keystore. Keys are never included in project files.
- Arabic RTL UI, dark Material 3 styling, custom launcher icon, file picker, progress messages, and GitHub token test.
- Unit tests cover safe ZIP path prefixing, Windows path separators, `.git` filtering, and traversal rejection.

## Important behavior and limits
- A ZIP update requires a `.zip` file and changes/adds files in the target branch. It does not delete repository files missing from the ZIP.
- Existing paths in the target branch are replaced by the ZIP files. Review the target repository and branch before confirming.
- ZIP entries with traversal paths are rejected; `.git` entries are ignored.
- The ZIP commit supports up to 500 files, 80 MB total extracted content, and 24 MB per individual file. Large APK/ZIP binaries should use Release mode.
- Release mode may replace a same-named asset on an existing release. Ensure the tag is the intended release tag.
- A GitHub token needs repository Contents write permission for the chosen repository. Release publishing also requires permission to create/edit releases. Do not share tokens.
- The app uses the OpenRouter key only for AI requests. Model availability and pricing are controlled by OpenRouter and the selected provider.
- The AI helper does not execute code, change repositories, or upload anything on its own.

## Build and upload using GitHub from a phone
1. Create a new GitHub repository for this source. Upload all root-level flat files from this bundle; GitHub's browser upload works best with the files extracted first.
2. Open `.github__workflows__build.yml` in the repository and copy its full contents.
3. In GitHub choose **Add file → Create new file**, name it exactly `.github/workflows/build.yml`, paste the workflow contents, and commit it. This real workflow path is required for GitHub Actions to discover the workflow.
4. Open **Actions** and run **Build and Release**, or create/push a tag such as `v1.0.0`. The workflow runs tests and lint before building, verifies the APK signature, calculates SHA-256 checksums, and attaches the APK to a GitHub Release.
5. Download the APK from the Release on your Android phone.

## Signing warning
The included `test_keystore_b64.txt` contains a public test signing key. It is only for personal testing and must **never** be used for Google Play. Anyone who has the key can sign an APK with it. For a private stable signing key, add GitHub Actions repository secrets named `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` before publishing. Keep your keystore private and backed up. The same private key must be reused for updates to install over prior versions.

## API setup
- Create a GitHub fine-grained token with access only to the repositories you need and **Contents: Read and write**. Allow the required release permissions for Release mode.
- Create an OpenRouter API key and set a model ID available to your account, such as `openai/gpt-4o-mini` if that model is available to you.
- Enter both in Settings inside RepoPilot. They are encrypted locally with Android Keystore.

## Workflow notes
- `setup.sh` reconstructs the Android project from flat filenames into `build_tree/`.
- The build uses JDK 17, Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 2.0.20, and Android SDK 34.
- Workflow action references use major tags rather than verified full commit SHAs; pinning to full SHAs has not been done.
- For manual workflow runs, the default version tag is `v0.0.0-manual`. The release action creates the tag if it does not exist.
