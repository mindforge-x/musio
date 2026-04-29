import {
  ChatRunResponse,
  LoginStartResult,
  LoginStatus,
  MusicGeneSnapshot,
  MusioPlaylist,
  PlayerState,
  ProviderStatus,
  Song,
  SongUrl,
  SystemStatus
} from "./types";

const API_BASE = "";

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
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
  providers: () => request<ProviderStatus[]>("/api/providers"),
  providerStatus: (provider: string) => request<ProviderStatus>(`/api/providers/${provider}/status`),
  providerMusicGene: (provider: string) => request<MusicGeneSnapshot>(`/api/providers/${provider}/music-gene`),
  startProviderLogin: (provider: string) => request<LoginStartResult>(`/api/providers/${provider}/login/start`, { method: "POST" }),
  providerLoginStatus: (provider: string, sessionId: string) =>
    request<LoginStatus>(`/api/providers/${provider}/login/${sessionId}/status`),
  logoutProvider: (provider: string) => request<LoginStatus>(`/api/providers/${provider}/logout`, { method: "POST" }),
  startLogin: () => request<LoginStartResult>("/api/auth/qqmusic/qr"),
  loginStatus: (sessionId: string) => request<LoginStatus>(`/api/auth/qqmusic/qr/${sessionId}/status`),
  startChat: (message: string) =>
    request<ChatRunResponse>("/api/chat", {
      method: "POST",
      body: JSON.stringify({ userId: "local", message })
    }),
  search: (keyword: string, limit = 8) =>
    request<Song[]>(`/api/music/search?keyword=${encodeURIComponent(keyword)}&limit=${limit}`),
  songUrl: (songId: string) => request<SongUrl>(`/api/music/songs/${songId}/url`),
  playerState: () => request<PlayerState>("/api/player/state"),
  musioPlaylists: () => request<MusioPlaylist[]>("/api/musio/playlists")
};
