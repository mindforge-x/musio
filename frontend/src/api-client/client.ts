const API_BASE = "";

export type SystemStatus = {
  backend: string;
  qqMusicSidecarBaseUrl: string;
  checkedAt: string;
};

export type LoginStartResult = {
  sessionId: string;
  provider: string;
  state: string;
  qrCodeDataUrl: string | null;
  message: string;
};

export type ChatRunResponse = {
  runId: string;
  state: string;
  message: string;
};

export type Song = {
  id: string;
  title: string;
  artists: string[];
  album?: string | null;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  return (await response.json()) as T;
}

export const api = {
  status: () => request<SystemStatus>("/api/system/status"),
  startLogin: () => request<LoginStartResult>("/api/auth/qqmusic/qr"),
  startChat: (message: string) =>
    request<ChatRunResponse>("/api/chat", {
      method: "POST",
      body: JSON.stringify({ userId: "local", message })
    }),
  search: (keyword: string) =>
    request<Song[]>(`/api/music/search?keyword=${encodeURIComponent(keyword)}&limit=8`)
};
