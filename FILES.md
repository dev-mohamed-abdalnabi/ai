settings.gradle.kts — Gradle settings and module registration.
build.gradle.kts — root Android Gradle Plugin and Kotlin plugin versions.
gradle.properties — Gradle and AndroidX build settings.
app__build.gradle.kts — app module configuration, Compose dependencies, signing, version metadata.
app__proguard-rules.pro — release shrinker rules.
app__src__main__AndroidManifest.xml — Android manifest and INTERNET permission.
app__src__main__res__values__strings.xml — app name resources.
app__src__main__res__values__colors.xml — launcher background color.
app__src__main__res__drawable__ic_launcher_foreground.xml — custom adaptive launcher icon foreground.
app__src__main__res__mipmap-anydpi-v26__ic_launcher.xml — adaptive launcher icon.
app__src__main__res__mipmap-anydpi-v26__ic_launcher_round.xml — round adaptive launcher icon.
app__src__main__res__values__themes.xml — Android window theme.
app__src__main__java__com__flow__repopilot__MainActivity.kt — Arabic Compose UI and user interactions.
app__src__main__java__com__flow__repopilot__RepoApi.kt — GitHub Git database API, Release asset upload, OpenRouter requests, ZIP safety checks.
app__src__main__java__com__flow__repopilot__SecretStore.kt — Android Keystore AES-GCM encrypted API credential storage.
app__src__test__java__com__flow__repopilot__ZipPathTest.kt — unit tests for ZIP path safety.
.github__workflows__build.yml — workflow source; copy its contents to the real `.github/workflows/build.yml` path in GitHub.
setup.sh — rebuilds the Android project folder tree from flat files.
.gitignore — excludes generated build output and local secrets.
README.md — setup, API permissions, build, and signing instructions.
FILES.md — file map.
test_keystore_b64.txt — public test-only signing key used by the automated build fallback.
