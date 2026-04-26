import { FormEvent, useEffect, useMemo, useState } from "react";
import { Activity, CircleStop, Headphones, ListMusic, MessageSquare, Play, QrCode, Radio, Search } from "lucide-react";
import { api, LoginStartResult, LoginStatus, Song, SystemStatus } from "./api-client/client";

type EventLog = {
  id: string;
  name: string;
  detail: string;
};

export function App() {
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [login, setLogin] = useState<LoginStartResult | null>(null);
  const [loginStatus, setLoginStatus] = useState<LoginStatus | null>(null);
  const [message, setMessage] = useState("Recommend five songs for a late-night coding session.");
  const [searchKeyword, setSearchKeyword] = useState("city pop");
  const [events, setEvents] = useState<EventLog[]>([]);
  const [songs, setSongs] = useState<Song[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.status()
      .then(setStatus)
      .catch(() => setStatus(null));
  }, []);

  const backendLabel = useMemo(() => status?.backend ?? "offline", [status]);

  useEffect(() => {
    if (!login?.sessionId) {
      return;
    }

    const terminalStates = new Set(["DONE", "EXPIRED", "FAILED"]);
    if (loginStatus && terminalStates.has(loginStatus.state)) {
      return;
    }
    let cancelled = false;

    const poll = async () => {
      try {
        const result = await api.loginStatus(login.sessionId);
        if (cancelled) {
          return;
        }
        setLoginStatus((previous) => {
          if (previous?.state !== result.state) {
            setEvents((current) => [
              { id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` },
              ...current
            ]);
          }
          return result;
        });
      } catch (error) {
        if (!cancelled) {
          setEvents((current) => [
            { id: crypto.randomUUID(), name: "login", detail: error instanceof Error ? error.message : "Login polling failed" },
            ...current
          ]);
        }
      }
    };

    const interval = window.setInterval(() => {
      if (!loginStatus || !terminalStates.has(loginStatus.state)) {
        void poll();
      }
    }, 2000);

    void poll();

    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [login?.sessionId, loginStatus?.state]);

  async function startLogin() {
    setBusy(true);
    try {
      const result = await api.startLogin();
      setLogin(result);
      setLoginStatus({
        sessionId: result.sessionId,
        provider: result.provider,
        state: result.state,
        credentialStored: false,
        message: result.message
      });
      setEvents((current) => [
        { id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` },
        ...current
      ]);
    } finally {
      setBusy(false);
    }
  }

  async function startChat(event: FormEvent) {
    event.preventDefault();
    if (!message.trim()) {
      return;
    }

    setBusy(true);
    try {
      const run = await api.startChat(message);
      setEvents((current) => [
        { id: crypto.randomUUID(), name: "run", detail: `${run.state}: ${run.runId}` },
        ...current
      ]);

      const source = new EventSource(`/api/chat/runs/${run.runId}/events`);
      source.onmessage = (evt) => {
        setEvents((current) => [{ id: crypto.randomUUID(), name: "message", detail: evt.data }, ...current]);
      };
      source.addEventListener("agent_message_delta", (evt) => {
        setEvents((current) => [{ id: crypto.randomUUID(), name: "agent", detail: (evt as MessageEvent).data }, ...current]);
      });
      source.addEventListener("done", () => {
        source.close();
        setBusy(false);
      });
      source.onerror = () => {
        source.close();
        setBusy(false);
      };
    } catch (error) {
      setBusy(false);
      setEvents((current) => [
        { id: crypto.randomUUID(), name: "error", detail: error instanceof Error ? error.message : "Unknown error" },
        ...current
      ]);
    }
  }

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!searchKeyword.trim()) {
      return;
    }

    setBusy(true);
    try {
      const result = await api.search(searchKeyword);
      setSongs(result);
      setEvents((current) => [
        { id: crypto.randomUUID(), name: "search", detail: `returned ${result.length} songs` },
        ...current
      ]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="app-shell">
      <aside className="rail">
        <div className="brand-mark">
          <Headphones size={24} strokeWidth={2.4} />
        </div>
        <button className="rail-button active" aria-label="Chat">
          <MessageSquare size={20} />
        </button>
        <button className="rail-button" aria-label="Library">
          <ListMusic size={20} />
        </button>
        <button className="rail-button" aria-label="Playback">
          <Radio size={20} />
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Musio local agent</p>
            <h1>Music operations console</h1>
          </div>
          <div className={`status-pill ${backendLabel === "ok" ? "online" : ""}`}>
            <Activity size={16} />
            <span>{backendLabel}</span>
          </div>
        </header>

        <div className="grid">
          <section className="panel command-panel">
            <div className="panel-heading">
              <h2>Agent Run</h2>
              <span>{busy ? "running" : "idle"}</span>
            </div>
            <form onSubmit={startChat} className="prompt-form">
              <textarea value={message} onChange={(event) => setMessage(event.target.value)} />
              <button type="submit" disabled={busy || !message.trim()}>
                <Play size={18} />
                Run
              </button>
            </form>
          </section>

          <section className="panel auth-panel">
            <div className="panel-heading">
              <h2>QQ Music</h2>
              <span>{loginStatus?.state ?? "not linked"}</span>
            </div>
            <div className="qr-box">
              {login?.qrCodeDataUrl ? <img src={login.qrCodeDataUrl} alt="QQ Music login QR" /> : <QrCode size={96} />}
            </div>
            <button className="secondary-action" type="button" onClick={startLogin} disabled={busy}>
              <QrCode size={18} />
              Login
            </button>
          </section>

          <section className="panel search-panel">
            <div className="panel-heading">
              <h2>Catalog</h2>
              <span>{songs.length} results</span>
            </div>
            <form onSubmit={search} className="search-form">
              <Search size={18} />
              <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} />
              <button type="submit" disabled={busy || !searchKeyword.trim()}>
                Search
              </button>
            </form>
            <div className="song-list">
              {songs.map((song) => (
                <article key={song.id} className="song-row">
                  <div>
                    <strong>{song.title || song.id}</strong>
                    <span>{song.artists?.join(", ") || "QQ Music"}</span>
                  </div>
                  <button aria-label={`Play ${song.title || song.id}`}>
                    <Play size={16} />
                  </button>
                </article>
              ))}
            </div>
          </section>

          <section className="panel event-panel">
            <div className="panel-heading">
              <h2>Events</h2>
              <button type="button" onClick={() => setEvents([])} aria-label="Clear events">
                <CircleStop size={17} />
              </button>
            </div>
            <div className="event-list">
              {events.map((item) => (
                <article key={item.id} className="event-row">
                  <span>{item.name}</span>
                  <p>{item.detail}</p>
                </article>
              ))}
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}
