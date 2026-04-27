import { useEffect, useState } from "react";
import { EventLog, LoginStartResult, LoginStatus } from "../../shared/types";
import { providerSetupClient } from "./providerSetupClient";
import { ProviderLoginCard } from "./ProviderLoginCard";

type SourceSetupPageProps = {
  busy: boolean;
  selectedSources: string[];
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
};

const QQ_PROVIDER = "qqmusic";
const TERMINAL_LOGIN_STATES = new Set(["DONE", "EXPIRED", "FAILED"]);
const SOURCE_OPTIONS = [
  { id: "qqmusic", label: "QQ Music", enabled: true },
  { id: "netease", label: "NetEase Cloud Music", enabled: false },
  { id: "local", label: "Local Music", enabled: false }
];

export function SourceSetupPage({ busy, selectedSources, onBusyChange, onEvent }: SourceSetupPageProps) {
  const [login, setLogin] = useState<LoginStartResult | null>(null);
  const [loginStatus, setLoginStatus] = useState<LoginStatus | null>(null);
  const selectedSet = new Set(selectedSources);
  const qqMusicSelected = selectedSet.has(QQ_PROVIDER);

  useEffect(() => {
    if (!login?.sessionId) {
      return;
    }

    if (loginStatus && TERMINAL_LOGIN_STATES.has(loginStatus.state)) {
      return;
    }

    let cancelled = false;

    const poll = async () => {
      try {
        const result = await providerSetupClient.loginStatus(QQ_PROVIDER, login.sessionId);
        if (cancelled) {
          return;
        }
        setLoginStatus((previous) => {
          if (previous?.state !== result.state) {
            onEvent({ id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` });
          }
          return result;
        });
      } catch (error) {
        if (!cancelled) {
          onEvent({
            id: crypto.randomUUID(),
            name: "login",
            detail: error instanceof Error ? error.message : "Login polling failed"
          });
        }
      }
    };

    const interval = window.setInterval(() => {
      if (!loginStatus || !TERMINAL_LOGIN_STATES.has(loginStatus.state)) {
        void poll();
      }
    }, 2000);

    void poll();

    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [login?.sessionId, loginStatus?.state, onEvent]);

  async function startLogin() {
    onBusyChange(true);
    try {
      const result = await providerSetupClient.startLogin(QQ_PROVIDER);
      setLogin(result);
      setLoginStatus({
        sessionId: result.sessionId,
        provider: result.provider,
        state: result.state,
        credentialStored: false,
        message: result.message
      });
      onEvent({ id: crypto.randomUUID(), name: "login", detail: `${result.state}: ${result.message}` });
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <section className="source-setup-stack">
      <section className="panel source-summary-panel">
        <div className="panel-heading">
          <h2>Music Sources</h2>
          <span>from terminal</span>
        </div>
        <div className="source-option-list">
          {SOURCE_OPTIONS.map((source) => (
            <article
              key={source.id}
              className={`source-option ${selectedSet.has(source.id) ? "selected" : ""} ${source.enabled ? "" : "disabled"}`}
            >
              <strong>{source.label}</strong>
              <span>{selectedSet.has(source.id) ? "selected" : source.enabled ? "available" : "coming soon"}</span>
            </article>
          ))}
        </div>
      </section>
      {qqMusicSelected ? (
        <ProviderLoginCard
          providerLabel="QQ Music"
          login={login}
          loginStatus={loginStatus}
          busy={busy}
          onStartLogin={startLogin}
        />
      ) : (
        <section className="panel auth-panel">
          <div className="panel-heading">
            <h2>No login source</h2>
            <span>waiting</span>
          </div>
          <p className="empty-copy">Select QQ Music in the terminal startup workflow to continue the MVP login flow.</p>
        </section>
      )}
    </section>
  );
}
