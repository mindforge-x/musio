# Musio npm packaging

Musio uses a Codex-style npm layout:

- `@mindforge-x/musio` is the small global launcher package.
- `@mindforge-x/musio-<platform>-<arch>` packages contain the native runtime payload under `vendor/`.

The launcher keeps the command name short:

```bash
npm install -g @mindforge-x/musio
musio
```

For a local project install, use npm's local binary runner:

```bash
npm install @mindforge-x/musio
npx musio
```

## Build a platform package

Platform payloads are built natively because both `jlink` and PyInstaller are platform-specific.

```bash
cd packaging/platforms/linux-x64
npm run build:vendor
npm pack --dry-run
```

The generated `vendor/` directory contains:

```text
vendor/
  runtime/                 # jlink Java runtime
  lib/musio-cli.jar
  app/backend-spring.jar
  app/frontend/
  sidecar/qqmusic-sidecar
```

## Publish order

Publish all platform packages first:

```bash
cd packaging/platforms/linux-x64 && npm publish
cd packaging/platforms/linux-arm64 && npm publish
cd packaging/platforms/darwin-x64 && npm publish
cd packaging/platforms/darwin-arm64 && npm publish
cd packaging/platforms/win32-x64 && npm publish
cd packaging/platforms/win32-arm64 && npm publish
```

Then publish the launcher:

```bash
cd packaging/npm
npm publish
```

The launcher declares platform packages as optional dependencies. npm installs the package matching the user's OS and CPU by default. Users should not need `--include=optional`; if their npm config omits optional dependencies, the launcher postinstall step installs the matching platform runtime package automatically.

## GitHub Actions release

Use the `npm release` workflow from GitHub Actions for cross-platform beta releases.

Recommended first run:

```text
version: 0.1.0-beta.0
tag: beta
dry_run: true
```

If all six platform jobs pass, rerun with:

```text
version: 0.1.0-beta.0
tag: beta
dry_run: false
```

Publishing uses npm trusted publishing. Configure each package on npm to trust this GitHub repository and workflow:

```text
Repository owner: mindforge-x
Repository name: musio
Workflow filename: npm-release.yml
Environment: unset
```

The configured package names are:

```text
@mindforge-x/musio
@mindforge-x/musio-linux-x64
@mindforge-x/musio-linux-arm64
@mindforge-x/musio-darwin-x64
@mindforge-x/musio-darwin-arm64
@mindforge-x/musio-win32-x64
@mindforge-x/musio-win32-arm64
```

npm trusted publishing requires the packages to already exist before adding trusted publisher rules. If these are brand-new packages, first create/publish them from an npm account that controls the `@mindforge-x` scope, then add the trusted publisher configuration above and use this workflow for later releases.

CLI configuration is also possible with npm 11.10+:

```bash
npm install -g npm@^11.10.0
for package in \
  @mindforge-x/musio \
  @mindforge-x/musio-linux-x64 \
  @mindforge-x/musio-linux-arm64 \
  @mindforge-x/musio-darwin-x64 \
  @mindforge-x/musio-darwin-arm64 \
  @mindforge-x/musio-win32-x64 \
  @mindforge-x/musio-win32-arm64
do
  npm trust github "$package" --repo mindforge-x/musio --file npm-release.yml --yes
done
```

The release workflow skips packages that already exist on npm, so rerunning the same beta version can be used to publish only the platform packages that failed in a previous matrix run.
