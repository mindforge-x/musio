# Musio QQ Music Sidecar

This service wraps QQ Music read APIs behind a local HTTP interface. It does
not own the Musio agent loop and is not called directly by the React frontend.

```bash
python -m app.main
```

Default URL:

```text
http://127.0.0.1:18767
```
